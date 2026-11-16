// Shared validation and contract for the YAP build gateway (v1 disk + v2 cloud).
// Pure functions only: no I/O, no network, no secrets. Both backends
// (filesystem in build-jobs.mjs / lib/build-repository-filesystem.mjs and
// Supabase in lib/build-repository-supabase.mjs) must enforce these limits.
// Source of truth for the limits: server/MUSE-APK.md and
// RANCANGAN-VERCEL-SUPABASE.md §6-§9.
import { createHash, createHmac, randomUUID } from 'node:crypto';

export const LIMITS = {
  protocolVersion: 2,
  maxApkBytes: 32 * 1024 * 1024, // 33_554_432
  maxPromptChars: 6000,
  maxContextChars: 12000,
  maxQuestionChars: 600,
  maxAnswerChars: 1200,
  maxMessageChars: 6000,
  maxAppNameChars: 80,
  maxClarifications: 5,
  leaseMs: 10 * 60_000, // 10 minutes
  leaseRenewEveryMs: 2 * 60_000, // worker heartbeat cadence (design §7)
  maxAttemptMs: 30 * 60_000,
  maxAttempts: 3,
  maxPreviewHtmlBytes: 2 * 1024 * 1024, // 2 MiB UTF-8
  maxPreviewEncodedBytes: 3 * 1024 * 1024, // 3 MiB after JSON escaping
  maxSubmitBodyBytes: 128 * 1024, // 128 KiB before parsing (§6)
  maxResultBodyBytes: 3 * 1024 * 1024, // finalize JSON, incl. preview
  maxSourceBytes: 8 * 1024 * 1024, // source snapshot, design §10
  signedUploadTtlSeconds: 2 * 60 * 60, // Supabase signed upload URL: 2h
  signedDownloadTtlSeconds: 300, // download URL TTL, design §8
};

export const STATUSES = ['queued', 'building', 'awaiting_answer', 'responded', 'ready', 'failed'];
export const RESPONSE_MODES = ['apk', 'chat'];

export class ApiError extends Error {
  constructor(status, message, code = undefined) {
    super(message);
    this.status = status;
    if (code) this.code = code;
  }
}

export const badRequest = message => new ApiError(400, message);
export const unauthorized = (message = 'Token tidak valid.') => new ApiError(401, message);
export const notFound = (message = 'Tugas tidak ditemukan.') => new ApiError(404, message);
export const conflict = (message, code = undefined) => new ApiError(409, message, code);
export const unprocessable = (message, code = undefined) => new ApiError(426, message, code);

const UUID_RE = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
const SHA256_RE = /^[a-f0-9]{64}$/;
const CONTROL_RE = /[\u0000-\u001f\u007f]/;

export function isUuid(value) {
  return typeof value === 'string' && UUID_RE.test(value);
}

export function requireUuid(value, label = 'ID') {
  if (!isUuid(value)) throw badRequest(`${label} tidak valid.`);
  return value;
}

export function validateString(value, { label, required = true, maxChars, maxBytes, allowEmpty = false }) {
  if (value === undefined || value === null) {
    if (required) throw badRequest(`${label} wajib diisi.`);
    return '';
  }
  if (typeof value !== 'string') throw badRequest(`${label} harus berupa teks.`);
  const trimmed = value.trim();
  if (required && !allowEmpty && !trimmed) throw badRequest(`${label} wajib diisi.`);
  if (maxChars !== undefined && value.length > maxChars) {
    throw badRequest(`${label} maksimal ${maxChars} karakter.`);
  }
  if (maxBytes !== undefined && Buffer.byteLength(value, 'utf8') > maxBytes) {
    throw badRequest(`${label} melebihi batas ukuran.`);
  }
  return trimmed;
}

export function validateSubmit(data) {
  if (!data || typeof data !== 'object') throw badRequest('Body harus berupa JSON object.');
  const id = requireUuid(data.id, 'ID build');
  const prompt = validateString(data.prompt, { label: 'Prompt', maxChars: LIMITS.maxPromptChars });
  const context = validateString(data.context ?? '', {
    label: 'Konteks percakapan', required: false, maxChars: LIMITS.maxContextChars, allowEmpty: true,
  });
  const responseMode = data.responseMode ?? 'apk';
  if (!RESPONSE_MODES.includes(responseMode)) throw badRequest('Mode respons tidak valid.');
  return { id, prompt, context, responseMode };
}

export function validateAnswer(data) {
  if (!data || typeof data !== 'object') throw badRequest('Body harus berupa JSON object.');
  const questionId = requireUuid(data.questionId, 'ID pertanyaan');
  const answer = validateString(data.answer, { label: 'Jawaban', maxChars: LIMITS.maxAnswerChars });
  return { questionId, answer };
}

export function validateQuestion(data) {
  if (!data || typeof data !== 'object') throw badRequest('Body harus berupa JSON object.');
  const question = validateString(data.question, { label: 'Pertanyaan', maxChars: LIMITS.maxQuestionChars });
  return { question };
}

export function validateMessage(data) {
  if (!data || typeof data !== 'object') throw badRequest('Body harus berupa JSON object.');
  const message = validateString(data.message, { label: 'Balasan', maxChars: LIMITS.maxMessageChars });
  return { message };
}

export function validateAppName(data) {
  if (!data || typeof data !== 'object') throw badRequest('Body harus berupa JSON object.');
  const appName = validateString(data.appName, { label: 'Nama aplikasi', maxChars: LIMITS.maxAppNameChars });
  if (CONTROL_RE.test(appName)) throw badRequest('Nama aplikasi mengandung karakter kontrol.');
  return { appName };
}

