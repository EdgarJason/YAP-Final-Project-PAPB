// Adapter between Node/Vercel HTTP requests and the v2 build API router.
// Selects the storage backend from YAP_STORAGE_BACKEND:
//   supabase   -> Supabase Postgres + Storage (cloud path)
//   filesystem -> local disk queue (rollback / local dev, default locally)
// On Vercel, an unset backend is a configuration error (503), never a
// silent fallback to the ephemeral disk — the disk would lose the queue.
import { createReadStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';
import { LIMITS, ApiError } from './build-service.mjs';
import { createBuildApi } from './build-api.mjs';
import { createFilesystemRepository } from './build-repository-filesystem.mjs';
import { createSupabaseRepository } from './build-repository-supabase.mjs';

export function createRepository({ env = process.env } = {}) {
  const backend = (env.YAP_STORAGE_BACKEND || '').toLowerCase();
  if (backend === 'supabase') {
    return createSupabaseRepository({
      supabaseUrl: env.SUPABASE_URL,
      serviceRoleKey: env.SUPABASE_SECRET_KEY || env.SUPABASE_SERVICE_ROLE_KEY,
    });
  }
  if (backend === 'filesystem' || backend === '') {
    if (env.VERCEL && backend === '') {
      throw new ApiError(503, 'Backend supabase belum dikonfigurasi (YAP_STORAGE_BACKEND=supabase).');
    }
    return createFilesystemRepository({ directory: env.YAP_BUILD_DIR || '.build-jobs' });
  }
  throw new ApiError(503, `YAP_STORAGE_BACKEND tidak dikenal: ${backend}.`);
}

export function createBuildApiForEnv(env = process.env) {
  return createBuildApi({ repository: createRepository({ env }), env });
}

function bodyLimitFor(path, method) {
  if (method === 'PUT' && path.endsWith('/result')) return LIMITS.maxResultBodyBytes;
  if (method === 'POST' && path === '/api/build') return LIMITS.maxSubmitBodyBytes;
  return 64 * 1024;
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

function replyJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(body === null || body === undefined ? '' : JSON.stringify(body));
}

/** Build a Vercel/Node handler from a createBuildApi instance (lazy repo). */
export function createVercelHandler({ env = process.env } = {}) {
  let apiPromise = null;
  const api = () => {
    if (!apiPromise) {
      apiPromise = Promise.resolve().then(() => createBuildApiForEnv(env));
    }
    return apiPromise;
  };
  return async (req, res) => {
    const url = new URL(req.url || '/', 'http://localhost');
    const path = url.pathname;
    const headers = {};
    for (const [key, value] of Object.entries(req.headers || {})) {
      headers[key.toLowerCase()] = Array.isArray(value) ? value[0] : value;
    }
    const contentType = String(headers['content-type'] || '');
    const isBinaryResult = methodIs(req, 'PUT') && path.endsWith('/result')
      && (contentType.includes('application/vnd.android.package-archive') || contentType.includes('application/octet-stream'));
    let body;
    let rawBody;
    try {
      if (req.body !== undefined) {
        const raw = typeof req.body === 'string' ? req.body : JSON.stringify(req.body);
        if (Buffer.byteLength(raw) > bodyLimitFor(path, req.method)) {
          return replyJson(res, 413, { error: 'Body terlalu besar.' });
        }
        body = typeof req.body === 'string' ? JSON.parse(raw) : req.body;
      } else if (isBinaryResult) {
        // v2 result is JSON only; do not buffer a legacy binary upload.
        rawBody = Buffer.alloc(0);
      } else if (req.method === 'POST' || req.method === 'PUT') {
        const raw = await readBody(req, bodyLimitFor(path, req.method));
        if (raw === null) return replyJson(res, 413, { error: 'Body terlalu besar.' });
        if (raw.length > 0) {
          try { body = JSON.parse(raw.toString('utf8')); } catch {
            return replyJson(res, 400, { error: 'Body JSON tidak valid.' });
          }
        }
      }
    } catch (error) {
      return replyJson(res, 400, { error: 'Body tidak dapat dibaca.' });
    }
    let result;
    try {
      // Capabilities is public and config-independent: answer it without
      // creating a repository (an unconfigured backend must not 503 it).
      if (req.method === 'GET' && path === '/api/capabilities') {
        return replyJson(res, 200, {
          protocolVersion: LIMITS.protocolVersion,
          artifactTransfer: 'signed-storage',
          preview: 'inline-json',
          maxApkBytes: LIMITS.maxApkBytes,
        });
      }
      result = await (await api()).dispatch({ method: req.method, path, headers, body, rawBody, contentType });
    } catch (error) {
      if (error instanceof ApiError) return replyJson(res, error.status, { error: error.message });
      return replyJson(res, 503, { error: error.message || 'Backend belum dikonfigurasi.' });
    }
    if (result.binaryFile) {
      const { path: filePath, size, fileName, contentType: type } = result.binaryFile;
      res.writeHead(200, {
        'Content-Type': type,
        'Content-Disposition': `attachment; filename*=UTF-8''${encodeURIComponent(fileName)}`,
        'Content-Length': size,
        'Cache-Control': 'no-store',
      });
      await pipeline(createReadStream(filePath), res);
      return;
    }
    if (result.status === 204 || result.body === null || result.body === undefined) {
      res.writeHead(result.status || 204, { 'Cache-Control': 'no-store' });
      return res.end();
    }
    return replyJson(res, result.status, result.body);
  };
}

function methodIs(req, method) {
  return String(req.method || '').toUpperCase() === method;
}
