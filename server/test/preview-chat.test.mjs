import { createServer } from 'node:http';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import { zipSync, strToU8 } from 'fflate';
import assert from 'node:assert/strict';
import test from 'node:test';
import { createBuildHandler } from '../build-jobs.mjs';

test('chat replies finish without an APK and preview is private to the submitting device', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'yap-preview-'));
  const worker = 'w'.repeat(64);
  const server = createServer(createBuildHandler({
    directory, env: { YAP_ACCESS_TOKEN: 'a'.repeat(64), YAP_WORKER_TOKEN: worker, YAP_PUBLIC_BUILDS: '1' }
  }));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = 'http://127.0.0.1:' + server.address().port + '/api/build';
  const user = 'guest_' + '1'.repeat(64);
  const other = 'guest_' + '2'.repeat(64);
  const call = (path, token, method = 'GET', body, lease) => fetch(base + path, {
    method, headers: { Authorization: 'Bearer ' + token, ...(lease && { 'X-YAP-Lease': lease }) },
    body: body instanceof Uint8Array ? body : body && JSON.stringify(body)
  });
  try {
    const chatId = randomUUID();
    await call('', user, 'POST', { id: chatId, prompt: 'What can you build?', context: 'Earlier discussion', responseMode: 'chat' });
    const chat = await (await call('/claim', worker, 'POST')).json();
    assert.equal(chat.context, 'Earlier discussion');
    assert.equal((await call('/' + chatId + '/message', user, 'PUT', { message: 'Hello' }, chat.lease)).status, 401);
    const reply = await (await call('/' + chatId + '/message', worker, 'PUT', { message: 'I can help design an app.' }, chat.lease)).json();
    assert.equal(reply.status, 'responded');
    assert.equal(reply.message, 'I can help design an app.');
    assert.equal((await call('/' + chatId, other)).status, 404);
    assert.equal((await call('/claim', worker, 'POST')).status, 204);

    const id = randomUUID();
    await call('', user, 'POST', { id, prompt: 'Build a counter' });
    const claim = await (await call('/claim', worker, 'POST')).json();
    const metadata = await call('/' + id + '/metadata', worker, 'PUT', { appName: 'Hitung Cerdas' }, claim.lease);
    assert.equal(metadata.status, 200);
    const html = '<!DOCTYPE html><html><button onclick="this.textContent=1">Count</button></html>';
    const apk = zipSync({
      'AndroidManifest.xml': strToU8('manifest'),
      'classes.dex': strToU8('dex'),
      'assets/index.html': strToU8(html),
      'assets/unused.txt': strToU8('Private data outside preview')
    });
    const result = await call('/' + id + '/result', worker, 'PUT', apk, claim.lease);
    assert.equal(result.status, 200);
    assert.equal((await result.json()).appName, 'Hitung Cerdas');
    assert.equal((await call('/' + id + '/preview', other)).status, 404);
    assert.deepEqual(await (await call('/' + id + '/preview', user)).json(), { html });
    assert.equal((await call('/' + id + '/message', worker, 'PUT', { message: 'Late' }, claim.lease)).status, 409);
  } finally {
    await new Promise(resolve => server.close(resolve));
    await rm(directory, { recursive: true, force: true });
  }
});
