// Supabase repository for the YAP build gateway (v2 cloud path).
// Access is REST/RPC only (PostgREST + Storage HTTP), via the service-role
// key held by the server — never a direct Postgres connection, never the
// anon key, and never exposed to Android or the worker. All mutations go
// through SECURITY DEFINER RPCs in server/supabase/migrations/0002_rpc.sql,
// which enforce atomic claim, lease checks against database time, the
// single-worker slot, and idempotency via build_operations.
// Every RPC returns a JSON envelope {ok, ...} or {ok:false, status, code,
// message}; transport errors are mapped to ApiError here.
import { ApiError, notFound, ADMIN_OWNER_HASH } from './build-service.mjs';
import { verifyArtifactObject } from './artifact-storage.mjs';

const BUCKET = 'yap-artifacts';

function envelopeError(body, httpStatus) {
  if (body && typeof body === 'object' && body.ok === false) {
    return new ApiError(body.status || httpStatus || 400, body.message || 'Operasi gagal.', body.code);
  }
  const message = body?.message || body?.error || `Supabase mengembalikan HTTP ${httpStatus}.`;
  return new ApiError(httpStatus >= 400 && httpStatus < 500 ? httpStatus : 502, String(message));
}

export function createSupabaseRepository({
  supabaseUrl, serviceRoleKey, fetchImpl = fetch, bucket = BUCKET, now = Date.now,
} = {}) {
  if (!supabaseUrl || !serviceRoleKey) {
    throw new Error('SUPABASE_URL dan SUPABASE_SECRET_KEY wajib diisi untuk backend supabase.');
  }
  const base = String(supabaseUrl).replace(/\/+$/, '');

  const headers = extra => ({
    apikey: serviceRoleKey,
    Authorization: `Bearer ${serviceRoleKey}`,
    'Content-Type': 'application/json',
    ...extra,
  });

  async function call(path, { method = 'POST', body, rawHeaders } = {}) {
    let response;
    try {
      response = await fetchImpl(`${base}${path}`, {
        method,
        headers: rawHeaders || headers(),
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (error) {
      throw new ApiError(502, `Tidak dapat menghubungi Supabase: ${error.message}`);
    }
    let data = null;
    try { data = await response.json(); } catch { /* empty body */ }
    if (!response.ok) throw envelopeError(data, response.status);
    return data;
  }

  const rpc = (name, args) => call(`/rest/v1/rpc/${name}`, { body: args ?? {} });

  /** Unwrap an RPC envelope; throws ApiError on {ok:false}. */
  function unwrap(data, label) {
    if (data && data.ok === false) throw envelopeError(data, data.status || 400);
    if (!data || data.ok !== true) throw new ApiError(502, `Respons RPC ${label} tidak valid.`);
    return data;
  }

  // Row -> internal job shape (same field names as the filesystem repo).
  const fromRow = row => row ? {
    id: row.id,
    ownerHash: row.owner_hash,
    prompt: row.prompt,
    context: row.context || '',
    responseMode: row.response_mode || 'apk',
    status: row.status,
    created: row.created_at ? Date.parse(row.created_at) : 0,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    queuedAt: row.queued_at ? Date.parse(row.queued_at) : 0,
    completedAt: row.completed_at ? Date.parse(row.completed_at) : null,
    lease: row.lease_id || null,
    leaseExpiresAt: row.lease_expires_at ? Date.parse(row.lease_expires_at) : null,
    attemptCount: row.attempt_count ?? 0,
    attemptStartedAt: row.attempt_started_at ? Date.parse(row.attempt_started_at) : null,
    queueRevision: row.queue_revision ?? 0,
    version: row.version ?? 0,
    appName: row.app_name ?? null,
    message: row.message ?? null,
    errorCode: row.error_code ?? null,
    sha256: row.sha256 ?? null,
    size: row.size ?? null,
    resultArtifactId: row.result_artifact_id ?? null,
    resultExpiresAt: row.result_expires_at ? Date.parse(row.result_expires_at) : null,
    previewHtml: row.preview_html ?? null,
    clarifications: (row.clarifications || []).map(entry => ({
      id: entry.id, question: entry.question, answer: entry.answer ?? null,
    })),
  } : null;

  async function getJobRow(id) {
    const rows = await call(`/rest/v1/build_jobs?id=eq.${encodeURIComponent(id)}&select=*,clarifications:build_questions(id,question,answer)`, { method: 'GET', rawHeaders: headers() });
    return Array.isArray(rows) && rows[0] ? rows[0] : null;
  }

  // Mint upload credentials for one registered artifact. The Storage
  // REST response for a signed upload URL is
  //   { "url": "/object/upload/sign/<bucket>/<path>?token=..." }
  // — a path relative to /storage/v1, with the token embedded in the
  // query string (there is NO separate token field). The token is
  // extracted from the URL and handed to the worker as the x-signature
  // header for TUS; standard uploads PUT straight to the full signed
  // URL. The object path is server-chosen and never worker-supplied.
  async function issueUploadCredentials(artifact, kind, size) {
    const signed = await call(`/storage/v1/object/upload/sign/${bucket}/${artifact.object_path}`, { body: {} });
    const relative = typeof signed?.url === 'string' ? signed.url : '';
    if (!relative.startsWith('/')) throw new ApiError(502, 'Storage tidak mengembalikan signed upload URL.');
    // The URL is relative to /storage/v1; tolerate a response that
    // already carries the prefix instead of doubling it.
    const fullUrl = relative.startsWith('/storage/v1/')
      ? `${base}${relative}`
      : `${base}/storage/v1${relative}`;
    const token = new URL(fullUrl).searchParams.get('token');
    if (!token) throw new ApiError(502, 'Storage tidak mengembalikan token upload.');
    const useTus = kind === 'apk' && size > 6 * 1024 * 1024;
    return {
      ...artifact,
      bucket,
      objectPath: artifact.object_path,
      uploadUrl: useTus ? `${base}/storage/v1/upload/resumable` : fullUrl,
      uploadToken: token,
      method: useTus ? 'tus' : 'standard',
      expiresAt: new Date(now() + 2 * 60 * 60_000).toISOString(),
    };
  }

  return {
    kind: 'supabase',
    bucket,

    async create({ id, ownerHash, prompt, context, responseMode }) {
      const data = unwrap(await rpc('yap_create_job', {
        p_id: id, p_owner_hash: ownerHash, p_prompt: prompt,
        p_context: context || '', p_response_mode: responseMode,
      }), 'yap_create_job');
      return { job: fromRow(data.job), created: data.created };
    },

    async get(id) {
      return fromRow(await getJobRow(id));
    },

    async signal() {
      const data = unwrap(await rpc('yap_signal', {}), 'yap_signal');
      if (!data.pending) return { pending: false };
      return {
        pending: true, jobId: data.job_id, queueRevision: data.queue_revision ?? 0,
        lease: data.lease_id || '',
      };
    },

    async claim({ claimRequestId, workerId = 'muse-primary' }) {
      const data = unwrap(await rpc('yap_claim_job', {
        p_claim_request_id: claimRequestId, p_worker_id: workerId,
      }), 'yap_claim_job');
      return { job: fromRow(data.job), replayed: data.replayed === true, slotBusy: data.slot_busy === true };
    },

    async answer({ jobId, ownerHash, questionId, answer }) {
      const data = unwrap(await rpc('yap_answer_question', {
        p_job_id: jobId, p_owner_hash: ownerHash, p_question_id: questionId, p_answer: answer,
      }), 'yap_answer_question');
      return { job: fromRow(data.job), alreadyAnswered: data.already_answered === true };
    },

    async askQuestion({ jobId, lease, operationId, question }) {
      const data = unwrap(await rpc('yap_ask_question', {
        p_job_id: jobId, p_lease_id: lease, p_operation_id: operationId, p_question: question,
      }), 'yap_ask_question');
      return { job: fromRow(data.job), replayed: data.replayed === true };
    },

    async sendMessage({ jobId, lease, operationId, message }) {
      const data = unwrap(await rpc('yap_send_message', {
        p_job_id: jobId, p_lease_id: lease, p_operation_id: operationId, p_message: message,
      }), 'yap_send_message');
      return { job: fromRow(data.job), replayed: data.replayed === true };
    },

    async setMetadata({ jobId, lease, operationId, appName }) {
      const data = unwrap(await rpc('yap_set_metadata', {
        p_job_id: jobId, p_lease_id: lease, p_operation_id: operationId, p_app_name: appName,
      }), 'yap_set_metadata');
      return { job: fromRow(data.job), replayed: data.replayed === true };
    },

    async fail({ jobId, lease, operationId, errorCode = 'build_failed' }) {
      const data = unwrap(await rpc('yap_fail_job', {
        p_job_id: jobId, p_lease_id: lease, p_operation_id: operationId, p_error_code: errorCode,
      }), 'yap_fail_job');
      return { job: fromRow(data.job), replayed: data.replayed === true };
    },

    async renew({ jobId, lease }) {
      const data = unwrap(await rpc('yap_renew_lease', {
        p_job_id: jobId, p_lease_id: lease,
      }), 'yap_renew_lease');
      return { job: fromRow(data.job), leaseExpiresAt: Date.parse(data.lease_expires_at) };
    },

    async prepareArtifact({ jobId, lease, operationId, kind, size, sha256 }) {
      const data = unwrap(await rpc('yap_prepare_artifact', {
        p_job_id: jobId, p_lease_id: lease, p_operation_id: operationId,
        p_kind: kind, p_size: size, p_sha256: sha256,
      }), 'yap_prepare_artifact');
      const artifact = data.artifact;
      if (data.replayed) {
        // A replay must re-validate before re-issuing upload
        // credentials for the same immutable object path: the job must
        // still be building under this lease, and the artifact must
        // still be pending. A verified artifact or a stale lease never
        // gets permission to overwrite a result.
        const job = await getJobRow(jobId);
        const leaseOk = job && job.status === 'building' && job.lease_id === lease
          && job.lease_expires_at && Date.parse(job.lease_expires_at) > now();
        if (!leaseOk) throw new ApiError(409, 'Klaim tugas tidak aktif.', 'lease_expired');
        if (!artifact || artifact.job_id !== jobId || artifact.lease_id !== lease) {
          throw new ApiError(409, 'Artifact tidak terdaftar untuk klaim ini.', 'artifact_mismatch');
        }
        if (artifact.status !== 'pending') {
          throw new ApiError(409, 'Artifact sudah terverifikasi; tidak dapat diunggah ulang.', 'artifact_unverified');
        }
      }
      // First request and replay share exactly one shape and one
      // credential-issuing path (same objectPath, same fields).
      return { artifact: await issueUploadCredentials(artifact, kind, size), replayed: data.replayed === true };
    },

    /** Direct-to-Storage upload is the worker's job; the server never accepts APK bytes. */
    async storeArtifactBytes() {
      throw new ApiError(426, 'Upload APK lewat gateway tidak didukung pada backend supabase; gunakan prepare + upload langsung ke Storage.');
    },

    async finalize({ jobId, lease, operationId, artifactId, previewHtml, verification }) {
      // 1. Fetch the registered artifact row (server-chosen path only).
      const rows = await call(`/rest/v1/build_artifacts?id=eq.${encodeURIComponent(artifactId)}&select=*`, { method: 'GET', rawHeaders: headers() });
      const artifact = Array.isArray(rows) && rows[0] ? rows[0] : null;
      if (!artifact || artifact.job_id !== jobId || artifact.lease_id !== lease) {
        throw new ApiError(409, 'Artifact tidak terdaftar untuk klaim ini.');
      }
      // 2. Outbound fetch from Storage + verify bytes ourselves (§9).
      const downloadUrl = `${base}/storage/v1/object/authenticated/${bucket}/${artifact.object_path}`;
      const verified = await verifyArtifactObject({
        url: downloadUrl,
        headers: { Authorization: `Bearer ${serviceRoleKey}`, apikey: serviceRoleKey },
        expectedSize: artifact.expected_size,
        expectedSha256: artifact.expected_sha256,
        fetchImpl,
      });
      if (previewHtml && verified.previewHtml && previewHtml !== verified.previewHtml) {
        throw new ApiError(400, 'Preview tidak cocok dengan assets/index.html di APK.');
      }
      // 3. RPC re-checks the lease and publishes ready atomically.
      const data = unwrap(await rpc('yap_finalize_job', {
        p_job_id: jobId, p_lease_id: lease, p_operation_id: operationId,
        p_artifact_id: artifactId, p_preview_html: previewHtml ?? verified.previewHtml ?? null,
        p_verified_size: verified.size, p_verified_sha256: verified.sha256,
        p_verification: verification ?? null,
      }), 'yap_finalize_job');
      return { job: fromRow(data.job), replayed: data.replayed === true };
    },

    async createSignedDownloadUrl({ jobId, ownerHash }) {
      const job = await this.get(jobId);
      if (!job) throw notFound();
      // Admin bypasses owner checks (v1 parity); guests need an exact match.
      if (ownerHash !== ADMIN_OWNER_HASH && (!ownerHash || !job.ownerHash || job.ownerHash !== ownerHash)) throw notFound();
      if (job.status !== 'ready' || !job.resultArtifactId) return null;
      const rows = await call(`/rest/v1/build_artifacts?id=eq.${encodeURIComponent(job.resultArtifactId)}&select=object_path`, { method: 'GET', rawHeaders: headers() });
      const artifact = Array.isArray(rows) && rows[0] ? rows[0] : null;
      if (!artifact) return null;
      const signed = await call(`/storage/v1/object/sign/${bucket}/${artifact.object_path}`, { body: { expiresIn: 300 } });
      return {
        url: `${base}${signed.signedURL}`,
        expiresAt: new Date(now() + 300_000).toISOString(),
        sha256: job.sha256, size: job.size,
        fileName: `${job.appName || `Aplikasi-${job.id.slice(0, 8)}`}.apk`,
      };
    },

    async getPreview(jobId) {
      const job = await this.get(jobId);
      if (!job || job.status !== 'ready' || !job.previewHtml) return null;
      return { html: job.previewHtml };
    },
  };
}
