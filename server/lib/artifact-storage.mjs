// Artifact verification for the YAP gateway (v2 cloud path).
// The server never trusts a worker-reported hash/size on its own: it fetches
// the registered object outbound from Storage and checks the bytes itself
// (RANCANGAN §9). Signature verification (apksigner) stays a worker-side
// report — it is NOT labelled as an independent server signature check.
import { createHash } from 'node:crypto';
import { unzipSync, strFromU8 } from 'fflate';
import { LIMITS, sha256Hex } from './build-service.mjs';

const ZIP_MAGIC = Buffer.from([0x50, 0x4b, 0x03, 0x04]);

function fail(message) {
  const error = new Error(message);
  error.status = 400;
  return error;
}

/**
 * Bounded ZIP inspection: central-directory style checks without extracting
 * everything. Rejects path traversal, duplicate ambiguous entries and
 * missing required entries. `bytes` must already be size-bounded by caller.
 */
export function inspectApkBytes(bytes, { maxBytes = LIMITS.maxApkBytes } = {}) {
  if (!Buffer.isBuffer(bytes)) bytes = Buffer.from(bytes);
  if (bytes.length === 0 || bytes.length > maxBytes) {
    throw fail('APK melebihi batas ukuran atau kosong.');
  }
  if (!bytes.subarray(0, 4).equals(ZIP_MAGIC)) throw fail('Berkas tidak menyerupai APK Android.');
  let entries;
  try {
    entries = unzipSync(bytes);
  } catch {
    throw fail('APK bukan arsip ZIP yang valid.');
  }
  const names = Object.keys(entries);
  const seen = new Set();
  for (const name of names) {
    if (name.includes('..') || name.startsWith('/') || name.includes('\\')) {
      throw fail('APK mengandung path traversal.');
    }
    if (seen.has(name)) throw fail('APK mengandung entry duplikat yang ambigu.');
    seen.add(name);
  }
  if (!names.includes('AndroidManifest.xml')) throw fail('APK tidak berisi AndroidManifest.xml.');
  if (!names.includes('classes.dex')) throw fail('APK tidak berisi classes.dex.');
  return { size: bytes.length, sha256: sha256Hex(bytes), entries: names };
}

/** Extract assets/index.html from an APK buffer, bounded to 2 MiB. */
export function extractPreviewHtml(bytes) {
  if (!Buffer.isBuffer(bytes)) bytes = Buffer.from(bytes);
  try {
    const files = unzipSync(bytes, {
      filter: entry => entry.name === 'assets/index.html' && entry.originalSize <= LIMITS.maxPreviewHtmlBytes,
    });
    const html = files['assets/index.html'];
    if (!html) return null;
    return strFromU8(html);
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
