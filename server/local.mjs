import { createServer } from 'node:http';
import generate from './api/generate.mjs';
import { createBuildHandler } from './build-jobs.mjs';

const builds = createBuildHandler();
const server = createServer((req, res) => {
  const handler = req.url === '/api/generate' ? generate : builds;
  handler(req, res).catch(() => {
    if (res.headersSent) return res.destroy();
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Server tidak dapat memproses request.' }));
  });
});
server.requestTimeout = 300_000;
server.listen(Number(process.env.PORT || 8787), process.env.YAP_BIND_HOST || '127.0.0.1', () => {
  console.log(`YAP gateway listening on port ${server.address().port}`);
});
