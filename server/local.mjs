import { createServer } from 'node:http';
import handler from './api/generate.mjs';

const server = createServer((req, res) => {
  if (req.url !== '/api/generate') {
    res.writeHead(404).end();
    return;
  }
  handler(req, res).catch(() => {
    if (!res.headersSent) res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Server tidak dapat memproses request.' }));
  });
server.requestTimeout = 300_000;
server.listen(Number(process.env.PORT || 8787), '127.0.0.1', () => {
  console.log(`YAP gateway listening on http://127.0.0.1:${server.address().port}`);
});
