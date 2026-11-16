// Bounded ZIP inspection tests for artifact-storage.mjs.
// Proves the central-directory parser rejects ZIP bombs, duplicate
// names and path traversal WITHOUT decompressing the archive.
import assert from 'node:assert/strict';
import test from 'node:test';
import { deflateSync, strToU8 } from 'fflate';
import { inspectApkBytes, extractPreviewHtml } from '../lib/artifact-storage.mjs';

// Build a ZIP from entries [{name, data: Buffer|Uint8Array, method}]
// writing local headers + central directory + EOCD by hand, so tests
// can include duplicate names and traversal names that zipSync would
// collapse or reject.
function makeZip(entries) {
  const localParts = [];
  const centralParts = [];
  let offset = 0;
  const encoder = new TextEncoder();
  for (const entry of entries) {
    const name = encoder.encode(entry.name);
    const raw = Buffer.from(entry.data);
    const method = entry.method ?? 8;
    const compressed = method === 8 ? Buffer.from(deflateSync(raw, { level: 9 })) : raw;
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0, 6); // flags
    local.writeUInt16LE(method, 8);
    local.writeUInt16LE(0, 10); // time
    local.writeUInt16LE(0, 12); // date
    local.writeUInt32LE(0, 14); // crc (unchecked by our parser)
    local.writeUInt32LE(compressed.length, 18);
    local.writeUInt32LE(raw.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    localParts.push(local, name, compressed);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4); // version made by
    central.writeUInt16LE(20, 6); // version needed
    central.writeUInt16LE(0, 8); // flags
    central.writeUInt16LE(method, 10);
    central.writeUInt16LE(0, 12);
    central.writeUInt16LE(0, 14);
    central.writeUInt32LE(0, 16);
    central.writeUInt32LE(compressed.length, 20);
    central.writeUInt32LE(raw.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt16LE(0, 30);
    central.writeUInt16LE(0, 32);
    central.writeUInt16LE(0, 34); // disk
    central.writeUInt16LE(0, 36); // internal attrs
    central.writeUInt32LE(0, 38); // external attrs
    central.writeUInt32LE(offset, 42);
    centralParts.push(central, name);
    offset += local.length + name.length + compressed.length;
  }
  const cd = Buffer.concat(centralParts);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(0, 4);
  eocd.writeUInt16LE(0, 6);
  eocd.writeUInt16LE(entries.length, 8);
  eocd.writeUInt16LE(entries.length, 10);
  eocd.writeUInt32LE(cd.length, 12);
  eocd.writeUInt32LE(offset, 16);
  eocd.writeUInt16LE(0, 20);
  return Buffer.concat([...localParts, cd, eocd]);
}

const MANIFEST = { name: 'AndroidManifest.xml', data: strToU8('<manifest/>'), method: 0 };
const DEX = { name: 'classes.dex', data: strToU8('dex\n035\0'), method: 0 };
const PREVIEW = '<html><body>preview-ok</body></html>';

function validApk() {
  return makeZip([
    MANIFEST,
    DEX,
    { name: 'assets/index.html', data: strToU8(PREVIEW), method: 8 },
  ]);
}

test('valid APK passes inspection and preview extraction', () => {
  const apk = validApk();
  const inspected = inspectApkBytes(apk);
  assert.deepEqual([...inspected.entries].sort(), ['AndroidManifest.xml', 'assets/index.html', 'classes.dex']);
  assert.equal(extractPreviewHtml(apk), PREVIEW);
});

test('stored (uncompressed) preview entry extracts correctly', () => {
  const apk = makeZip([MANIFEST, DEX, { name: 'assets/index.html', data: strToU8(PREVIEW), method: 0 }]);
  assert.equal(extractPreviewHtml(apk), PREVIEW);
});

test('ZIP bomb: 300 MiB of zeros is rejected without decompressing', () => {
  const zeros = Buffer.alloc(300 * 1024 * 1024, 0);
  const bomb = makeZip([
    MANIFEST,
    DEX,
    { name: 'assets/big.bin', data: zeros, method: 8 },
  ]);
  // The bomb compresses to well under 1 MiB — the parser must reject it
  // from the central directory alone, never expanding the 300 MiB.
  assert.ok(bomb.length < 1024 * 1024, `fixture bomb should stay small, got ${bomb.length}`);
  assert.throws(() => inspectApkBytes(bomb), /melebihi batas/);
});

test('oversized preview entry is not extracted', () => {
  const big = Buffer.alloc(3 * 1024 * 1024, 65); // 3 MiB of 'A'
  const apk = makeZip([MANIFEST, DEX, { name: 'assets/index.html', data: big, method: 8 }]);
  assert.ok(apk.length < 1024 * 1024, 'fixture should stay small');
  assert.equal(extractPreviewHtml(apk), null);
});

test('duplicate entry names are rejected', () => {
  const apk = makeZip([MANIFEST, DEX, DEX]);
  assert.throws(() => inspectApkBytes(apk), /duplikat/);
});

test('path traversal names are rejected', () => {
  for (const bad of ['../evil.txt', '/abs/path.txt', 'a\\b.txt', 'x/../../evil.txt']) {
    const apk = makeZip([MANIFEST, DEX, { name: bad, data: strToU8('x'), method: 0 }]);
    assert.throws(() => inspectApkBytes(apk), /traversal/, bad);
  }
});

test('missing manifest or dex is rejected; non-ZIP is rejected', () => {
  assert.throws(() => inspectApkBytes(makeZip([DEX])), /AndroidManifest/);
  assert.throws(() => inspectApkBytes(makeZip([MANIFEST])), /classes\.dex/);
  assert.throws(() => inspectApkBytes(Buffer.from('not a zip at all')), /APK Android/);
  assert.throws(() => inspectApkBytes(Buffer.alloc(0)), /kosong/);
});
