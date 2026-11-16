// Contract tests for the filesystem repository (lib/build-repository-filesystem.mjs).
// These run locally with no live Supabase. The fixture APK is a minimal ZIP
// (AndroidManifest.xml + classes.dex + assets/index.html) built with fflate —
// it exercises hash/size/ZIP/preview matching, NOT a real signed APK.
// apksigner verification is a worker-side step and is not claimed here.
import { mkdtemp, rm, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import test from 'node:test';
import { zipSync, strToU8 } from 'fflate';
import { createFilesystemRepository } from '../lib/build-repository-filesystem.mjs';
import { sha256Hex, LIMITS } from '../lib/build-service.mjs';

const PREVIEW = '<html><head><title>Calc</title></head><body>calc</body></html>';

function makeFixtureApk(preview = PREVIEW) {
  return Buffer.from(zipSync({
    'AndroidManifest.xml': strToU8('<manifest package="com.example.test"/>'),
    'classes.dex': strToU8('dex\n035\0'),
    'assets/index.html': strToU8(preview),
  }));
}

async function setup() {
  const directory = await mkdtemp(join(tmpdir(), 'yap-fs-repo-'));
  let clock = Date.now();
  const repo = createFilesystemRepository({ directory, now: () => clock });
  const advance = ms => { clock += ms; };
  return { directory, repo, advance, clock: () => clock };
}

async function submitClaimed(repo, { responseMode = 'apk' } = {}) {
  const id = randomUUID();
  await repo.create({ id, ownerHash: 'owner-hash', prompt: 'Buat kalkulator', context: '', responseMode });
  const { job } = await repo.claim({ claimRequestId: randomUUID() });
  assert.ok(job, 'claim should succeed');
  assert.equal(job.id, id);
  return { id, job };
}

test('submit is idempotent for identical retries and 409 for a changed payload', async () => {
  const { directory, repo } = await setup();
  try {
    const id = randomUUID();
    const payload = { id, ownerHash: 'owner-hash', prompt: 'Buat kalkulator', context: '', responseMode: 'apk' };
    const first = await repo.create(payload);
    assert.equal(first.created, true);
    const retry = await repo.create(payload);
    assert.equal(retry.created, false);
    assert.equal(retry.job.id, id);
    await assert.rejects(
      repo.create({ ...payload, prompt: 'Buat tracker' }),
      error => error.status === 409,
    );
    await assert.rejects(
      repo.create({ ...payload, ownerHash: 'other-owner' }),
      error => error.status === 404,
    );
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('concurrent claims hand out one job, once, and the slot blocks a second claim', async () => {
  const { directory, repo } = await setup();
  try {
    const id = randomUUID();
    await repo.create({ id, ownerHash: 'owner-hash', prompt: 'Buat kalkulator', context: '', responseMode: 'apk' });
    const results = await Promise.all(Array.from({ length: 10 }, () =>
      repo.claim({ claimRequestId: randomUUID() })));
    const claimed = results.filter(result => result.job);
    assert.equal(claimed.length, 1);
    assert.equal(claimed[0].job.id, id);
    assert.equal(new Set(claimed.map(result => result.job.lease)).size, 1);
    // A second, different job cannot be claimed while the slot is busy.
    const id2 = randomUUID();
    await repo.create({ id: id2, ownerHash: 'owner-hash', prompt: 'Buat tracker', context: '', responseMode: 'apk' });
    const blocked = await repo.claim({ claimRequestId: randomUUID() });
    assert.equal(blocked.job, null);
    assert.equal(blocked.slotBusy, true);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('claim is idempotent by claimRequestId: a retry returns the same lease', async () => {
  const { directory, repo } = await setup();
  try {
    const id = randomUUID();
    await repo.create({ id, ownerHash: 'owner-hash', prompt: 'Buat kalkulator', context: '', responseMode: 'apk' });
    const claimRequestId = randomUUID();
    const first = await repo.claim({ claimRequestId });
    const replay = await repo.claim({ claimRequestId });
    assert.equal(replay.replayed, true);
    assert.equal(replay.job.lease, first.job.lease);
    assert.equal(replay.job.id, id);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('a stale lease is rejected and the job can be reclaimed afterwards', async () => {
  const { directory, repo, advance } = await setup();
  try {
    const { id, job } = await submitClaimed(repo);
    advance(LIMITS.leaseMs + 1000);
    await assert.rejects(
      repo.askQuestion({ jobId: id, lease: job.lease, operationId: randomUUID(), question: 'Daily?' }),
      error => error.status === 409,
    );
    await assert.rejects(
      repo.fail({ jobId: id, lease: job.lease, operationId: randomUUID() }),
      error => error.status === 409,
    );
    const reclaimed = await repo.claim({ claimRequestId: randomUUID() });
    assert.ok(reclaimed.job);
    assert.notEqual(reclaimed.job.lease, job.lease);
    assert.equal(reclaimed.job.attemptCount, 2);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('question releases the lease, an answer requeues the same job, and a repeated answer is idempotent', async () => {
  const { directory, repo } = await setup();
  try {
    const { id, job } = await submitClaimed(repo);
    const asked = await repo.askQuestion({ jobId: id, lease: job.lease, operationId: randomUUID(), question: 'Daily or weekly?' });
    assert.equal(asked.job.status, 'awaiting_answer');
    assert.equal(asked.job.lease, null);
    const questionId = asked.job.clarifications[0].id;
    // The released lease can no longer mutate the job.
    await assert.rejects(
      repo.fail({ jobId: id, lease: job.lease, operationId: randomUUID() }),
      error => error.status === 409,
    );
    const answered = await repo.answer({ jobId: id, ownerHash: 'owner-hash', questionId, answer: 'Weekly' });
    assert.equal(answered.job.status, 'queued');
    assert.equal(answered.job.queueRevision, 1);
    const repeated = await repo.answer({ jobId: id, ownerHash: 'owner-hash', questionId, answer: 'Weekly' });
    assert.equal(repeated.alreadyAnswered, true);
    assert.equal(repeated.job.status, 'queued');
    const resumed = await repo.claim({ claimRequestId: randomUUID() });
    assert.equal(resumed.job.clarifications[0].answer, 'Weekly');
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('at most five clarifications per job', async () => {
  const { directory, repo } = await setup();
  try {
    const id = randomUUID();
    await repo.create({ id, ownerHash: 'owner-hash', prompt: 'Buat tracker', context: '', responseMode: 'apk' });
    for (let n = 0; n < 5; n++) {
      const { job } = await repo.claim({ claimRequestId: randomUUID() });
      const asked = await repo.askQuestion({ jobId: id, lease: job.lease, operationId: randomUUID(), question: `Question ${n}?` });
      await repo.answer({ jobId: id, ownerHash: 'owner-hash', questionId: asked.job.clarifications.at(-1).id, answer: 'Yes' });
    }
    const { job } = await repo.claim({ claimRequestId: randomUUID() });
    await assert.rejects(
      repo.askQuestion({ jobId: id, lease: job.lease, operationId: randomUUID(), question: 'One more?' }),
      error => error.status === 409,
    );
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('message is chat-only and fail releases the slot', async () => {
  const { directory, repo } = await setup();
  try {
    const apkJob = await submitClaimed(repo, { responseMode: 'apk' });
    await assert.rejects(
      repo.sendMessage({ jobId: apkJob.id, lease: apkJob.job.lease, operationId: randomUUID(), message: 'Hi' }),
      error => error.status === 409,
    );
    const failed = await repo.fail({ jobId: apkJob.id, lease: apkJob.job.lease, operationId: randomUUID(), errorCode: 'build_failed' });
    assert.equal(failed.job.status, 'failed');

    const chat = await submitClaimed(repo, { responseMode: 'chat' });
    const messaged = await repo.sendMessage({ jobId: chat.id, lease: chat.job.lease, operationId: randomUUID(), message: 'Here is the plan.' });
    assert.equal(messaged.job.status, 'responded');
    assert.equal(messaged.job.message, 'Here is the plan.');
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('prepare -> store bytes -> finalize publishes ready only with matching hash/size/preview', async () => {
  const { directory, repo } = await setup();
  try {
    const { id, job } = await submitClaimed(repo);
    const apk = makeFixtureApk();
    const prepareOp = randomUUID();
    const prepared = await repo.prepareArtifact({
      jobId: id, lease: job.lease, operationId: prepareOp,
      kind: 'apk', size: apk.length, sha256: sha256Hex(apk),
    });
    assert.match(prepared.artifact.objectPath, new RegExp(`^jobs/${id}/`));
    assert.equal(prepared.artifact.status, 'pending');
    // Re-prepare with the same operationId returns the same reservation.
    const replayedPrepare = await repo.prepareArtifact({
      jobId: id, lease: job.lease, operationId: prepareOp,
      kind: 'apk', size: apk.length, sha256: sha256Hex(apk),
    });
    assert.equal(replayedPrepare.replayed, true);
    assert.equal(replayedPrepare.artifact.id, prepared.artifact.id);

    await repo.storeArtifactBytes({ artifactId: prepared.artifact.id, bytes: apk });
    const finalized = await repo.finalize({
      jobId: id, lease: job.lease, operationId: randomUUID(),
      artifactId: prepared.artifact.id, previewHtml: PREVIEW, verification: { apksigner: 'verified' },
    });
    assert.equal(finalized.job.status, 'ready');
    assert.equal(finalized.job.sha256, sha256Hex(apk));
    assert.equal(finalized.job.size, apk.length);
    assert.equal(finalized.job.previewHtml, PREVIEW);
    assert.ok(finalized.job.resultExpiresAt);

    const preview = await repo.getPreview(id);
    assert.equal(preview.html, PREVIEW);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('finalize rejects a mismatched preview and a tampered artifact', async () => {
  const { directory, repo } = await setup();
  try {
    const { id, job } = await submitClaimed(repo);
    const apk = makeFixtureApk();
    const prepared = await repo.prepareArtifact({
      jobId: id, lease: job.lease, operationId: randomUUID(),
      kind: 'apk', size: apk.length, sha256: sha256Hex(apk),
    });
    await repo.storeArtifactBytes({ artifactId: prepared.artifact.id, bytes: apk });
    await assert.rejects(
      repo.finalize({
        jobId: id, lease: job.lease, operationId: randomUUID(),
        artifactId: prepared.artifact.id, previewHtml: '<html>different</html>', verification: null,
      }),
      /Preview tidak cocok/,
    );
    // A retry of the same finalize operationId after success returns ready.
    const finalizeOp = randomUUID();
    const ok = await repo.finalize({
      jobId: id, lease: job.lease, operationId: finalizeOp,
      artifactId: prepared.artifact.id, previewHtml: PREVIEW, verification: null,
    });
    assert.equal(ok.job.status, 'ready');
    // The lease was released by the successful finalize, so a fresh
    // operationId is rejected — but the recorded operationId replays.
    await assert.rejects(
      repo.finalize({
        jobId: id, lease: job.lease, operationId: randomUUID(),
        artifactId: prepared.artifact.id, previewHtml: PREVIEW, verification: null,
      }),
      error => error.status === 409,
    );
    const replay = await repo.finalize({
      jobId: id, lease: job.lease, operationId: finalizeOp,
      artifactId: prepared.artifact.id, previewHtml: PREVIEW, verification: null,
    });
    assert.equal(replay.replayed, true);
    assert.equal(replay.job.status, 'ready');
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('renew extends the same lease', async () => {
  const { directory, repo, advance } = await setup();
  try {
    const { id, job } = await submitClaimed(repo);
    advance(60_000);
    const renewed = await repo.renew({ jobId: id, lease: job.lease });
    assert.equal(renewed.job.lease, job.lease);
    assert.ok(renewed.leaseExpiresAt > job.leaseExpiresAt);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('signal reflects pending work and goes quiet after claim', async () => {
  const { directory, repo } = await setup();
  try {
    assert.deepEqual(await repo.signal(), { pending: false });
    const id = randomUUID();
    await repo.create({ id, ownerHash: 'owner-hash', prompt: 'Buat kalkulator', context: '', responseMode: 'apk' });
    const signal = await repo.signal();
    assert.equal(signal.pending, true);
    assert.equal(signal.jobId, id);
    await repo.claim({ claimRequestId: randomUUID() });
    assert.deepEqual(await repo.signal(), { pending: false });
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('claim replay after reclaim returns a dead replay, never the new lease', async () => {
  const { directory, repo, advance } = await setup();
  try {
    const id = randomUUID();
    await repo.create({ id, ownerHash: 'owner-hash', prompt: 'Buat kalkulator', context: '', responseMode: 'apk' });
    const claimId = randomUUID();
    const first = await repo.claim({ claimRequestId: claimId });
    assert.equal(first.job.id, id);
    const lease1 = first.job.lease;
    // Lease expires, job is reclaimed under a new lease.
    advance(LIMITS.leaseMs + 1000);
    const second = await repo.claim({ claimRequestId: randomUUID() });
    assert.equal(second.job.id, id);
    assert.notEqual(second.job.lease, lease1);
    // Replaying the first claimRequestId must NOT hand out the new lease.
    const replay = await repo.claim({ claimRequestId: claimId });
    assert.equal(replay.replayed, true);
    assert.equal(replay.job, null);
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('renew is capped at the 30-minute attempt deadline', async () => {
  const { directory, repo, advance, clock } = await setup();
  try {
    const { id, job } = await submitClaimed(repo);
    const start = job.attemptStartedAt;
    const deadline = start + LIMITS.maxAttemptMs;
    // Keep the lease alive with renews: 9 -> 19, 18 -> 28, 27 -> capped at 30.
    advance(9 * 60_000);
    const r1 = await repo.renew({ jobId: id, lease: job.lease });
    assert.equal(r1.leaseExpiresAt, start + 19 * 60_000);
    advance(9 * 60_000);
    const r2 = await repo.renew({ jobId: id, lease: job.lease });
    assert.equal(r2.leaseExpiresAt, start + 28 * 60_000);
    advance(9 * 60_000);
    const r3 = await repo.renew({ jobId: id, lease: job.lease });
    assert.equal(r3.leaseExpiresAt, deadline, 'renew terakhir di-cap di deadline 30 menit');
    // Put the job 31 minutes into its attempt while the lease is still
    // alive (same state the SQL test creates via UPDATE): renew must be
    // rejected with attempt_expired, not silently extended.
    const jobPath = join(directory, `${id}.json`);
    const doc = JSON.parse(await readFile(jobPath, 'utf8'));
    doc.attemptStartedAt = clock() - 31 * 60_000;
    doc.leaseExpiresAt = clock() + 5 * 60_000;
    doc.expires = doc.leaseExpiresAt;
    await writeFile(jobPath, JSON.stringify(doc));
    await assert.rejects(
      repo.renew({ jobId: id, lease: job.lease }),
      error => error.status === 409 && error.code === 'attempt_expired',
    );
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test('owner checks are exact: other guests and empty owners are rejected', async () => {
  const { directory, repo } = await setup();
  try {
    const { id, job } = await submitClaimed(repo);
    const asked = await repo.askQuestion({ jobId: id, lease: job.lease, operationId: randomUUID(), question: 'Warna apa?' });
    const questionId = asked.job.clarifications.at(-1).id;
    // requireOwner is enforced by answer(): wrong owner, empty owner and
    // missing owner all get 404; the admin namespace may answer any job.
    await assert.rejects(
      repo.answer({ jobId: id, ownerHash: 'other-owner', questionId, answer: 'Merah' }),
      error => error.status === 404,
    );
    await assert.rejects(
      repo.answer({ jobId: id, ownerHash: null, questionId, answer: 'Merah' }),
      error => error.status === 404,
    );
    await assert.rejects(
      repo.answer({ jobId: id, ownerHash: '', questionId, answer: 'Merah' }),
      error => error.status === 404,
    );
    await assert.rejects(
      repo.answer({ jobId: id, ownerHash: undefined, questionId, answer: 'Merah' }),
      error => error.status === 404,
    );
    const adminAnswer = await repo.answer({ jobId: id, ownerHash: 'admin', questionId, answer: 'Biru' });
    assert.equal(adminAnswer.job.status, 'queued');
  } finally { await rm(directory, { recursive: true, force: true }); }
});
