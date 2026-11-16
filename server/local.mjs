import { createServer } from 'node:http';
import generate from './api/generate.mjs';
import { createBuildHandler } from './build-jobs.mjs';
import { createVercelHandler } from './lib/vercel-adapter.mjs';

// Default backend is the legacy disk queue (filesystem) — the rollback path.
// Set YAP_STORAGE_BACKEND=supabase to serve the v2 cloud API from this
// process instead (requires SUPABASE_URL + SUPABASE_SECRET_KEY), or
// YAP_API_VERSION=v2 to serve the v2 API on the local filesystem backend
// (for local end-to-end testing of the v2 contract without a live project).
// The /api/generate route is unaffected by the backend choice.
const builds = createBuildHandler();
const useV2 = process.env.YAP_STORAGE_BACKEND === 'supabase' || process.env.YAP_API_VERSION === 'v2';
const v2 = useV2 ? createVercelHandler() : null;
const server = createServer((req, res) => {
  const handler = req.url === '/api/generate' ? generate : (v2 ?? builds);
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