export function validatePreviewHtml(html) {
  if (html === undefined || html === null) return null;
  if (typeof html !== 'string') throw badRequest('Preview HTML harus berupa teks.');
  if (Buffer.byteLength(html, 'utf8') > LIMITS.maxPreviewHtmlBytes) {
    throw badRequest('Preview HTML melebihi 2 MiB.');
  }
  const encoded = Buffer.byteLength(JSON.stringify({ html }), 'utf8');
  if (encoded > LIMITS.maxPreviewEncodedBytes) {
    throw badRequest('Preview JSON setelah escaping melebihi 3 MiB. Perkecil preview.');
  }
  return html;
}

export function validateArtifactDescriptor({ kind, size, sha256 }) {
  if (!['apk', 'source', 'verification'].includes(kind)) throw badRequest('Jenis artifact tidak valid.');
  if (!Number.isInteger(size) || size <= 0) throw badRequest('Ukuran artifact tidak valid.');
  if (kind === 'apk' && size > LIMITS.maxApkBytes) {
    throw new ApiError(413, 'APK terlalu besar.');
  }
  if (kind === 'source' && size > LIMITS.maxSourceBytes) {
    throw new ApiError(413, 'Snapshot sumber terlalu besar.');
  }
  if (typeof sha256 !== 'string' || !SHA256_RE.test(sha256)) {
    throw badRequest('SHA-256 artifact tidak valid.');
  }
  return { kind, size, sha256 };
}

export function validateResult(data) {
  if (!data || typeof data !== 'object') throw badRequest('Body harus berupa JSON object.');
  const operationId = requireUuid(data.operationId, 'ID operasi');
  const artifactId = requireUuid(data.artifactId, 'ID artifact');
  const previewHtml = validatePreviewHtml(data.previewHtml ?? null);
  const verification = data.verification ?? null;
  if (verification !== null && typeof verification !== 'object') {
    throw badRequest('Laporan verifikasi tidak valid.');
  }
  return { operationId, artifactId, previewHtml, verification };
}

export function validateFail(data) {
  // Body is optional; when present it may carry a safe error code only.
  if (data === undefined || data === null) return { errorCode: 'build_failed' };
  if (typeof data !== 'object') throw badRequest('Body harus berupa JSON object.');
  const errorCode = typeof data.errorCode === 'string' && /^[a-z0-9_]{1,64}$/.test(data.errorCode)
    ? data.errorCode
    : 'build_failed';
  return { errorCode };
}

/** Canonical request hash for idempotent submit retries (§6). */
export function canonicalRequestHash({ id, ownerHash, prompt, context, responseMode }) {
  return createHash('sha256')
    .update(JSON.stringify([id, ownerHash || '', prompt, context || '', responseMode || 'apk']))
    .digest('hex');
}

export function ownerHashFromGuestToken(authorization) {
  const match = /^Bearer (guest_[a-f0-9]{64})$/.exec(authorization || '');
  if (!match) return null;
  return createHash('sha256').update(`Bearer ${match[1]}`).digest('hex');
}

export function sha256Hex(buffer) {
  return createHash('sha256').update(buffer).digest('hex');
}

/**
 * Opaque wake id for the public signal. When a server secret is configured
 * (YAP_SIGNAL_SECRET) this is an HMAC over the candidate identity, queue
 * revision, last lease and a 2-minute retry window (§9); without a secret it
 * falls back to the legacy SHA-256 derivation so local/dev behaviour is
 * unchanged. Never expose the job id, prompt or owner in the signal.
 */
export function wakeIdFor({ jobId, queueRevision = 0, lease = '', now = Date.now() }, secret = '') {
  const window = Math.floor(now / 120_000);
  const payload = JSON.stringify([jobId, queueRevision, lease || '', window]);
  if (secret) return createHmac('sha256', secret).update(payload).digest('hex');
  return createHash('sha256').update(payload).digest('hex');
}

export function newLease(now = Date.now()) {
  return { lease: randomUUID(), expiresAt: now + LIMITS.leaseMs };
}

export function isLeaseActive(job, now = Date.now()) {
  return Boolean(job?.lease && job.leaseExpiresAt && job.leaseExpiresAt > now);
}

/** Public job view — the shape legacy clients already consume (§8). */
export function publicJob(job) {
  if (!job) return null;
  const clarifications = (job.clarifications || []).map(entry => ({
    id: entry.id,
    question: entry.question,
    ...(entry.answer !== undefined && entry.answer !== null ? { answer: entry.answer } : {}),
  }));
  return {
    id: job.id,
    status: job.status,
    sha256: job.sha256 ?? null,
    size: job.size ?? null,
    clarification: clarifications.at(-1) ?? null,
    clarifications,
    message: job.message ?? null,
    appName: job.appName ?? null,
    ...(job.updatedAt ? { updatedAt: job.updatedAt } : {}),
    ...(job.resultExpiresAt ? { resultExpiresAt: job.resultExpiresAt } : {}),
    ...(job.errorCode ? { errorCode: job.errorCode } : {}),
    ...(job.status === 'ready' ? { artifactAvailable: true } : {}),
  };
}

export function checkGatewayConfig(env = process.env) {
  const access = env.YAP_ACCESS_TOKEN || '';
  const worker = env.YAP_WORKER_TOKEN || '';
  if (!access || access.length < 32 || !worker || worker.length < 32 || access === worker) {
    throw new ApiError(503, 'Build APK belum dikonfigurasi.');
  }
  return { accessToken: access, workerToken: worker };
}
