// TUS client tests for worker-v2.sh against a local recording TUS
// endpoint (NOT Supabase Storage — this proves the client speaks the
// Supabase resumable-upload contract: 6 MiB chunks, signed token in
// x-signature, metadata bucketName/objectName/contentType, resume from
// the server offset). Real Supabase Storage behaviour still needs a
// live project; see server/supabase/README.md.
import assert from 'node:assert/strict';
import test, { describe, before, after, beforeEach } from 'node:test';
import { execFile } from 'node:child_process';
import { randomUUID, randomBytes } from 'node:crypto';
import { createServer } from 'node:http';
import { mkdtemp, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const WORKER = join(ROOT, 'muse', 'worker-v2.sh');
const TOKEN = 'test-token-123';
const CHUNK = 6 * 1024 * 1024;

let server;
let port;
let base;
let stateDir;
// upload id -> { length, offset, chunks: [{offset, size}], data: Buffer[], metadata, signature, created }
let uploads;
let seedOffset = 0;
let seedData = null;
let failPatchWith = 0;

function parseMetadata(header) {
  const out = {};
  for (const part of String(header || '').split(',')) {
    const [key, value] = part.trim().split(' ');
    if (key && value) out[key] = Buffer.from(value, 'base64').toString('utf8');
  }
  return out;
}

function startTusServer() {
  uploads = new Map();
  seedOffset = 0;
  seedData = null;
  failPatchWith = 0;
  server = createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    if (req.method === 'POST' && url.pathname === '/storage/v1/upload/resumable') {
      const metadata = parseMetadata(req.headers['upload-metadata']);
      const id = randomUUID();
      uploads.set(id, {
        length: Number(req.headers['upload-length']),
        offset: seedOffset,
        chunks: [],
        data: seedData ? [seedData] : [],
        metadata,
        signature: req.headers['x-signature'],
        authHeader: req.headers.authorization || null,
        created: true,
      });
      res.writeHead(201, {
        'Tus-Resumable': '1.0.0',
        Location: `/files/${id}`,
      });
      res.end();
      return;
    }
    const match = /^\/files\/([a-f0-9-]+)$/.exec(url.pathname);
    if (!match) { res.writeHead(404); res.end(); return; }
    const upload = uploads.get(match[1]);
    if (!upload) { res.writeHead(404); res.end(); return; }
    if (req.method === 'HEAD') {
      res.writeHead(200, { 'Tus-Resumable': '1.0.0', 'Upload-Offset': String(upload.offset) });
      res.end();
      return;
    }
    if (req.method === 'PATCH') {
      if (failPatchWith) {
        res.writeHead(failPatchWith, { 'Tus-Resumable': '1.0.0' });
        res.end();
        return;
      }
      const offset = Number(req.headers['upload-offset']);
      if (offset !== upload.offset) {
        res.writeHead(409, { 'Tus-Resumable': '1.0.0' });
        res.end();
        return;
      }
      if (req.headers['x-signature'] !== TOKEN) {
        res.writeHead(401, { 'Tus-Resumable': '1.0.0' });
        res.end();
        return;
      }
      const chunks = [];
      req.on('data', chunk => chunks.push(chunk));
      req.on('end', () => {
        const body = Buffer.concat(chunks);
        upload.chunks.push({ offset, size: body.length });
        upload.data.push(body);
        upload.offset += body.length;
        res.writeHead(204, { 'Tus-Resumable': '1.0.0', 'Upload-Offset': String(upload.offset) });
        res.end();
      });
      return;
    }
    res.writeHead(405);
    res.end();
  });
  return new Promise(resolve => {
    server.listen(0, '127.0.0.1', () => {
      port = server.address().port;
      base = `http://127.0.0.1:${port}`;
      resolve();
    });
  });
}

function runUpload(id, file) {
  return new Promise(resolve => {
    execFile('bash', [WORKER, 'upload', id, file], {
      env: {
        ...process.env,
        YAP_BASE_URL: 'https://example.invalid',
        YAP_WORKER_TOKEN: 't'.repeat(32),
        YAP_WORKER_STATE_DIR: stateDir,
      },
    }, (error, stdout, stderr) => {
      resolve({ code: error ? (error.code ?? 1) : 0, stdout: stdout.trim(), stderr: stderr.trim() });
    });
  });
}

