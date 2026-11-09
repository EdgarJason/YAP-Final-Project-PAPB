import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import { mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync, statSync, createReadStream } from 'node:fs';
import { resolve, join } from 'node:path';
import { pipeline } from 'node:stream/promises';
import { unzipSync, strFromU8 } from 'fflate';

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
  const publicJob = job => ({
    id: job.id, status: job.status, sha256: job.sha256, size: job.size,
    clarification: job.clarifications?.at(-1), clarifications: job.clarifications || [], message: job.message,
    appName: job.appName
  });

  return async (req, res) => {
    const { method, url } = req;
    if (!env.YAP_ACCESS_TOKEN || env.YAP_ACCESS_TOKEN.length < 32 ||
        !env.YAP_WORKER_TOKEN || env.YAP_WORKER_TOKEN.length < 32 ||
        env.YAP_ACCESS_TOKEN === env.YAP_WORKER_TOKEN) {
      return reply(res, 503, { error: 'Build APK belum dikonfigurasi.' });
    }
    if (method === 'GET' && url === '/api/build/signal') {
      const job = pending()[0];
      if (!job) return reply(res, 200, { pending: false });
      const wakeId = digest(JSON.stringify([job.id, job.clarifications?.length || 0, job.lease])).toString('hex');
      return reply(res, 200, { pending: true, wakeId });
    }
    const worker = url === '/api/build/claim' || /^\/api\/build\/[\da-f-]{36}\/(result|fail|question|message|metadata)$/.test(url);
    const guestToken = env.YAP_PUBLIC_BUILDS === '1' && !worker
      && /^Bearer guest_[a-f0-9]{64}$/.test(req.headers.authorization || '')
      ? req.headers.authorization : null;
    const owner = guestToken ? digest(guestToken).toString('hex') : null;
    if (!owner && !matches(req.headers.authorization, worker ? env.YAP_WORKER_TOKEN : env.YAP_ACCESS_TOKEN)) {
      if (method === 'POST' && url === '/api/build/claim' && env.YAP_AUTH_DIAGNOSTICS === '1') {
        const authorization = req.headers.authorization || '';
        const kind = !authorization ? 'missing'
          : authorization.startsWith('Bearer hsurr:') ? 'unresolved-surrogate'
          : authorization.startsWith('Bearer ') ? 'invalid-bearer'
          : 'unexpected-scheme';
        console.info(`[YAP] worker authentication rejected: ${kind}`);
      }
      return reply(res, 401, { error: 'Token tidak valid.' });
    }
    if (method === 'POST' && url === '/api/build') {
      const body = await readBody(req, 131_072);
      let data;
      try { data = body && JSON.parse(body.toString()); } catch {}
      const { id, prompt, context = '' } = data || {};
      if (typeof context !== 'string' || context.length > 12_000) {
        return reply(res, 400, { error: 'Konteks percakapan terlalu panjang.' });
      }
      if (typeof prompt !== 'string' || !prompt.trim() || prompt.length > 6000) {
        return reply(res, 400, { error: 'Prompt wajib diisi, maksimal 6000 karakter.' });
      }
      if (typeof id !== 'string' || !/^[\da-f-]{36}$/.test(id)) {
        return reply(res, 400, { error: 'ID build tidak valid.' });
      }
      const existing = read(id);
      if (existing) {
        if (owner && existing.owner !== owner) return reply(res, 404, { error: 'Tugas tidak ditemukan.' });
        if (existing.prompt !== prompt.trim()) return reply(res, 409, { error: 'ID sudah dipakai.' });
        return reply(res, 200, publicJob(existing));
      }
      const job = { id, prompt: prompt.trim(), context, status: 'queued', created: now(), ...(owner && { owner }) };
      write(job);
      return reply(res, 202, publicJob(job));
    }
    if (method === 'POST' && url === '/api/build/claim') {
      const job = pending()[0];
      if (!job) return res.writeHead(204).end();
      Object.assign(job, { status: 'building', lease: randomUUID(), expires: now() + leaseTime });
      write(job);
      return reply(res, 200, { id: job.id, prompt: job.prompt, context: job.context || '', lease: job.lease, clarifications: job.clarifications || [] });
    }
    const route = /^\/api\/build\/([\da-f-]{36})(?:\/(apk|result|fail|question|answer|message|preview|metadata))?$/.exec(url);
    const job = route && read(route[1]);
    if (!job) return reply(res, 404, { error: 'Tugas tidak ditemukan.' });
    if (owner && job.owner !== owner) return reply(res, 404, { error: 'Tugas tidak ditemukan.' });
    if (method === 'GET' && !route[2]) return reply(res, 200, publicJob(job));
    if (method === 'GET' && route[2] === 'preview' && job.status === 'ready') {
      try {
        const files = unzipSync(readFileSync(apkPath(job.id)), {
          filter: entry => entry.name === 'assets/index.html' && entry.originalSize <= 2 * 1024 * 1024
        });
        const html = files['assets/index.html'];
        if (html) return reply(res, 200, { html: strFromU8(html) });
      } catch {}
      return reply(res, 404, { error: 'Pratinjau web tidak tersedia untuk APK ini.' });
    }
    if (method === 'POST' && route[2] === 'answer') {
      const body = await readBody(req, 16_384);
      let data;
      try { data = body && JSON.parse(body.toString()); } catch {}
      const { questionId, answer } = data || {};
      if (typeof answer !== 'string' || !answer.trim() || answer.length > 1200) {
        return reply(res, 400, { error: 'Jawaban wajib diisi, maksimal 1200 karakter.' });
      }
      const current = read(job.id);
      const question = current.clarifications?.find(entry => entry.id === questionId);
      if (question?.answer === answer.trim()) return reply(res, 200, publicJob(current));
      if (!question || question.answer || current.status !== 'awaiting_answer') {
        return reply(res, 409, { error: 'Pertanyaan ini sudah tidak aktif.' });
      }
      question.answer = answer.trim();
      current.status = 'queued';
      write(current);
      return reply(res, 200, publicJob(current));
    }
    if (method === 'GET' && route[2] === 'apk' && job.status === 'ready') {
      const path = apkPath(job.id);
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': "attachment; filename*=UTF-8''" + encodeURIComponent((job.appName || 'Aplikasi-' + job.id.slice(0, 8)) + '.apk'),
        'Content-Length': statSync(path).size,
        'Cache-Control': 'no-store'
      });
      await pipeline(createReadStream(path), res);
      return;
    }
    if (method === 'PUT' && worker) {
      const owns = current => current?.status === 'building' && current.expires > now()
        && current.lease === req.headers['x-yap-lease'];
      if (!owns(job)) return reply(res, 409, { error: 'Klaim tugas tidak aktif.' });
      if (route[2] === 'metadata') {
        const body = await readBody(req, 2048);
        let appName;
        try { appName = body && JSON.parse(body.toString()).appName; } catch {}
        if (typeof appName !== 'string' || !appName.trim() || appName.length > 80 || /[\u0000-\u001f\u007f]/.test(appName)) {
          return reply(res, 400, { error: 'Nama aplikasi wajib diisi, maksimal 80 karakter.' });
        }
        const current = read(job.id);
        if (!owns(current)) return reply(res, 409, { error: 'Klaim tugas tidak aktif.' });
        current.appName = appName.trim();
        write(current);
        return reply(res, 200, publicJob(current));
      }
      if (route[2] === 'message') {
        const body = await readBody(req, 40_960);
        let message;
        try { message = body && JSON.parse(body.toString()).message; } catch {}
        if (typeof message !== 'string' || !message.trim() || message.length > 6000) {
          return reply(res, 400, { error: 'Balasan wajib diisi, maksimal 6000 karakter.' });
        }
        const current = read(job.id);
        if (!owns(current)) return reply(res, 409, { error: 'Klaim tugas tidak aktif.' });
        Object.assign(current, { status: 'responded', message: message.trim() });
        delete current.lease;
        delete current.expires;
        write(current);
        return reply(res, 200, publicJob(current));
      }
      if (route[2] === 'question') {
        const body = await readBody(req, 8192);
        let question;
        try { question = body && JSON.parse(body.toString()).question; } catch {}
        if (typeof question !== 'string' || !question.trim() || question.length > 600) {
          return reply(res, 400, { error: 'Pertanyaan wajib diisi, maksimal 600 karakter.' });
        }
        const current = read(job.id);
        if (!owns(current)) return reply(res, 409, { error: 'Klaim tugas tidak aktif.' });
        current.clarifications ||= [];
        if (current.clarifications.length >= 5) {
          return reply(res, 409, { error: 'Batas klarifikasi tercapai. Lanjutkan build.' });
        }
        current.clarifications.push({ id: randomUUID(), question: question.trim() });
        current.status = 'awaiting_answer';
        delete current.lease;
        delete current.expires;
        write(current);
        return reply(res, 200, publicJob(current));
      }
      if (route[2] === 'fail') {
        job.status = 'failed';
      } else {
        const apk = await readBody(req, maxApk);
        if (!apk) return reply(res, 413, { error: 'APK terlalu besar.' });
        if (!apk.subarray(0, 4).equals(Buffer.from([80, 75, 3, 4])) ||
            !apk.includes(Buffer.from('AndroidManifest.xml')) || !apk.includes(Buffer.from('classes.dex'))) {
          return reply(res, 400, { error: 'Berkas tidak menyerupai APK Android.' });
        }
        const current = read(job.id);
        if (!owns(current)) return reply(res, 409, { error: 'Klaim tugas tidak aktif.' });
        Object.assign(job, current);
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
