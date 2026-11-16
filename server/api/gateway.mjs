import { createVercelHandler } from '../lib/vercel-adapter.mjs';

const handle = createVercelHandler();
export default function gateway(req, res) {
  const url = new URL(req.url || '/', 'https://gateway.invalid');
  if (url.pathname === '/api/gateway' || url.pathname === '/api/gateway.mjs') {
    const route = req.query?.yapRoute ?? url.searchParams.get('yapRoute');
    if (typeof route !== 'string' || !/^[a-zA-Z0-9/-]+$/.test(route)) {
      res.writeHead(404, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
      return res.end(JSON.stringify({ error: 'Endpoint tidak ditemukan.' }));
    }
    req.url = '/api/' + route;
  }
  return handle(req, res);
}
