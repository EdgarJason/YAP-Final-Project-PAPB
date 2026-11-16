// Filesystem repository for the YAP build gateway (v2 contract).
// This is the legacy disk backend from build-jobs.mjs, re-exposed through
// the v2 repository interface so contract tests and rollback run against the
// exact on-disk format the old handler uses: {id}.json + {id}.apk.
// Artifact reservations live in a sidecar {id}.artifacts.json, which the old
// handler ignores (its pending() filter only matches {uuid}.json).
// Single-process atomicity is provided by an in-process promise mutex;
// cross-process safety is NOT claimed (the gateway is one process).
import { randomUUID } from 'node:crypto';
import { mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync, existsSync } from 'node:fs';
import { resolve, join } from 'node:path';
import {
  LIMITS, ApiError, notFound, conflict, canonicalRequestHash, sha256Hex,
} from './build-service.mjs';
import { inspectApkBytes, extractPreviewHtml, artifactObjectPath } from './artifact-storage.mjs';

const UUID_FILE = /^[\da-f-]{36}\.json$/;

function leaseActive(job, now) {
  return Boolean(job?.lease && job.leaseExpiresAt && job.leaseExpiresAt > now);
}

export function createFilesystemRepository({ directory, now = Date.now } = {}) {
  const root = resolve(directory || '.build-jobs');
  mkdirSync(root, { recursive: true, mode: 0o700 });
  const jobPath = id => join(root, `${id}.json`);
  const apkPath = id => join(root, `${id}.apk`);
  const artifactsPath = id => join(root, `${id}.artifacts.json`);
  const operationsPath = () => join(root, '.operations.json');

  // In-process mutex: every mutation runs after the previous one settles.
  let chain = Promise.resolve();
  const withLock = fn => {
    const run = chain.then(fn, fn);
    chain = run.catch(() => {});
    return run;
  };

  const readJson = path => {
    try { return JSON.parse(readFileSync(path, 'utf8')); } catch (error) {
      if (error.code === 'ENOENT') return null;
      throw error;
    }
  };
  const writeJson = (path, value) => {
    writeFileSync(`${path}.tmp`, JSON.stringify(value), { mode: 0o600 });
    renameSync(`${path}.tmp`, path);
  };

  /** Normalize legacy (build-jobs.mjs) and v2 job documents. */
  const normalize = raw => {
    if (!raw) return null;
    return {
      id: raw.id,
      ownerHash: raw.ownerHash || raw.owner || null,
      prompt: raw.prompt,
      context: raw.context || '',
      responseMode: raw.responseMode || 'apk',
      status: raw.status,
      created: raw.created ?? (raw.createdAt ? Date.parse(raw.createdAt) : 0),
      createdAt: raw.createdAt || new Date(raw.created ?? 0).toISOString(),
      updatedAt: raw.updatedAt || raw.createdAt || new Date(raw.created ?? 0).toISOString(),
      queuedAt: raw.queuedAt ?? raw.created ?? 0,
      completedAt: raw.completedAt ?? null,
      lease: raw.lease || null,
      leaseExpiresAt: raw.leaseExpiresAt ?? raw.expires ?? null,
      attemptCount: raw.attemptCount ?? 0,
      attemptStartedAt: raw.attemptStartedAt ?? null,
      queueRevision: raw.queueRevision ?? 0,
      version: raw.version ?? 0,
      appName: raw.appName ?? null,
      message: raw.message ?? null,
      errorCode: raw.errorCode ?? null,
      sha256: raw.sha256 ?? null,
      size: raw.size ?? null,
      resultArtifactId: raw.resultArtifactId ?? null,
      resultExpiresAt: raw.resultExpiresAt ?? null,
      previewHtml: raw.previewHtml ?? null,
      requestHash: raw.requestHash ?? null,
      verification: raw.verification ?? null,
      clarifications: (raw.clarifications || []).map(entry => ({
        id: entry.id, question: entry.question, answer: entry.answer ?? null,
      })),
    };
  };

  const persist = job => {
    const doc = { ...job, owner: job.ownerHash, expires: job.leaseExpiresAt };
    writeJson(jobPath(job.id), doc);
    return normalize(doc);
  };
  const read = id => normalize(readJson(jobPath(id)));
  const listAll = () => readdirSync(root)
    .filter(file => UUID_FILE.test(file))
    .map(file => read(file.slice(0, -5)))
    .filter(Boolean);
  const readArtifacts = jobId => readJson(artifactsPath(jobId)) || [];
  const writeArtifacts = (jobId, list) => writeJson(artifactsPath(jobId), list);
  const readOperations = () => readJson(operationsPath()) || {};
  const writeOperations = map => writeJson(operationsPath(), map);

  /** Idempotency: same operationId + same request hash returns the stored result. */
  const checkOperation = (operationId, requestHash) => {
    if (!operationId) return null;
    const stored = readOperations()[operationId];
    if (!stored) return null;
    if (stored.requestHash !== requestHash) {
      throw conflict('ID operasi sudah dipakai dengan payload berbeda.', 'operation_conflict');
    }
    return stored;
  };
  const recordOperation = (operationId, requestHash, result) => {
    if (!operationId) return;
    const map = readOperations();
    map[operationId] = { operationId, requestHash, result, createdAt: new Date(now()).toISOString() };
    writeOperations(map);
  };
  const opHash = value => sha256Hex(Buffer.from(JSON.stringify(value)));

  const requireJob = id => {
    const job = read(id);
    if (!job) throw notFound();
    return job;
  };
  const requireOwner = (job, ownerHash) => {
    if (ownerHash && job.ownerHash && job.ownerHash !== ownerHash) throw notFound();
  };
  const requireLease = (job, lease, at) => {
    if (job.status !== 'building' || !leaseActive(job, at) || job.lease !== lease) {
      throw conflict('Klaim tugas tidak aktif.', 'lease_expired');
    }
  };

  return {
    kind: 'filesystem',
    root,

    async create({ id, ownerHash, prompt, context, responseMode }) {
      return withLock(() => {
        const at = now();
        const requestHash = canonicalRequestHash({ id, ownerHash, prompt, context, responseMode });
        const existing = read(id);
        if (existing) {
          requireOwner(existing, ownerHash);
          if (existing.requestHash && existing.requestHash !== requestHash) {
            throw conflict('ID sudah dipakai.', 'id_reused');
          }
          if (existing.prompt !== prompt || existing.context !== (context || '') || existing.responseMode !== responseMode) {
            throw conflict('ID sudah dipakai.', 'id_reused');
          }
          return { job: existing, created: false };
        }
        const job = normalize({
          id, ownerHash, owner: ownerHash, prompt, context: context || '', responseMode,
          status: 'queued', created: at, createdAt: new Date(at).toISOString(),
          updatedAt: new Date(at).toISOString(), queuedAt: at,
          queueRevision: 0, version: 0, clarifications: [], requestHash,
        });
        persist(job);
        return { job, created: true };
      });
    },

    async get(id) {
      return read(id);
    },

    async signal() {
      const at = now();
      const pending = listAll()
        .filter(job => job.status === 'queued' || (job.status === 'building' && !leaseActive(job, at)))
        .sort((a, b) => (a.queuedAt - b.queuedAt) || (a.id < b.id ? -1 : 1));
      const job = pending[0];
      if (!job) return { pending: false };
      return { pending: true, jobId: job.id, queueRevision: job.queueRevision, lease: job.lease || '' };
    },

    async claim({ claimRequestId, workerId = 'muse-primary' }) {
      return withLock(() => {
        const at = now();
        const stored = checkOperation(claimRequestId, opHash(['claim', workerId]));
        if (stored) {
          const job = read(stored.result.jobId);
          return job && job.status === 'building' && job.lease === stored.result.lease
            ? { job, replayed: true }
            : { job: null, replayed: true };
        }
        // Single-worker slot: an actively building job blocks a new claim.
        const active = listAll().find(job => job.status === 'building' && leaseActive(job, at));
        if (active) return { job: null, slotBusy: true };
        const candidate = listAll()
          .filter(job => job.status === 'queued' || (job.status === 'building' && !leaseActive(job, at)))
          .sort((a, b) => (a.queuedAt - b.queuedAt) || (a.id < b.id ? -1 : 1))[0];
        if (!candidate) return { job: null };
        if (candidate.attemptCount >= LIMITS.maxAttempts && candidate.status === 'building') {
          candidate.status = 'failed';
          candidate.errorCode = 'attempts_exhausted';
          candidate.completedAt = at;
          candidate.lease = null;
          candidate.leaseExpiresAt = null;
          persist(candidate);
          return { job: null };
        }
        candidate.status = 'building';
        candidate.lease = randomUUID();
        candidate.leaseExpiresAt = at + LIMITS.leaseMs;
        candidate.attemptCount += 1;
        candidate.attemptStartedAt = at;
        candidate.version += 1;
        candidate.updatedAt = new Date(at).toISOString();
        const job = persist(candidate);
        recordOperation(claimRequestId, opHash(['claim', workerId]), { jobId: job.id, lease: job.lease });
        return { job, replayed: false };
      });
    },

    async answer({ jobId, ownerHash, questionId, answer }) {
      return withLock(() => {
        const at = now();
        const job = requireJob(jobId);
        requireOwner(job, ownerHash);
        const question = job.clarifications.find(entry => entry.id === questionId);
        if (question?.answer === answer) return { job, alreadyAnswered: true };
        if (!question || question.answer || job.status !== 'awaiting_answer') {
          throw conflict('Pertanyaan ini sudah tidak aktif.', 'question_inactive');
        }
        question.answer = answer;
        job.status = 'queued';
        job.queueRevision += 1;
        job.queuedAt = at;
        job.lease = null;
        job.leaseExpiresAt = null;
        job.version += 1;
        job.updatedAt = new Date(at).toISOString();
        return { job: persist(job), alreadyAnswered: false };
      });
    },

    async askQuestion({ jobId, lease, operationId, question }) {
      return withLock(() => {
        const at = now();
        const stored = checkOperation(operationId, opHash(['question', jobId, question]));
        if (stored) return { job: read(stored.result.jobId), replayed: true };
        const job = requireJob(jobId);
        requireLease(job, lease, at);
        if (job.clarifications.length >= LIMITS.maxClarifications) {
          throw conflict('Batas klarifikasi tercapai. Lanjutkan build.', 'clarifications_exhausted');
        }
        const entry = { id: randomUUID(), question, answer: null };
        job.clarifications.push(entry);
        job.status = 'awaiting_answer';
        job.lease = null;
        job.leaseExpiresAt = null;
        job.version += 1;
        job.updatedAt = new Date(at).toISOString();
        const saved = persist(job);
        recordOperation(operationId, opHash(['question', jobId, question]), { jobId: job.id, questionId: entry.id });
        return { job: saved, replayed: false };
      });
    },

    async sendMessage({ jobId, lease, operationId, message }) {
      return withLock(() => {
        const at = now();
        const stored = checkOperation(operationId, opHash(['message', jobId, message]));
        if (stored) return { job: read(stored.result.jobId), replayed: true };
        const job = requireJob(jobId);
        requireLease(job, lease, at);
        if (job.responseMode !== 'chat') {
          throw conflict('Klien APK lama membutuhkan hasil APK atau klarifikasi.', 'chat_unsupported');
        }
        job.status = 'responded';
        job.message = message;
        job.completedAt = at;
        job.lease = null;
        job.leaseExpiresAt = null;
        job.version += 1;
        job.updatedAt = new Date(at).toISOString();
        const saved = persist(job);
        recordOperation(operationId, opHash(['message', jobId, message]), { jobId: job.id });
        return { job: saved, replayed: false };
      });
    },

    async setMetadata({ jobId, lease, operationId, appName }) {
      return withLock(() => {
        const at = now();
        const stored = checkOperation(operationId, opHash(['metadata', jobId, appName]));
        if (stored) return { job: read(stored.result.jobId), replayed: true };
        const job = requireJob(jobId);
        requireLease(job, lease, at);
        job.appName = appName;
        job.version += 1;
        job.updatedAt = new Date(at).toISOString();
        const saved = persist(job);
        recordOperation(operationId, opHash(['metadata', jobId, appName]), { jobId: job.id });
        return { job: saved, replayed: false };
      });
    },

    async fail({ jobId, lease, operationId, errorCode = 'build_failed' }) {
      return withLock(() => {
        const at = now();
        const stored = checkOperation(operationId, opHash(['fail', jobId, errorCode]));
        if (stored) return { job: read(stored.result.jobId), replayed: true };
        const job = requireJob(jobId);
        requireLease(job, lease, at);
        job.status = 'failed';
        job.errorCode = errorCode;
        job.completedAt = at;
        job.lease = null;
        job.leaseExpiresAt = null;
        job.version += 1;
        job.updatedAt = new Date(at).toISOString();
        const saved = persist(job);
        recordOperation(operationId, opHash(['fail', jobId, errorCode]), { jobId: job.id });
        return { job: saved, replayed: false };
      });
    },

    async renew({ jobId, lease }) {
      return withLock(() => {
        const at = now();
        const job = requireJob(jobId);
        requireLease(job, lease, at);
        job.leaseExpiresAt = at + LIMITS.leaseMs;
        job.version += 1;
        job.updatedAt = new Date(at).toISOString();
        const saved = persist(job);
        return { job: saved, leaseExpiresAt: saved.leaseExpiresAt };
      });
    },

    async prepareArtifact({ jobId, lease, operationId, kind, size, sha256 }) {
      return withLock(() => {
        const at = now();
        const stored = checkOperation(operationId, opHash(['prepare', jobId, kind, size, sha256]));
        if (stored) {
          const artifacts = readArtifacts(jobId);
          const artifact = artifacts.find(entry => entry.id === stored.result.artifactId);
          return { artifact, replayed: true };
        }
        const job = requireJob(jobId);
        requireLease(job, lease, at);
        const generationId = randomUUID();
        const artifact = {
          id: randomUUID(), jobId, leaseId: job.lease, generationId, kind,
          objectPath: artifactObjectPath({ jobId, leaseId: job.lease, generationId, kind }),
          expectedSize: size, expectedSha256: sha256,
          status: 'pending', createdAt: new Date(at).toISOString(),
          verifiedSize: null, verifiedSha256: null,
        };
        const artifacts = readArtifacts(jobId);
        artifacts.push(artifact);
        writeArtifacts(jobId, artifacts);
        recordOperation(operationId, opHash(['prepare', jobId, kind, size, sha256]), { artifactId: artifact.id });
        // Filesystem backend has no Storage: the worker (or test) stores the
        // bytes locally via storeArtifactBytes; there is no signed upload URL.
        return { artifact: { ...artifact, uploadUrl: null, uploadToken: null, method: 'local', expiresAt: null }, replayed: false };
      });
    },

    /** Local-only helper (tests / local worker): store artifact bytes on disk. */
    async storeArtifactBytes({ artifactId, bytes }) {
      return withLock(() => {
        const at = now();
        for (const job of listAll()) {
          const artifacts = readArtifacts(job.id);
          const artifact = artifacts.find(entry => entry.id === artifactId);
          if (!artifact) continue;
          if (artifact.kind === 'apk') {
            const inspected = inspectApkBytes(bytes);
            if (inspected.size !== artifact.expectedSize || inspected.sha256 !== artifact.expectedSha256) {
              throw new ApiError(400, 'Ukuran atau SHA-256 artifact tidak cocok.');
            }
            writeFileSync(apkPath(job.id) + '.tmp', bytes, { mode: 0o600 });
            renameSync(apkPath(job.id) + '.tmp', apkPath(job.id));
            artifact.previewHtml = extractPreviewHtml(bytes);
          } else {
            if (bytes.length !== artifact.expectedSize || sha256Hex(bytes) !== artifact.expectedSha256) {
              throw new ApiError(400, 'Ukuran atau SHA-256 artifact tidak cocok.');
            }
            const path = join(root, `${job.id}.${artifact.kind}.bin`);
            writeFileSync(`${path}.tmp`, bytes, { mode: 0o600 });
            renameSync(`${path}.tmp`, path);
          }
          artifact.status = 'verified';
          artifact.verifiedSize = bytes.length;
          artifact.verifiedSha256 = sha256Hex(bytes);
          artifact.verifiedAt = new Date(at).toISOString();
          writeArtifacts(job.id, artifacts);
          return { artifact };
        }
        throw notFound('Artifact tidak ditemukan.');
      });
    },

    async finalize({ jobId, lease, operationId, artifactId, previewHtml, verification }) {
      return withLock(() => {
        const at = now();
        const stored = checkOperation(operationId, opHash(['finalize', jobId, artifactId]));
        if (stored) return { job: read(stored.result.jobId), replayed: true };
        const job = requireJob(jobId);
        // Re-check the lease AFTER verification, per design §9.
        requireLease(job, lease, at);
        const artifacts = readArtifacts(jobId);
        const artifact = artifacts.find(entry => entry.id === artifactId);
        if (!artifact || artifact.jobId !== jobId || artifact.leaseId !== job.lease) {
          throw conflict('Artifact tidak terdaftar untuk klaim ini.', 'artifact_mismatch');
        }
        if (artifact.kind !== 'apk' || artifact.status !== 'verified') {
          throw conflict('Artifact belum terverifikasi.', 'artifact_unverified');
        }
        if (!existsSync(apkPath(jobId))) throw notFound('Artifact tidak ditemukan.');
        const storedBytes = readFileSync(apkPath(jobId));
        const inspected = inspectApkBytes(storedBytes);
        if (inspected.size !== artifact.verifiedSize || inspected.sha256 !== artifact.verifiedSha256) {
          throw new ApiError(400, 'Artifact di disk berubah setelah verifikasi.');
        }
        const extracted = extractPreviewHtml(storedBytes);
        if (previewHtml && extracted && previewHtml !== extracted) {
          throw new ApiError(400, 'Preview tidak cocok dengan assets/index.html di APK.');
        }
        job.status = 'ready';
        job.sha256 = artifact.verifiedSha256;
        job.size = artifact.verifiedSize;
        job.resultArtifactId = artifact.id;
        job.resultExpiresAt = at + 72 * 60 * 60_000; // 72h retention, design §13
        job.previewHtml = previewHtml ?? extracted ?? null;
        job.verification = verification ?? null;
        job.completedAt = at;
        job.lease = null;
        job.leaseExpiresAt = null;
        job.version += 1;
        job.updatedAt = new Date(at).toISOString();
        const saved = persist(job);
        recordOperation(operationId, opHash(['finalize', jobId, artifactId]), { jobId: job.id });
        return { job: saved, replayed: false };
      });
    },

    /** Filesystem has no signed Storage download; callers fall back to /apk. */
    async createSignedDownloadUrl() {
      return null;
    },

    async getPreview(jobId) {
      const job = read(jobId);
      if (!job || job.status !== 'ready' || !job.previewHtml) return null;
      return { html: job.previewHtml };
    },
  };
}
