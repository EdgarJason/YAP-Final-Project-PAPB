// Contract tests for the shared validation layer (lib/build-service.mjs).
// No I/O, no network, no live Supabase.
import assert from 'node:assert/strict';
import test from 'node:test';
import {
  LIMITS, validateSubmit, validateAnswer, validateQuestion, validateMessage,
  validateAppName, validatePreviewHtml, validateArtifactDescriptor, validateResult,
  canonicalRequestHash, wakeIdFor, publicJob,
} from '../lib/build-service.mjs';

const UUID = '123e4567-e89b-12d3-a456-426614174000';

test('limits match the documented contract', () => {
  assert.equal(LIMITS.maxApkBytes, 32 * 1024 * 1024);
  assert.equal(LIMITS.maxPromptChars, 6000);
  assert.equal(LIMITS.maxContextChars, 12000);
  assert.equal(LIMITS.maxQuestionChars, 600);
  assert.equal(LIMITS.maxAnswerChars, 1200);
  assert.equal(LIMITS.maxMessageChars, 6000);
  assert.equal(LIMITS.maxAppNameChars, 80);
  assert.equal(LIMITS.maxClarifications, 5);
  assert.equal(LIMITS.leaseMs, 10 * 60_000);
  assert.equal(LIMITS.protocolVersion, 2);
});

test('validateSubmit enforces prompt/context/mode limits', () => {
  assert.deepEqual(validateSubmit({ id: UUID, prompt: ' Buat kalkulator ', context: '', responseMode: 'chat' }), {
    id: UUID, prompt: 'Buat kalkulator', context: '', responseMode: 'chat',
  });
  assert.throws(() => validateSubmit({ id: UUID, prompt: '' }), /wajib diisi/);
  assert.throws(() => validateSubmit({ id: UUID, prompt: 'x'.repeat(6001) }), /maksimal 6000/);
  assert.throws(() => validateSubmit({ id: UUID, prompt: 'ok', context: 'x'.repeat(12001) }), /maksimal 12000/);
  assert.throws(() => validateSubmit({ id: 'not-a-uuid', prompt: 'ok' }), /tidak valid/);
  assert.throws(() => validateSubmit({ id: UUID, prompt: 'ok', responseMode: 'sms' }), /Mode respons/);
});

test('question/answer/message/appName limits', () => {
  assert.throws(() => validateQuestion({ question: 'x'.repeat(601) }), /maksimal 600/);
  assert.throws(() => validateAnswer({ questionId: UUID, answer: 'x'.repeat(1201) }), /maksimal 1200/);
  assert.throws(() => validateMessage({ message: 'x'.repeat(6001) }), /maksimal 6000/);
  assert.throws(() => validateAppName({ appName: 'x'.repeat(81) }), /maksimal 80/);
  assert.throws(() => validateAppName({ appName: 'Bad\u0001Name' }), /karakter kontrol/);
  assert.deepEqual(validateAppName({ appName: ' Kalkulator ' }), { appName: 'Kalkulator' });
});

test('preview HTML is bounded raw and after JSON escaping', () => {
  assert.equal(validatePreviewHtml(null), null);
  assert.equal(validatePreviewHtml('<html></html>'), '<html></html>');
  assert.throws(() => validatePreviewHtml('x'.repeat(LIMITS.maxPreviewHtmlBytes + 1)), /2 MiB/);
  // Many escapable characters: raw size is under 2 MiB but the JSON-encoded
  // form exceeds 3 MiB (each '"' becomes '\"', doubling its size).
  const escapable = '"'.repeat(1_600_000); // 1.6 MiB raw, ~3.2 MiB escaped
  assert.throws(() => validatePreviewHtml(escapable), /3 MiB/);
});

test('artifact descriptor rejects oversized APK and bad hashes', () => {
  assert.throws(
    () => validateArtifactDescriptor({ kind: 'apk', size: LIMITS.maxApkBytes + 1, sha256: 'a'.repeat(64) }),
    error => error.status === 413,
  );
  assert.throws(
    () => validateArtifactDescriptor({ kind: 'apk', size: 100, sha256: 'not-a-hash' }),
    /SHA-256/,
  );
  assert.throws(() => validateArtifactDescriptor({ kind: 'video', size: 100, sha256: 'a'.repeat(64) }), /Jenis artifact/);
  assert.deepEqual(
    validateArtifactDescriptor({ kind: 'apk', size: 100, sha256: 'a'.repeat(64) }),
    { kind: 'apk', size: 100, sha256: 'a'.repeat(64) },
  );
});

test('validateResult requires operationId, artifactId and a bounded preview', () => {
  assert.throws(() => validateResult({ artifactId: UUID }), /ID operasi/);
  assert.throws(() => validateResult({ operationId: UUID, artifactId: 'bad' }), /ID artifact/);
  assert.deepEqual(
    validateResult({ operationId: UUID, artifactId: UUID, previewHtml: null, verification: { apksigner: 'verified' } }),
    { operationId: UUID, artifactId: UUID, previewHtml: null, verification: { apksigner: 'verified' } },
  );
});

test('canonical request hash distinguishes payload changes', () => {
  const base = { id: UUID, ownerHash: 'owner', prompt: 'Buat kalkulator', context: '', responseMode: 'apk' };
  const same = canonicalRequestHash(base);
  assert.equal(canonicalRequestHash({ ...base }), same);
  assert.notEqual(canonicalRequestHash({ ...base, prompt: 'Buat tracker' }), same);
  assert.notEqual(canonicalRequestHash({ ...base, ownerHash: 'other' }), same);
});

test('wakeId is stable inside a 2-minute window and changes after it', () => {
  const input = { jobId: UUID, queueRevision: 1, lease: '' };
  assert.equal(wakeIdFor({ ...input, now: 1_000 }, 'secret'), wakeIdFor({ ...input, now: 60_000 }, 'secret'));
  assert.notEqual(wakeIdFor({ ...input, now: 1_000 }, 'secret'), wakeIdFor({ ...input, now: 200_000 }, 'secret'));
  assert.notEqual(wakeIdFor({ ...input, now: 1_000 }, 'secret'), wakeIdFor({ ...input, now: 1_000 }, 'other'));
  assert.match(wakeIdFor({ ...input, now: 1_000 }), /^[a-f0-9]{64}$/);
});

test('publicJob keeps the legacy shape and hides internals', () => {
  const view = publicJob({
    id: UUID, status: 'ready', sha256: 'a'.repeat(64), size: 123,
    clarifications: [{ id: UUID, question: 'Daily?', answer: 'Weekly' }],
    message: null, appName: 'Tracker',
    prompt: 'secret prompt', context: 'secret', ownerHash: 'owner', lease: 'lease',
  });
  assert.deepEqual(Object.keys(view).sort(), [
    'appName', 'artifactAvailable', 'clarification', 'clarifications', 'id', 'message', 'sha256', 'size', 'status',
  ].sort());
  assert.equal(view.clarification.answer, 'Weekly');
});
