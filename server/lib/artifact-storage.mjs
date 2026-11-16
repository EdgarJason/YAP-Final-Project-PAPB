// Artifact verification for the YAP gateway (v2 cloud path).
// The server never trusts a worker-reported hash/size on its own: it fetches
// the registered object outbound from Storage and checks the bytes itself
// (RANCANGAN §9). Signature verification (apksigner) stays a worker-side
// report — it is NOT labelled as an independent server signature check.
import { createHash } from 'node:crypto';
import { strFromU8 } from 'fflate';
import { inflateRawSync } from 'node:zlib';
import { LIMITS, sha256Hex } from './build-service.mjs';

const ZIP_MAGIC = Buffer.from([0x50, 0x4b, 0x03, 0x04]);
// Bounds for ZIP inspection — enforced on the central directory BEFORE
// any decompression, so a 32 MiB highly-compressed archive (ZIP bomb)
// can never force a multi-gigabyte expansion in memory.
const MAX_ZIP_ENTRIES = 8192;
const MAX_ZIP_ENTRY_BYTES = LIMITS.maxApkBytes; // 32 MiB per entry
const MAX_ZIP_TOTAL_BYTES = 256 * 1024 * 1024; // 256 MiB uncompressed total
const MAX_ZIP_NAME_BYTES = 4096;

function fail(message) {
  const error = new Error(message);
  error.status = 400;
  return error;
}

function readU16(buf, off) { return buf.readUInt16LE(off); }
function readU32(buf, off) { return buf.readUInt32LE(off); }

/**
 * Parse the ZIP central directory with hard bounds. Returns entries
 * [{name, method, compressedSize, uncompressedSize, localOffset}].
 * Never decompresses anything. Rejects ZIP64, encrypted entries,
 * duplicate names and path traversal before any extraction happens.
 */
