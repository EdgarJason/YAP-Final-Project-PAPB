import { createServer } from 'node:http';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import assert from 'node:assert/strict';
import test from 'node:test';
import { createBuildHandler } from '../build-jobs.mjs';

test('public builds keep device jobs private and worker routes authenticated', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'yap-guest-'));
  const env = {
    YAP_ACCESS_TOKEN: 'a'.repeat(64), YAP_WORKER_TOKEN: 'w'.repeat(64), YAP_PUBLIC_BUILDS: '1'
  };
  const handler = createBuildHandler({ directory, env });
  const server = createServer(handler);
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = 'http://127.0.0.1:' + server.address().port + '/api/build';
  const alice = 'guest_' + 'a'.repeat(64);
  const bob = 'guest_' + 'b'.repeat(64);
  const call = (path, token, method = 'GET', body, lease) => fetch(base + path, {
    method, headers: {
      Authorization: 'Bearer ' + token,
      ...(lease && { 'X-YAP-Lease': lease })
    },
    body: body && JSON.stringify(body)
  });
  try {
    const id = randomUUID();
    const body = { id, prompt: 'Make a habit tracker' };
    assert.equal((await call('', alice, 'POST', body)).status, 202);
    assert.equal((await call('', alice, 'POST', body)).status, 200);
    assert.equal((await call('/' + id, bob)).status, 404);
    assert.equal((await call('', bob, 'POST', body)).status, 404);
    assert.equal((await call('/claim', alice, 'POST')).status, 401);
    assert.equal((await call('/' + id + '/question', alice, 'PUT', { question: 'Daily?' })).status, 401);
    const claim = await (await call('/claim', env.YAP_WORKER_TOKEN, 'POST')).json();
    assert.equal(claim.id, id);
    const paused = await (await call('/' + id + '/question', env.YAP_WORKER_TOKEN, 'PUT',
      { question: 'Daily or weekly?' }, claim.lease)).json();
    const answer = { questionId: paused.clarification.id, answer: 'Weekly' };
    assert.equal((await call('/' + id + '/answer', bob, 'POST', answer)).status, 404);
    assert.equal((await call('/' + id + '/answer', alice, 'POST', answer)).status, 200);
    const resumed = await (await call('/claim', env.YAP_WORKER_TOKEN, 'POST')).json();
    assert.equal(resumed.clarifications[0].answer, 'Weekly');
    await call('/' + id + '/fail', env.YAP_WORKER_TOKEN, 'PUT', null, resumed.lease);
    env.YAP_PUBLIC_BUILDS = '0';
    assert.equal((await call('/' + id, alice)).status, 401);
    assert.equal((await call('/' + id, env.YAP_ACCESS_TOKEN)).status, 200);
  } finally {
    await new Promise(resolve => server.close(resolve));
    await rm(directory, { recursive: true, force: true });
  }
});
