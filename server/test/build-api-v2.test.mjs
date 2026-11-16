// Router-level contract tests for the v2 API (lib/build-api.mjs) against
// the filesystem repository. No network, no live Supabase.
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import test from 'node:test';
import { zipSync, strToU8 } from 'fflate';
import { createBuildApi } from '../lib/build-api.mjs';
import { createFilesystemRepository } from '../lib/build-repository-filesystem.mjs';
import { sha256Hex } from '../lib/build-service.mjs';

const PREVIEW = '<html><head><title>Calc</title></head><body>calc</body></html>';
const ENV = {
  YAP_ACCESS_TOKEN: 'a'.repeat(64),
  YAP_WORKER_TOKEN: 'w'.repeat(64),
  YAP_PUBLIC_BUILDS: '1',
};
const GUEST = `Bearer guest_${'a'.repeat(64)}`;
const OTHER_GUEST = `Bearer guest_${'b'.repeat(64)}`;
const WORKER = `Bearer ${ENV.YAP_WORKER_TOKEN}`;

function makeFixtureApk(preview = PREVIEW) {
  return Buffer.from(zipSync({
    'AndroidManifest.xml': strToU8('<manifest package="com.example.test"/>'),
    'classes.dex': strToU8('dex\n035\0'),
    'assets/index.html': strToU8(preview),
  }));
}

async function setup() {
  const directory = await mkdtemp(join(tmpdir(), 'yap-api-v2-'));
  const repo = createFilesystemRepository({ directory });
  const api = createBuildApi({ repository: repo, env: { ...ENV } });
  return { directory, repo, api };
}

const call = (api, method, path, { authorization, lease, body, contentType } = {}) => api.dispatch({
  method, path,
  headers: {
    ...(authorization ? { authorization } : {}),
    ...(lease ? { 'x-yap-lease': lease } : {}),
    ...(contentType ? { 'content-type': contentType } : {}),
  },
  body, contentType,
});