export function parseZipCentralDirectory(bytes) {
  if (!Buffer.isBuffer(bytes)) bytes = Buffer.from(bytes);
  // Find the End of Central Directory record: scan backwards and accept
  // only a record whose comment length lands exactly at the buffer end.
  let eocd = -1;
  const scanStart = Math.max(0, bytes.length - 22 - 65535);
  for (let i = bytes.length - 22; i >= scanStart; i--) {
    if (bytes[i] === 0x50 && bytes[i + 1] === 0x4b && bytes[i + 2] === 0x05 && bytes[i + 3] === 0x06) {
      const commentLen = readU16(bytes, i + 20);
      if (i + 22 + commentLen === bytes.length) { eocd = i; break; }
    }
  }
  if (eocd < 0) throw fail('APK bukan arsip ZIP yang valid.');
  const totalEntries = readU16(bytes, eocd + 10);
  const cdSize = readU32(bytes, eocd + 12);
  const cdOffset = readU32(bytes, eocd + 16);
  if (totalEntries === 0xffff || cdSize === 0xffffffff || cdOffset === 0xffffffff) {
    throw fail('APK memakai ZIP64 yang tidak didukung.');
  }
  if (totalEntries > MAX_ZIP_ENTRIES) throw fail('APK berisi terlalu banyak entry.');
  if (cdOffset + cdSize > bytes.length) throw fail('Central directory APK rusak.');
  const entries = [];
  const seen = new Set();
  let totalUncompressed = 0;
  let off = cdOffset;
  for (let n = 0; n < totalEntries; n++) {
    if (off + 46 > bytes.length
      || bytes[off] !== 0x50 || bytes[off + 1] !== 0x4b || bytes[off + 2] !== 0x01 || bytes[off + 3] !== 0x02) {
      throw fail('Central directory APK rusak.');
    }
    const flags = readU16(bytes, off + 8);
    const method = readU16(bytes, off + 10);
    const compressedSize = readU32(bytes, off + 20);
    const uncompressedSize = readU32(bytes, off + 24);
    const nameLen = readU16(bytes, off + 28);
    const extraLen = readU16(bytes, off + 30);
    const commentLen = readU16(bytes, off + 32);
    const localOffset = readU32(bytes, off + 42);
    if (nameLen === 0 || nameLen > MAX_ZIP_NAME_BYTES) throw fail('Nama entry APK tidak valid.');
    if (compressedSize === 0xffffffff || uncompressedSize === 0xffffffff || localOffset === 0xffffffff) {
      throw fail('APK memakai ZIP64 yang tidak didukung.');
    }
    if (flags & 0x1) throw fail('APK berisi entry terenkripsi yang tidak didukung.');
    if (method !== 0 && method !== 8) throw fail('APK memakai metode kompresi yang tidak didukung.');
    if (uncompressedSize > MAX_ZIP_ENTRY_BYTES) throw fail('Entry APK melebihi batas ukuran.');
    totalUncompressed += uncompressedSize;
    if (totalUncompressed > MAX_ZIP_TOTAL_BYTES) throw fail('Total ukuran hasil dekompresi APK melebihi batas.');
    const name = bytes.subarray(off + 46, off + 46 + nameLen).toString('utf8');
    if (name.startsWith('/') || name.includes('\\') || name.split('/').includes('..')) {
      throw fail('APK mengandung path traversal.');
    }
    if (seen.has(name)) throw fail('APK mengandung entry duplikat yang ambigu.');
    seen.add(name);
    entries.push({ name, method, compressedSize, uncompressedSize, localOffset });
    off += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

/**
 * Bounded ZIP inspection: central-directory checks only, no extraction.
 * `bytes` must already be size-bounded by caller.
 */
export function inspectApkBytes(bytes, { maxBytes = LIMITS.maxApkBytes } = {}) {
  if (!Buffer.isBuffer(bytes)) bytes = Buffer.from(bytes);
  if (bytes.length === 0 || bytes.length > maxBytes) {
    throw fail('APK melebihi batas ukuran atau kosong.');
  }
  if (!bytes.subarray(0, 4).equals(ZIP_MAGIC)) throw fail('Berkas tidak menyerupai APK Android.');
  const entries = parseZipCentralDirectory(bytes);
  const names = entries.map(entry => entry.name);
  if (!names.includes('AndroidManifest.xml')) throw fail('APK tidak berisi AndroidManifest.xml.');
  if (!names.includes('classes.dex')) throw fail('APK tidak berisi classes.dex.');
  return { size: bytes.length, sha256: sha256Hex(bytes), entries: names };
}

/** Extract assets/index.html from an APK buffer, bounded to 2 MiB. */
export function extractPreviewHtml(bytes) {
  if (!Buffer.isBuffer(bytes)) bytes = Buffer.from(bytes);
  try {
    const entries = parseZipCentralDirectory(bytes);
    const entry = entries.find(item => item.name === 'assets/index.html');
    if (!entry) return null;
    // The central-directory bound above already caps this entry at
    // 2 MiB, so the single inflation below cannot expand unbounded.
    if (entry.uncompressedSize > LIMITS.maxPreviewHtmlBytes) return null;
    const local = entry.localOffset;
    if (local + 30 > bytes.length
      || bytes[local] !== 0x50 || bytes[local + 1] !== 0x4b || bytes[local + 2] !== 0x03 || bytes[local + 3] !== 0x04) {
      return null;
    }
    const nameLen = readU16(bytes, local + 26);
    const extraLen = readU16(bytes, local + 28);
    const dataStart = local + 30 + nameLen + extraLen;
    const dataEnd = dataStart + entry.compressedSize;
    if (dataEnd > bytes.length) return null;
    const compressed = bytes.subarray(dataStart, dataEnd);
    const raw = entry.method === 0
      ? compressed
      : inflateRawSync(compressed, { maxOutputLength: LIMITS.maxPreviewHtmlBytes });
    if (raw.length !== entry.uncompressedSize) return null;
    return strFromU8(raw);
  } catch {
    return null;
  }
}

/** Server-chosen object path. Never accept a worker-supplied path (§9). */
export function artifactObjectPath({ jobId, leaseId, generationId, kind = 'apk' }) {
  const file = kind === 'apk' ? 'app.apk' : kind === 'source' ? 'source.zip' : 'verification.json';
  return `jobs/${jobId}/${leaseId}/${generationId}/${file}`;
}

export function artifactFileName(appName, jobId) {
  const base = (appName || `Aplikasi-${String(jobId).slice(0, 8)}`).replace(/[^\p{L}\p{N} _.-]/gu, '').trim() || 'Aplikasi';
  return `${base}.apk`;
}

/**
 * Fetch a registered Storage object outbound and verify it against the
 * expected size/hash, with a hard byte cap applied while reading.
 * `fetchImpl` defaults to global fetch (Supabase Storage download URL).
 * Throws an Error with .status 400 (corrupt/mismatch), 404 (missing) or 502.
 */
export async function verifyArtifactObject({
  url, headers = {}, expectedSize, expectedSha256, maxBytes = LIMITS.maxApkBytes, fetchImpl = fetch,
}) {
  let response;
  try {
    response = await fetchImpl(url, { headers });
  } catch (error) {
    const wrapped = new Error(`Gagal mengambil artifact dari Storage: ${error.message}`);
    wrapped.status = 502;
    throw wrapped;
  }
  if (response.status === 404) {
    const error = new Error('Artifact tidak ditemukan di Storage.');
    error.status = 404;
    throw error;
  }
  if (!response.ok) {
    const error = new Error(`Storage mengembalikan HTTP ${response.status}.`);
    error.status = 502;
    throw error;
  }
  const reader = response.body?.getReader ? response.body.getReader() : null;
  const hash = createHash('sha256');
  const chunks = [];
  let size = 0;
  if (reader) {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > maxBytes) {
        const error = new Error('Artifact melebihi batas ukuran.');
        error.status = 400;
        throw error;
      }
      hash.update(value);
      chunks.push(Buffer.from(value));
    }
  } else {
    const buffer = Buffer.from(await response.arrayBuffer());
    size = buffer.length;
    if (size > maxBytes) {
      const error = new Error('Artifact melebihi batas ukuran.');
      error.status = 400;
      throw error;
    }
    hash.update(buffer);
    chunks.push(buffer);
  }
  const actualSha256 = hash.digest('hex');
  if (expectedSize !== undefined && size !== expectedSize) {
    throw fail(`Ukuran artifact tidak cocok (diharapkan ${expectedSize}, didapat ${size}).`);
  }
  if (expectedSha256 && actualSha256 !== expectedSha256) {
    throw fail('SHA-256 artifact tidak cocok.');
  }
  const bytes = Buffer.concat(chunks);
  const inspected = inspectApkBytes(bytes);
  return { size, sha256: actualSha256, entries: inspected.entries, previewHtml: extractPreviewHtml(bytes) };
}
