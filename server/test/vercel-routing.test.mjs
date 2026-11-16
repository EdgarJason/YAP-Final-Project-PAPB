import assert from 'node:assert/strict';
import test from 'node:test';
import { createServer } from 'node:http';
import gateway from '../api/gateway.mjs';

test('single Vercel gateway handles original and rewritten URLs', async () => {
  const server = createServer((req, res) => gateway(req, res));
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    for (const path of ['/api/capabilities', '/api/gateway?yapRoute=capabilities']) {
      const response = await fetch(base + path);
      assert.equal(response.status, 200);
      assert.equal((await response.json()).protocolVersion, 2);
    }
    assert.equal((await fetch(base + '/api/gateway?yapRoute=../escape')).status, 404);
    assert.equal((await fetch(base + '/api/gateway')).status, 404);
  } finally { await new Promise(resolve => server.close(resolve)); }
});
