import { createServer } from 'node:http';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import test from 'node:test';
import { createBuildHandler } from '../build-jobs.mjs';

test('Muse pauses a claimed job for a question and receives the answer on the next claim', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'yap-clarification-'));
  const android = 'android-test-access-token'.repeat(2);
  const worker = 'muse-test-worker-token'.repeat(2);
  const server = createServer(createBuildHandler({
    directory, env: { YAP_ACCESS_TOKEN: android, YAP_WORKER_TOKEN: worker }
  }));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = 'http://127.0.0.1:' + server.address().port + '/api/build';
  const call = (path, { method = 'GET', token = android, lease, body } = {}) => fetch(base + path, {
    method,
    headers: { Authorization: 'Bearer ' + token, ...(lease && { 'X-YAP-Lease': lease }) },
    body: body && JSON.stringify(body)
  });
  try {
    const id = randomUUID();
    assert.equal((await call('', { method: 'POST', body: { id, prompt: 'Build a tracker' } })).status, 202);
    const claim = await (await call('/claim', { method: 'POST', token: worker })).json();
    const question = { question: 'Should the tracker reset daily or weekly?' };
    assert.equal((await call('/' + id + '/question', { method: 'PUT', lease: claim.lease, body: question })).status, 401);
    const paused = await (await call('/' + id + '/question', { method: 'PUT', token: worker, lease: claim.lease, body: question })).json();
    assert.equal(paused.status, 'awaiting_answer');
    assert.equal((await (await call('/' + id)).json()).clarification.question, question.question);
    assert.deepEqual(await (await call('/signal')).json(), { pending: false });
    assert.equal((await call('/' + id + '/fail', { method: 'PUT', token: worker, lease: claim.lease })).status, 409);
    assert.equal((await call('/' + id + '/answer', { method: 'POST', body: { questionId: randomUUID(), answer: 'Daily' } })).status, 409);
    const answer = { questionId: paused.clarification.id, answer: 'Daily' };
    assert.equal((await call('/' + id + '/answer', { method: 'POST', token: worker, body: answer })).status, 401);
    assert.equal((await call('/' + id + '/answer', { method: 'POST', body: answer })).status, 200);
    const resumedSignal = await (await call('/signal')).json();
    assert.equal(resumedSignal.pending, true);
    assert.match(resumedSignal.wakeId, /^[a-f0-9]{64}$/);
    const resumed = await (await call('/claim', { method: 'POST', token: worker })).json();
    assert.equal(resumed.id, id);
    assert.notEqual(resumed.lease, claim.lease);
    assert.equal(resumed.clarifications[0].answer, 'Daily');
    const repeated = await (await call('/' + id + '/answer', { method: 'POST', body: answer })).json();
    assert.equal(repeated.status, 'building');
    assert.equal((await call('/claim', { method: 'POST', token: worker })).status, 204);
  } finally {
    await new Promise(resolve => server.close(resolve));
    await rm(directory, { recursive: true, force: true });
  }
});