test('capabilities is public and advertises protocol v2', async () => {
  const { directory, api } = await setup();
  try {
    const res = await api.dispatch({ method: 'GET', path: '/api/capabilities', headers: {} });
    assert.equal(res.status, 200);
    assert.equal(res.body.protocolVersion, 2);
    assert.equal(res.body.artifactTransfer, 'signed-storage');
    assert.equal(res.body.maxApkBytes, 32 * 1024 * 1024);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('submit/claim/signal flow with guest isolation and worker auth', async () => {
  const { directory, api } = await setup();
  try {
    const id = randomUUID();
    assert.equal((await call(api, 'POST', '/api/build', { body: { id, prompt: 'Buat kalkulator' } })).status, 401);
    const created = await call(api, 'POST', '/api/build', { authorization: GUEST, body: { id, prompt: 'Buat kalkulator' } });
    assert.equal(created.status, 202);
    assert.equal(created.body.status, 'queued');
    const retry = await call(api, 'POST', '/api/build', { authorization: GUEST, body: { id, prompt: 'Buat kalkulator' } });
    assert.equal(retry.status, 200);
    const changed = await call(api, 'POST', '/api/build', { authorization: GUEST, body: { id, prompt: 'Buat tracker' } });
    assert.equal(changed.status, 409);
    assert.equal((await call(api, 'GET', `/api/build/${id}`, { authorization: OTHER_GUEST })).status, 404);

    const signal = await call(api, 'GET', '/api/build/signal', {});
    assert.equal(signal.body.pending, true);
    assert.match(signal.body.wakeId, /^[a-f0-9]{64}$/);

    assert.equal((await call(api, 'POST', '/api/build/claim', { authorization: GUEST, body: {} })).status, 401);
    const claim = await call(api, 'POST', '/api/build/claim', { authorization: WORKER, body: { claimRequestId: randomUUID() } });
    assert.equal(claim.status, 200);
    assert.equal(claim.body.id, id);
    assert.equal(claim.body.protocolVersion, 2);
    assert.ok(claim.body.leaseExpiresAt);
    assert.equal((await call(api, 'POST', '/api/build/claim', { authorization: WORKER, body: {} })).status, 204);
    assert.equal((await call(api, 'GET', '/api/build/signal', {})).body.pending, false);

    // Resume with the same claimRequestId replays the claim.
    const resumed = await call(api, 'POST', '/api/worker/resume', {
      authorization: WORKER, body: { claimRequestId: claim.body.claimRequestId },
    });
    assert.equal(resumed.status, 200);
    assert.equal(resumed.body.replayed, true);
    assert.equal(resumed.body.lease, claim.body.lease);
    return { claim };
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('renew extends the lease; a stale lease cannot mutate', async () => {
  const { directory, api } = await setup();
  try {
    const id = randomUUID();
    await call(api, 'POST', '/api/build', { authorization: GUEST, body: { id, prompt: 'Buat kalkulator' } });
    const claim = await call(api, 'POST', '/api/build/claim', { authorization: WORKER, body: {} });
    const renewed = await call(api, 'PUT', `/api/build/${id}/renew`, { authorization: WORKER, lease: claim.body.lease });
    assert.equal(renewed.status, 200);
    assert.equal(renewed.body.lease, claim.body.lease);
    const bad = await call(api, 'PUT', `/api/build/${id}/fail`, { authorization: WORKER, lease: randomUUID(), body: {} });
    assert.equal(bad.status, 409);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('v2 result rejects legacy binary uploads with 426 and accepts the JSON finalize flow', async () => {
  const { directory, repo, api } = await setup();
  try {
    const id = randomUUID();
    await call(api, 'POST', '/api/build', { authorization: GUEST, body: { id, prompt: 'Buat kalkulator' } });
    const claim = await call(api, 'POST', '/api/build/claim', { authorization: WORKER, body: {} });
    const lease = claim.body.lease;

    const binary = await call(api, 'PUT', `/api/build/${id}/result`, {
      authorization: WORKER, lease, contentType: 'application/vnd.android.package-archive',
      rawBody: Buffer.from('PK\x03\x04fake'),
    });
    assert.equal(binary.status, 426);
    assert.match(binary.body.error, /JSON/);

    const apk = makeFixtureApk();
    const prepared = await call(api, 'POST', `/api/build/${id}/artifacts/prepare`, {
      authorization: WORKER, lease,
      body: { operationId: randomUUID(), kind: 'apk', size: apk.length, sha256: sha256Hex(apk) },
    });
    assert.equal(prepared.status, 200);
    assert.equal(prepared.body.method, 'local');
    assert.equal(prepared.body.uploadUrl, null);
    await repo.storeArtifactBytes({ artifactId: prepared.body.artifactId, bytes: apk });

    const meta = await call(api, 'PUT', `/api/build/${id}/metadata`, {
      authorization: WORKER, lease, body: { appName: 'Kalkulator' },
    });
    assert.equal(meta.status, 200);
    assert.equal(meta.body.appName, 'Kalkulator');

    const finalized = await call(api, 'PUT', `/api/build/${id}/result`, {
      authorization: WORKER, lease,
      body: {
        operationId: randomUUID(), artifactId: prepared.body.artifactId,
        previewHtml: PREVIEW, verification: { apksigner: 'verified' },
      },
    });
    assert.equal(finalized.status, 200);
    assert.equal(finalized.body.status, 'ready');
    assert.equal(finalized.body.sha256, sha256Hex(apk));

    const preview = await call(api, 'GET', `/api/build/${id}/preview`, { authorization: GUEST });
    assert.equal(preview.status, 200);
    assert.equal(preview.body.html, PREVIEW);

    // Filesystem backend has no signed Storage download; /download says so.
    const download = await call(api, 'GET', `/api/build/${id}/download`, { authorization: GUEST });
    assert.equal(download.status, 501);
    // The legacy binary route still serves the file on the filesystem backend.
    const apkRoute = await call(api, 'GET', `/api/build/${id}/apk`, { authorization: GUEST });
    assert.equal(apkRoute.status, 200);
    assert.equal(apkRoute.binaryFile.size, apk.length);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('question/answer round trip through the router', async () => {
  const { directory, api } = await setup();
  try {
    const id = randomUUID();
    await call(api, 'POST', '/api/build', { authorization: GUEST, body: { id, prompt: 'Buat tracker', responseMode: 'chat' } });
    const claim = await call(api, 'POST', '/api/build/claim', { authorization: WORKER, body: {} });
    const asked = await call(api, 'PUT', `/api/build/${id}/question`, {
      authorization: WORKER, lease: claim.body.lease, body: { question: 'Daily or weekly?' },
    });
    assert.equal(asked.status, 200);
    assert.equal(asked.body.status, 'awaiting_answer');
    const questionId = asked.body.clarification.id;
    const answered = await call(api, 'POST', `/api/build/${id}/answer`, {
      authorization: GUEST, body: { questionId, answer: 'Weekly' },
    });
    assert.equal(answered.status, 200);
    assert.equal(answered.body.status, 'queued');
    const resumed = await call(api, 'POST', '/api/build/claim', { authorization: WORKER, body: {} });
    assert.equal(resumed.body.clarifications[0].answer, 'Weekly');
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('unknown routes are 404 and unconfigured gateways are 503', async () => {
  const { directory } = await setup();
  try {
    const repo = createFilesystemRepository({ directory });
    const broken = createBuildApi({ repository: repo, env: { YAP_ACCESS_TOKEN: 'short', YAP_WORKER_TOKEN: 'short' } });
    const res = await broken.dispatch({ method: 'GET', path: '/api/build/signal', headers: {} });
    assert.equal(res.status, 503);
    const api = createBuildApi({ repository: repo, env: { ...ENV } });
    assert.equal((await api.dispatch({ method: 'GET', path: '/api/nope', headers: {} })).status, 404);
  } finally { await rm(directory, { recursive: true, force: true }); }
});
