// Framework-agnostic v2 build API router.
// Used by the thin Vercel handlers in server/api/ and by local.mjs when
// YAP_STORAGE_BACKEND=supabase. The legacy disk handler (build-jobs.mjs)
// stays untouched for the filesystem backend; this router is the v2 path.
// Dispatch input: {method, path, headers, body, rawBody, contentType}.
// Output: {status, body} or {status:204} or {status:200, binaryFile:{...}}.
import { randomUUID, createHash, timingSafeEqual } from 'node:crypto';
import { existsSync, statSync } from 'node:fs';
import { join } from 'node:path';
import {
  LIMITS, ApiError, badRequest, unauthorized, notFound, unprocessable,
  validateSubmit, validateAnswer, validateQuestion, validateMessage, validateAppName,
  validateArtifactDescriptor, validateResult, validateFail, requireUuid,
  canonicalRequestHash, ownerHashFromGuestToken, wakeIdFor, publicJob, checkGatewayConfig,
  ADMIN_OWNER_HASH,
} from './build-service.mjs';
const UUID_SEGMENT = '[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}';

export function createBuildApi({ repository, env = process.env, now = Date.now } = {}) {
  const signalSecret = env.YAP_SIGNAL_SECRET || '';
  const matches = (actual, token) => {
    const a = createHash('sha256').update(actual || '').digest();
    const b = createHash('sha256').update(`Bearer ${token}`).digest();
    return timingSafeEqual(a, b);
  };
  const auth = headers => {
    const authorization = headers.authorization || headers.Authorization || '';
    return { authorization };
  };
  const requireWorker = headers => {
    const { accessToken, workerToken } = checkGatewayConfig(env);
    if (!matches(auth(headers).authorization, workerToken)) throw unauthorized();
    return { accessToken, workerToken };
  };
  const requireUser = headers => {
    const { accessToken } = checkGatewayConfig(env);
    const { authorization } = auth(headers);
    if (env.YAP_PUBLIC_BUILDS === '1') {
      const ownerHash = ownerHashFromGuestToken(authorization);
      if (ownerHash) return { ownerHash, admin: false };
    }
    if (matches(authorization, accessToken)) return { ownerHash: ADMIN_OWNER_HASH, admin: true };
    throw unauthorized();
  };
  const loadOwnedJob = async (id, ownerHash, admin) => {
    const job = await repository.get(id);
    if (!job) throw notFound();
    // Guests must match the job owner exactly; an empty owner on either
    // side never grants access (a job with ownerHash null is unreadable
    // by guests, and a guest without a token never reaches here).
    if (!admin && (!ownerHash || !job.ownerHash || job.ownerHash !== ownerHash)) throw notFound();
    return job;
  };
  const leaseHeader = headers => headers['x-yap-lease'] || headers['X-YAP-Lease'] || '';
  const operationId = body => (body?.operationId && typeof body.operationId === 'string' ? body.operationId : randomUUID());

  async function dispatch(input) {
    const method = input.method;
    const rawPath = (input.path || '').split('?')[0];
    const headers = input.headers || {};
    const body = input.body;

    if (method === 'GET' && rawPath === '/api/capabilities') {
      return {
        status: 200,
        body: {
          protocolVersion: LIMITS.protocolVersion,
          artifactTransfer: 'signed-storage',
          preview: 'inline-json',
          maxApkBytes: LIMITS.maxApkBytes,
        },
      };
    }

    if (method === 'GET' && rawPath === '/api/build/signal') {
      checkGatewayConfig(env);
      const signal = await repository.signal();
      if (!signal.pending) return { status: 200, body: { pending: false } };
      return {
        status: 200,
        body: {
          pending: true,
          wakeId: wakeIdFor({ jobId: signal.jobId, queueRevision: signal.queueRevision, lease: signal.lease, now: now() }, signalSecret),
        },
      };
    }

    if (method === 'POST' && rawPath === '/api/build') {
      const { ownerHash, admin } = requireUser(headers);
      const data = validateSubmit(body);
      const requestHash = canonicalRequestHash({ id: data.id, ownerHash, ...data });
      const { job, created } = await repository.create({ ...data, ownerHash, requestHash });
      return { status: created ? 202 : 200, body: publicJob(job) };
    }

    if ((method === 'POST' && rawPath === '/api/build/claim') || (method === 'POST' && rawPath === '/api/worker/resume')) {
      requireWorker(headers);
      const claimRequestId = body?.claimRequestId && typeof body.claimRequestId === 'string'
        ? requireUuid(body.claimRequestId, 'ID permintaan klaim')
        : randomUUID();
      const { job, replayed } = await repository.claim({ claimRequestId });
      if (!job) {
        // A replayed claim whose lease is dead (superseded, expired, or
        // terminal) is NOT an empty queue: say so explicitly so the
        // worker starts a fresh claim instead of waiting forever.
        if (replayed) return { status: 200, body: { replayed: true, job: null, claimRequestId } };
        return { status: 204, body: null };
      }
      return {
        status: 200,
        body: {
          id: job.id, prompt: job.prompt, context: job.context || '',
          responseMode: job.responseMode || 'apk', lease: job.lease,
          leaseExpiresAt: job.leaseExpiresAt ? new Date(job.leaseExpiresAt).toISOString() : null,
          clarifications: job.clarifications || [], protocolVersion: LIMITS.protocolVersion,
          claimRequestId, ...(replayed ? { replayed: true } : {}),
        },
      };
    }

    const match = new RegExp(`^/api/build/(${UUID_SEGMENT})(?:/(apk|result|fail|question|answer|message|preview|metadata|renew|download))?$`).exec(rawPath);
    const prepareMatch = new RegExp(`^/api/build/(${UUID_SEGMENT})/artifacts/prepare$`).exec(rawPath);
    if (!match && !prepareMatch) {
      throw new ApiError(404, 'Endpoint tidak ditemukan.');
    }
    const id = (match || prepareMatch)[1];
    const action = match ? match[2] : 'prepare';

    if (method === 'GET' && !action) {
      const { ownerHash, admin } = requireUser(headers);
      const job = await loadOwnedJob(id, ownerHash, admin);
      return { status: 200, body: publicJob(job) };
    }

    if (method === 'POST' && action === 'answer') {
      const { ownerHash, admin } = requireUser(headers);
      const data = validateAnswer(body);
      const { job } = await repository.answer({ jobId: id, ownerHash, ...data });
      return { status: 200, body: publicJob(job) };
    }

    if (method === 'GET' && action === 'download') {
      const { ownerHash, admin } = requireUser(headers);
      const job = await loadOwnedJob(id, ownerHash, admin);
      if (job.status !== 'ready') throw notFound('Tugas belum siap.');
      const signed = await repository.createSignedDownloadUrl({ jobId: id, ownerHash });
      if (!signed) {
        // Filesystem backend: no signed Storage URL — use the legacy /apk route.
        throw new ApiError(501, 'Download sementara tidak tersedia pada backend filesystem; gunakan /apk.');
      }
      return { status: 200, body: signed };
    }

    if (method === 'GET' && action === 'preview') {
      const { ownerHash, admin } = requireUser(headers);
      await loadOwnedJob(id, ownerHash, admin);
      const preview = await repository.getPreview(id);
      if (!preview) throw notFound('Pratinjau web tidak tersedia untuk APK ini.');
      return { status: 200, body: preview };
    }

    if (method === 'GET' && action === 'apk') {
      // Legacy binary download. Cloud backend rejects it with 426 (§8);
      // filesystem backend still serves the file (rollback compatibility).
      const { ownerHash, admin } = requireUser(headers);
      const job = await loadOwnedJob(id, ownerHash, admin);
      if (job.status !== 'ready') throw notFound('Tugas belum siap.');
      if (repository.kind !== 'filesystem') {
        throw unprocessable('Klien perlu diperbarui: gunakan /download untuk URL sementara.');
      }
      const path = join(repository.root, `${id}.apk`);
      if (!existsSync(path)) throw notFound('APK tidak ditemukan.');
      return {
        status: 200,
        binaryFile: {
          path, size: statSync(path).size, fileName: `${job.appName || `Aplikasi-${id.slice(0, 8)}`}.apk`,
          contentType: 'application/vnd.android.package-archive',
        },
      };
    }

    // ---- Worker mutations below: Bearer worker token + active lease. ----
    if (method === 'PUT' && action === 'renew') {
      requireWorker(headers);
      const lease = leaseHeader(headers);
      const { leaseExpiresAt } = await repository.renew({ jobId: id, lease });
      return { status: 200, body: { id, lease, leaseExpiresAt: new Date(leaseExpiresAt).toISOString() } };
    }

    if (method === 'PUT' && action === 'question') {
      requireWorker(headers);
      const data = validateQuestion(body);
      const { job } = await repository.askQuestion({ jobId: id, lease: leaseHeader(headers), operationId: operationId(body), ...data });
      return { status: 200, body: publicJob(job) };
    }

    if (method === 'PUT' && action === 'message') {
      requireWorker(headers);
      const data = validateMessage(body);
      const { job } = await repository.sendMessage({ jobId: id, lease: leaseHeader(headers), operationId: operationId(body), ...data });
      return { status: 200, body: publicJob(job) };
    }

    if (method === 'PUT' && action === 'metadata') {
      requireWorker(headers);
      const data = validateAppName(body);
      const { job } = await repository.setMetadata({ jobId: id, lease: leaseHeader(headers), operationId: operationId(body), ...data });
      return { status: 200, body: publicJob(job) };
    }

    if (method === 'PUT' && action === 'fail') {
      requireWorker(headers);
      const { errorCode } = validateFail(body);
      const { job } = await repository.fail({ jobId: id, lease: leaseHeader(headers), operationId: operationId(body), errorCode });
      return { status: 200, body: publicJob(job) };
    }

    if (method === 'POST' && action === 'prepare') {
      requireWorker(headers);
      if (!body || typeof body !== 'object') throw badRequest('Body harus berupa JSON object.');
      const descriptor = validateArtifactDescriptor(body);
      const opId = operationId(body);
      const { artifact } = await repository.prepareArtifact({
        jobId: id, lease: leaseHeader(headers), operationId: opId, ...descriptor,
      });
      return {
        status: 200,
        body: {
          artifactId: artifact.id, bucket: artifact.bucket || 'yap-artifacts',
          objectPath: artifact.objectPath, uploadUrl: artifact.uploadUrl || null,
          uploadToken: artifact.uploadToken || null, method: artifact.method || 'standard',
          expiresAt: artifact.expiresAt || null, operationId: opId,
        },
      };
    }

    if (method === 'PUT' && action === 'result') {
      requireWorker(headers);
      const contentType = String(input.contentType || headers['content-type'] || headers['Content-Type'] || '');
      if (contentType.includes('application/vnd.android.package-archive') || Buffer.isBuffer(input.rawBody)) {
        // v2 result is JSON only; legacy binary upload is rejected loudly (§9).
        throw unprocessable('Hasil v2 memakai JSON (prepare + finalize). Biner legacy ditolak; perbarui helper worker.');
      }
      const data = validateResult(body);
      const { job } = await repository.finalize({
        jobId: id, lease: leaseHeader(headers), ...data,
      });
      return { status: 200, body: publicJob(job) };
    }

    throw new ApiError(404, 'Endpoint tidak ditemukan.');
  }

  return {
    dispatch: async input => {
      try {
        return await dispatch(input);
      } catch (error) {
        if (error instanceof ApiError) return { status: error.status, body: { error: error.message, ...(error.code ? { code: error.code } : {}) } };
        return { status: 500, body: { error: 'Server tidak dapat memproses request.' } };
      }
    },
  };
}
