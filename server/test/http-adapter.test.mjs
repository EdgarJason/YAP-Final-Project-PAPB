// Real HTTP tests for the v2 API + adapter + worker-v2.sh (filesystem backend).
// Every test starts its own local.mjs (YAP_API_VERSION=v2) on a loopback
// port with its own queue directory, and drives it with fetch and with
// the actual worker helper script — no mocked fetch. The filesystem
// backend has no Storage, so the byte-store step between script prepare
// and script finalize is done through the repository's storeArtifactBytes
// (the documented local equivalent of a Storage upload); the script
// itself performs prepare (POST) and finalize over HTTP.
import assert from 'node:assert/strict';
import test, { describe, before, beforeEach, afterEach } from 'node:test';
import { spawn, execFile } from 'node:child_process';
import { createHash, randomUUID } from 'node:crypto';
import { mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { accessSync, constants } from 'node:fs';
import { createFilesystemRepository } from '../lib/build-repository-filesystem.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const FIXTURE = join(ROOT, 'test', 'fixtures', 'test-app.apk');
const WORKER = join(ROOT, 'muse', 'worker-v2.sh');
const ACCESS = 'a'.repeat(32);
const WORKER_TOKEN = 'w'.repeat(32);
const GUEST_A = `guest_${'a'.repeat(64)}`;
const GUEST_B = `guest_${'b'.repeat(64)}`;

function findApksigner() {
  const candidates = [
    process.env.APKSIGNER,
    join(process.env.HOME || '', 'workspace/yap-tools/android-sdk/build-tools/34.0.0/apksigner'),
    '/opt/android-sdk/build-tools/34.0.0/apksigner',
  ].filter(Boolean);
  for (const candidate of candidates) {
    try { accessSync(candidate, constants.X_OK); return candidate; } catch { /* try next */ }
  }
  return null;
}
const APKSIGNER = findApksigner();

let fixture;
let fixtureSha;
let server;
let port;
let dir;
let stateDir;
let base;

async function startServer() {
  dir = await mkdtemp(join(tmpdir(), 'yap-http-'));
  stateDir = await mkdtemp(join(tmpdir(), 'yap-worker-state-'));
  port = 18100 + Math.floor(Math.random() * 500);
  base = `http://127.0.0.1:${port}`;
  server = spawn('node', ['local.mjs'], {
    cwd: ROOT,
    env: {
      ...process.env,
      PORT: String(port),
      YAP_BIND_HOST: '127.0.0.1',
      YAP_BUILD_DIR: dir,
      YAP_API_VERSION: 'v2',
      YAP_ACCESS_TOKEN: ACCESS,
      YAP_WORKER_TOKEN: WORKER_TOKEN,
      YAP_PUBLIC_BUILDS: '1',
    },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  const deadline = Date.now() + 10000;
  for (;;) {
    try {
      const res = await fetch(`${base}/api/capabilities`);
      if (res.ok) return;
    } catch { /* not up yet */ }
    if (Date.now() > deadline) throw new Error('local.mjs did not start in time');
    await new Promise(r => setTimeout(r, 100));
  }
}

async function request(path, { method = 'GET', token = null, body = undefined, lease = null } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (lease) headers['X-YAP-Lease'] = lease;
  const res = await fetch(`${base}${path}`, {
    method, headers, body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { /* non-JSON body */ }
  return { status: res.status, headers: res.headers, text, json };
}

async function requestBytes(path, { token = null } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${base}${path}`, { headers });
  const buffer = Buffer.from(await res.arrayBuffer());
  return { status: res.status, headers: res.headers, buffer };
}

function runWorker(args, env = {}) {
  return new Promise(resolve => {
    execFile('bash', [WORKER, ...args], {
      env: {
        ...process.env,
        // apksigner is a shell wrapper that needs java on PATH.
        PATH: APKSIGNER
          ? `${join(process.env.HOME || '', 'workspace/yap-tools/jdk-21/bin')}:${process.env.PATH}`
          : process.env.PATH,
        YAP_BASE_URL: base,
        YAP_WORKER_TOKEN: WORKER_TOKEN,
        YAP_WORKER_STATE_DIR: stateDir,
        ...(APKSIGNER ? { APKSIGNER } : {}),
        ...env,
      },
    }, (error, stdout, stderr) => {
      resolve({ code: error ? (error.code ?? 1) : 0, stdout: stdout.trim(), stderr: stderr.trim() });
    });
  });
}

function submitAsGuest(guest, prompt = 'Buat kalkulator') {
  return request('/api/build', {
    method: 'POST', token: guest,
    body: { id: randomUUID(), prompt, responseMode: 'apk' },
  });
}

describe('HTTP v2 (local.mjs, backend filesystem)', () => {
  before(async () => {
    fixture = await readFile(FIXTURE);
    fixtureSha = createHash('sha256').update(fixture).digest('hex');
  });
  beforeEach(startServer);
  afterEach(() => {
    if (server && !server.killed) server.kill();
    server = null;
  });

  test('capabilities + signal publik', async () => {
    const caps = await request('/api/capabilities');
    assert.equal(caps.status, 200);
    assert.equal(caps.json.protocolVersion, 2);
    assert.equal(caps.json.maxApkBytes, 33554432);
    const signal = await request('/api/build/signal');
    assert.equal(signal.status, 200);
    assert.equal(signal.json.pending, false);
  });

  test('worker-v2.sh prepare (POST) + finalize end-to-end, lalu /apk mengembalikan byte lengkap', { skip: !APKSIGNER ? 'apksigner tidak ditemukan; lewati finalize via script' : false }, async () => {
    const submitted = await submitAsGuest(GUEST_A);
    assert.equal(submitted.status, 202);
    const id = submitted.json.id;
    const claimed = await runWorker(['claim', 'new']);
    assert.equal(claimed.code, 0, claimed.stderr);
    const claim = JSON.parse(claimed.stdout);
    assert.equal(claim.id, id);
    const lease = claim.lease;
    const prepared = await runWorker(['prepare', id, lease, FIXTURE]);
    assert.equal(prepared.code, 0, prepared.stderr);
    const prep = JSON.parse(prepared.stdout);
    assert.ok(prep.artifactId, 'prepare harus mengembalikan artifactId');
    assert.equal(prep.method, 'local');
    assert.match(prep.objectPath, new RegExp(`^jobs/${id}/${lease}/`));
    // Byte-store lokal (padanan upload Storage pada backend filesystem).
    const repo = createFilesystemRepository({ directory: dir });
    await repo.storeArtifactBytes({ artifactId: prep.artifactId, bytes: fixture });
    const finalized = await runWorker(['finalize', id, lease, FIXTURE, 'Fixture Uji']);
    assert.equal(finalized.code, 0, finalized.stderr);
    const fin = JSON.parse(finalized.stdout);
    assert.equal(fin.status, 'ready');
    assert.equal(fin.sha256, fixtureSha);
    // Adapter: byte APK lengkap + Content-Length + hash cocok.
    const downloaded = await requestBytes(`/api/build/${id}/apk`, { token: GUEST_A });
    assert.equal(downloaded.status, 200);
    assert.equal(downloaded.headers.get('content-type'), 'application/vnd.android.package-archive');
    assert.equal(Number(downloaded.headers.get('content-length')), fixture.length);
    assert.ok(downloaded.buffer.equals(fixture), 'byte unduhan harus sama persis dengan APK fixture');
    assert.equal(createHash('sha256').update(downloaded.buffer).digest('hex'), fixtureSha);
    const preview = await request(`/api/build/${id}/preview`, { token: GUEST_A });
    assert.equal(preview.status, 200);
    assert.match(preview.json.html, /preview-marker-7f3a9/);
    const download = await request(`/api/build/${id}/download`, { token: GUEST_A });
    assert.equal(download.status, 501, 'backend filesystem tidak punya signed download URL');
  });

  test('owner: guest lain 404, tanpa auth 401, admin bisa baca, job admin tak terbaca guest', async () => {
    const submitted = await submitAsGuest(GUEST_A);
    const id = submitted.json.id;
    const other = await request(`/api/build/${id}`, { token: GUEST_B });
    assert.equal(other.status, 404);
    const noAuth = await request(`/api/build/${id}`);
    assert.equal(noAuth.status, 401);
    const admin = await request(`/api/build/${id}`, { token: ACCESS });
    assert.equal(admin.status, 200);
    assert.equal(admin.json.id, id);
    const adminCreated = await request('/api/build', {
      method: 'POST', token: ACCESS,
      body: { id: randomUUID(), prompt: 'Job admin', responseMode: 'apk' },
    });
    assert.equal(adminCreated.status, 202);
    const adminJobId = adminCreated.json.id;
    const guestReadsAdmin = await request(`/api/build/${adminJobId}`, { token: GUEST_A });
    assert.equal(guestReadsAdmin.status, 404, 'guest tidak boleh membaca job admin');
    const adminReadsAdmin = await request(`/api/build/${adminJobId}`, { token: ACCESS });
    assert.equal(adminReadsAdmin.status, 200);
  });

  test('answer oleh guest lain ditolak; jawaban owner diterima', async () => {
    const submitted = await submitAsGuest(GUEST_A);
    const id = submitted.json.id;
    const claimed = await runWorker(['claim', 'new']);
    const claim = JSON.parse(claimed.stdout);
    assert.equal(claim.id, id);
    const asked = await request(`/api/build/${id}/question`, {
      method: 'PUT', token: WORKER_TOKEN, lease: claim.lease,
      body: { question: 'Warna apa?', operationId: randomUUID() },
    });
    assert.equal(asked.status, 200);
    const questionId = asked.json.clarifications.at(-1).id;
    const wrongAnswer = await request(`/api/build/${id}/answer`, {
      method: 'POST', token: GUEST_B,
      body: { questionId, answer: 'Merah' },
    });
    assert.equal(wrongAnswer.status, 404);
    const rightAnswer = await request(`/api/build/${id}/answer`, {
      method: 'POST', token: GUEST_A,
      body: { questionId, answer: 'Biru' },
    });
    assert.equal(rightAnswer.status, 200);
    assert.equal(rightAnswer.json.status, 'queued');
  });

  test('claim replay: lease aktif dikembalikan; setelah question, replay mati (200, job null)', async () => {
    const submitted = await submitAsGuest(GUEST_A);
    const id = submitted.json.id;
    const claimed = await runWorker(['claim', 'new']);
    const claim = JSON.parse(claimed.stdout);
    assert.equal(claim.id, id);
    const lease = claim.lease;
    const claimId = claim.claimRequestId;
    const replay = await request('/api/build/claim', {
      method: 'POST', token: WORKER_TOKEN, body: { claimRequestId: claimId },
    });
    assert.equal(replay.status, 200);
    assert.equal(replay.json.replayed, true);
    assert.equal(replay.json.lease, lease);
    // Question melepaskan lease; replay claim lama tidak boleh memberi lease baru.
    const asked = await request(`/api/build/${id}/question`, {
      method: 'PUT', token: WORKER_TOKEN, lease,
      body: { question: 'Lanjut?', operationId: randomUUID() },
    });
    assert.equal(asked.status, 200);
    const dead = await request('/api/build/claim', {
      method: 'POST', token: WORKER_TOKEN, body: { claimRequestId: claimId },
    });
    assert.equal(dead.status, 200);
    assert.equal(dead.json.replayed, true);
    assert.equal(dead.json.job, null);
    // Script worker juga mengenali replay mati (bukan antrean kosong).
    const scriptDead = await runWorker(['claim', 'reuse']);
    assert.notEqual(scriptDead.code, 0);
    assert.match(scriptDead.stderr, /Claim lama sudah tidak aktif/);
  });

  test('renew dibatasi deadline 30 menit (via manipulasi job di disk)', async () => {
    const submitted = await submitAsGuest(GUEST_A);
    const id = submitted.json.id;
    const claimed = await runWorker(['claim', 'new']);
    const claim = JSON.parse(claimed.stdout);
    const lease = claim.lease;
    const jobPath = join(dir, `${id}.json`);
    const setAttempt = async minutesAgo => {
      const doc = JSON.parse(await readFile(jobPath, 'utf8'));
      doc.attemptStartedAt = Date.now() - minutesAgo * 60_000;
      doc.leaseExpiresAt = Date.now() + 5 * 60_000;
      doc.expires = doc.leaseExpiresAt;
      await writeFile(jobPath, JSON.stringify(doc));
    };
    await setAttempt(29);
    const ok29 = await request(`/api/build/${id}/renew`, { method: 'PUT', token: WORKER_TOKEN, lease });
    assert.equal(ok29.status, 200);
    await setAttempt(31);
    const denied = await request(`/api/build/${id}/renew`, { method: 'PUT', token: WORKER_TOKEN, lease });
    assert.equal(denied.status, 409);
    assert.equal(denied.json.code, 'attempt_expired');
  });
});
