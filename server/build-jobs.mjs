import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import { mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync, statSync, createReadStream } from 'node:fs';
import { resolve, join } from 'node:path';
import { pipeline } from 'node:stream/promises';

const maxApk = 32 * 1024 * 1024;
const leaseTime = 10 * 60_000;
const digest = value => createHash('sha256').update(value).digest();
const matches = (actual, token) => timingSafeEqual(digest(actual || ''), digest('Bearer ' + token));

function reply(res, code, body) {
  res.writeHead(code, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(body));
}

async function readBody(req, limit) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) return null;
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

export function createBuildHandler({ env = process.env, directory = env.YAP_BUILD_DIR || '.build-jobs', now = Date.now } = {}) {
  const root = resolve(directory);
  mkdirSync(root, { recursive: true, mode: 0o700 });
  const jobPath = id => join(root, id + '.json');
  const apkPath = id => join(root, id + '.apk');
  const read = id => {
    try { return JSON.parse(readFileSync(jobPath(id), 'utf8')); }
    catch (error) { if (error.code === 'ENOENT') return null; throw error; }
  };
  const write = job => {
    const path = jobPath(job.id);
    writeFileSync(path + '.tmp', JSON.stringify(job), { mode: 0o600 });
    renameSync(path + '.tmp', path);
  };
  const pending = () => readdirSync(root)
    .filter(file => /^[\da-f-]{36}\.json$/.test(file))
    .map(file => read(file.slice(0, -5)))
    .filter(job => job && (job.status === 'queued' || (job.status === 'building' && job.expires <= now())))
    .sort((a, b) => a.created - b.created);
  const publicJob = job => ({ id: job.id, status: job.status, sha256: job.sha256, size: job.size });

  return async (req, res) => {
    const { method, url } = req;
    if (!env.YAP_ACCESS_TOKEN || env.YAP_ACCESS_TOKEN.length < 32 ||
        !env.YAP_WORKER_TOKEN || env.YAP_WORKER_TOKEN.length < 32 ||
        env.YAP_ACCESS_TOKEN === env.YAP_WORKER_TOKEN) {
      return reply(res, 503, { error: 'Build APK belum dikonfigurasi.' });
    }
    if (method === 'GET' && url === '/api/build/signal') {
      return reply(res, 200, { pending: pending().length > 0 });
    }
    const worker = url === '/api/build/claim' || /^\/api\/build\/[\da-f-]{36}\/(result|fail)$/.test(url);
    if (!matches(req.headers.authorization, worker ? env.YAP_WORKER_TOKEN : env.YAP_ACCESS_TOKEN)) {
      return reply(res, 401, { error: 'Token tidak valid.' });
    }
    if (method === 'POST' && url === '/api/build') {
      const body = await readBody(req, 32_768);
      let data;
      try { data = body && JSON.parse(body.toString()); } catch {}
      const { id, prompt } = data || {};
      if (typeof prompt !== 'string' || !prompt.trim() || prompt.length > 6000) {
        return reply(res, 400, { error: 'Prompt wajib diisi, maksimal 6000 karakter.' });
      }
      if (typeof id !== 'string' || !/^[\da-f-]{36}$/.test(id)) {
        return reply(res, 400, { error: 'ID build tidak valid.' });
      }
      const existing = read(id);
      if (existing) {
        if (existing.prompt !== prompt.trim()) return reply(res, 409, { error: 'ID sudah dipakai.' });
        return reply(res, 200, publicJob(existing));
      }
      const job = { id, prompt: prompt.trim(), status: 'queued', created: now() };
      write(job);
      return reply(res, 202, publicJob(job));
    }
    if (method === 'POST' && url === '/api/build/claim') {
      const job = pending()[0];
      if (!job) return res.writeHead(204).end();
      Object.assign(job, { status: 'building', lease: randomUUID(), expires: now() + leaseTime });
      write(job);
      return reply(res, 200, { id: job.id, prompt: job.prompt, lease: job.lease });
    }
    const route = /^\/api\/build\/([\da-f-]{36})(?:\/(apk|result|fail))?$/.exec(url);
    const job = route && read(route[1]);
    if (!job) return reply(res, 404, { error: 'Tugas tidak ditemukan.' });
    if (method === 'GET' && !route[2]) return reply(res, 200, publicJob(job));
    if (method === 'GET' && route[2] === 'apk' && job.status === 'ready') {
      const path = apkPath(job.id);
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="yap-app.apk"',
        'Content-Length': statSync(path).size,
        'Cache-Control': 'no-store'
      });
      await pipeline(createReadStream(path), res);
      return;
    }
    if (method === 'PUT' && worker) {
      const owns = current => current.status === 'building' && current.expires > now()
        && current.lease === req.headers['x-yap-lease'];
      if (!owns(job)) return reply(res, 409, { error: 'Klaim tugas tidak aktif.' });
      if (route[2] === 'fail') {
        job.status = 'failed';
      } else {
        const apk = await readBody(req, maxApk);
        if (!apk) return reply(res, 413, { error: 'APK terlalu besar.' });
        if (!apk.subarray(0, 4).equals(Buffer.from([80, 75, 3, 4])) ||
            !apk.includes(Buffer.from('AndroidManifest.xml')) || !apk.includes(Buffer.from('classes.dex'))) {
          return reply(res, 400, { error: 'Berkas tidak menyerupai APK Android.' });
        }
        if (!owns(read(job.id))) return reply(res, 409, { error: 'Klaim tugas tidak aktif.' });
        writeFileSync(apkPath(job.id) + '.tmp', apk, { mode: 0o600 });
        renameSync(apkPath(job.id) + '.tmp', apkPath(job.id));
        Object.assign(job, { status: 'ready', sha256: digest(apk).toString('hex'), size: apk.length });
      }
      delete job.lease;
      delete job.expires;
      write(job);
      return reply(res, 200, publicJob(job));
    }
    return reply(res, 404, { error: 'Endpoint tidak ditemukan.' });
  };
}
