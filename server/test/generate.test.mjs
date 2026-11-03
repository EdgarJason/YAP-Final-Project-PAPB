import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { createHandler, parseGeneration } from '../api/generate.mjs';

const token = 'test-access-token-with-at-least-32-characters';
const secret = 'test-provider-key-must-not-reach-client';
const html = '<!DOCTYPE html><html><head></head><body><button>Hitung</button></body></html>';
const completion = (content = html, finish_reason = 'stop') => ({
  choices: [{ finish_reason, message: { content } }]
});

async function gateway(t, options = {}) {
  const server = createServer(createHandler({
    env: { YAP_ACCESS_TOKEN: token, META_API_KEY: secret }, ...options
  }));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  t.after(() => new Promise(resolve => { server.closeAllConnections(); server.close(resolve); }));
  return (body, authorization = `Bearer ${token}`) => fetch(`http://127.0.0.1:${server.address().port}/api/generate`, {
    method: 'POST', headers: { Authorization: authorization }, body: JSON.stringify(body)
  });
}

test('rejects unauthorized requests without calling Muse', async t => {
  const call = await gateway(t, { fetchImpl: () => { throw new Error('must not call'); } });
  assert.equal((await call({ prompt: 'hello' }, 'Bearer wrong')).status, 401);
});

test('fails closed when secrets are missing or reused', async t => {
  for (const env of [{}, { META_API_KEY: secret, YAP_ACCESS_TOKEN: secret }]) {
    const call = await gateway(t, { env });
    assert.equal((await call({ prompt: 'hello' })).status, 503);
  }
});

test('forwards creation and revision to the fixed Muse model, returns isolated HTML only', async t => {
  let captured;
  const call = await gateway(t, { fetchImpl: async (url, init) => {
    captured = { url, init, body: JSON.parse(init.body) };
    return Response.json(completion());
  } });
  const response = await call({ prompt: 'Buat kalkulator', previousHtml: html });
  assert.equal(response.status, 200);
  const data = await response.json();
  assert.equal(captured.url, 'https://api.meta.ai/v1/chat/completions');
  assert.equal(captured.body.model, 'muse-spark-1.3');
  assert.equal(captured.init.headers.Authorization, `Bearer ${secret}`);
  assert.ok(captured.body.messages[1].content.includes(html));
  assert.equal(captured.body.messages.at(-1).content, 'Buat kalkulator');
  assert.ok(data.html.includes('Content-Security-Policy'));
  assert.ok(!JSON.stringify(data).includes(secret));
});

test('rejects invalid prompts and excessive input before spending provider tokens', async t => {
  const call = await gateway(t, { fetchImpl: () => { throw new Error('must not call'); } });
  for (const body of [null, {}, { prompt: ' ' }, { prompt: 'x'.repeat(6001) }, { prompt: 'hi', previousHtml: 12 }]) {
    assert.equal((await call(body)).status, 400);
  }
  assert.equal((await call({ prompt: 'x'.repeat(256_001) })).status, 413);
});

test('does not expose upstream errors or credentials', async t => {
  const call = await gateway(t, { fetchImpl: async () => new Response(secret, { status: 401 }) });
  const response = await call({ prompt: 'hello' });
  assert.equal(response.status, 502);
  assert.ok(!(await response.text()).includes(secret));
});

test('accepts bodies pre-parsed by a serverless runtime', async () => {
  const handler = createHandler({
    env: { YAP_ACCESS_TOKEN: token, META_API_KEY: secret },
    fetchImpl: async () => Response.json(completion())
  });
  let status;
  let output;
  const req = { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: { prompt: 'hello' } };
  const res = { writeHead(code) { status = code; }, end(body) { output = JSON.parse(body); } };
  await handler(req, res);
  assert.equal(status, 200);
  assert.equal(output.model, 'muse-spark-1.3');
});

test('rejects truncated and malformed provider results', () => {
  assert.throws(() => parseGeneration(completion(html, 'length')));
  assert.throws(() => parseGeneration(completion('Here is your code')));
  assert.throws(() => parseGeneration(completion('<html><body></body></html>')));
  assert.ok(parseGeneration(completion('```html\n' + html + '\n```')).includes('Content-Security-Policy'));
});

test('returns a retryable error for truncated provider output', async t => {
  const call = await gateway(t, { fetchImpl: async () => Response.json(completion(html, 'length')) });
  assert.equal((await call({ prompt: 'hello' })).status, 502);
});

test('limits concurrent requests and releases the slot after completion', async t => {
  let release;
  let entered;
  const ready = new Promise(resolve => { entered = resolve; });
  const call = await gateway(t, { fetchImpl: async () => {
    entered();
    await new Promise(resolve => { release = resolve; });
    return Response.json(completion());
  } });
  const first = call({ prompt: 'first' });
  await ready;
  assert.equal((await call({ prompt: 'second' })).status, 429);
  release();
  assert.equal((await first).status, 200);
});