async function seedState({ method = 'tus', objectPath = 'jobs/j/l/g/app.apk' } = {}) {
  stateDir = await mkdtemp(join(tmpdir(), 'yap-tus-state-'));
  await writeFile(join(stateDir, 'last-claim.json'), JSON.stringify({
    jobId: randomUUID(),
    lease: randomUUID(),
    prepare: {
      uploadUrl: `${base}/storage/v1/upload/resumable`,
      uploadToken: TOKEN,
      method,
      artifactId: randomUUID(),
      bucket: 'yap-artifacts',
      objectPath,
    },
  }));
}

describe('worker-v2.sh TUS client (endpoint TUS lokal)', () => {
  before(startTusServer);
  after(() => new Promise(resolve => server.close(resolve)));
  beforeEach(() => {
    uploads.clear();
    seedOffset = 0;
    seedData = null;
    failPatchWith = 0;
  });

  test('upload 13 MiB: chunk 6 MiB, x-signature, metadata bucket/object/contentType', async () => {
    await seedState();
    const file = join(await mkdtemp(join(tmpdir(), 'yap-tus-file-')), 'app.apk');
    const bytes = randomBytes(13 * 1024 * 1024);
    await writeFile(file, bytes);
    const id = randomUUID();
    const result = await runUpload(id, file);
    assert.equal(result.code, 0, result.stderr);
    assert.equal(uploads.size, 1);
    const [upload] = uploads.values();
    assert.equal(upload.signature, TOKEN, 'token harus di x-signature, bukan Authorization Bearer');
    assert.equal(upload.authHeader, null, 'tidak boleh ada header Authorization Bearer');
    assert.equal(upload.metadata.bucketName, 'yap-artifacts');
    assert.equal(upload.metadata.objectName, 'jobs/j/l/g/app.apk');
    assert.equal(upload.metadata.contentType, 'application/vnd.android.package-archive');
    assert.equal(upload.length, bytes.length);
    assert.deepEqual(upload.chunks.map(c => c.size), [CHUNK, CHUNK, 1024 * 1024]);
    assert.equal(upload.offset, bytes.length);
    assert.ok(Buffer.concat(upload.data).equals(bytes), 'byte hasil upload harus sama dengan file');
  });

  test('resume dari offset 6 MiB yang dilaporkan server', async () => {
    await seedState();
    const file = join(await mkdtemp(join(tmpdir(), 'yap-tus-file-')), 'app.apk');
    const bytes = randomBytes(13 * 1024 * 1024);
    await writeFile(file, bytes);
    seedOffset = CHUNK;
    seedData = bytes.subarray(0, CHUNK);
    const result = await runUpload(randomUUID(), file);
    assert.equal(result.code, 0, result.stderr);
    const [upload] = uploads.values();
    assert.equal(upload.chunks[0].offset, CHUNK, 'PATCH pertama harus mulai dari offset server');
    assert.deepEqual(upload.chunks.map(c => c.size), [CHUNK, 1024 * 1024]);
    assert.equal(upload.offset, bytes.length);
    assert.ok(Buffer.concat(upload.data).equals(bytes));
  });

  test('upload 32 MiB (batas APK) selesai dalam chunk 6 MiB', async () => {
    await seedState();
    const file = join(await mkdtemp(join(tmpdir(), 'yap-tus-file-')), 'app.apk');
    const bytes = randomBytes(32 * 1024 * 1024);
    await writeFile(file, bytes);
    const result = await runUpload(randomUUID(), file);
    assert.equal(result.code, 0, result.stderr);
    const [upload] = uploads.values();
    assert.deepEqual(
      upload.chunks.map(c => c.size),
      [CHUNK, CHUNK, CHUNK, CHUNK, CHUNK, 2 * 1024 * 1024],
    );
    assert.equal(upload.offset, bytes.length);
    assert.ok(Buffer.concat(upload.data).equals(bytes));
  });

  test('server menolak PATCH (401) -> upload gagal, bukan sukses diam-diam', async () => {
    await seedState();
    const file = join(await mkdtemp(join(tmpdir(), 'yap-tus-file-')), 'app.apk');
    await writeFile(file, randomBytes(1024));
    failPatchWith = 401;
    const result = await runUpload(randomUUID(), file);
    assert.notEqual(result.code, 0);
  });
});
