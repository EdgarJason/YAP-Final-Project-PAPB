// Supabase repository tests with a mocked fetch — no live project needed.
// The live integration test lives in supabase-live.test.mjs and is skipped
// without credentials.
import assert from 'node:assert/strict';
import test from 'node:test';
import { zipSync, strToU8 } from 'fflate';
import { createSupabaseRepository } from '../lib/build-repository-supabase.mjs';
import { sha256Hex } from '../lib/build-service.mjs';

const UUID = '123e4567-e89b-12d3-a456-426614174000';
const URL = 'https://example.supabase.co';
const KEY = 'service-role-key';

function jsonResponse(status, body) {
  return { ok: status >= 200 && status < 300, status, json: async () => body };
}

function makeFixtureApk() {
  return Buffer.from(zipSync({
    'AndroidManifest.xml': strToU8('<manifest package="com.example.test"/>'),
    'classes.dex': strToU8('dex\n035\0'),
    'assets/index.html': strToU8('<html><body>hi</body></html>'),
  }));
}

const JOB_ROW = {
  id: UUID, owner_hash: 'owner', prompt: 'Buat kalkulator', context: '', response_mode: 'apk',
  status: 'queued', created_at: '2026-11-16T00:00:00Z', updated_at: '2026-11-16T00:00:00Z',
  queued_at: '2026-11-16T00:00:00Z', completed_at: null, lease_id: null, lease_expires_at: null,
  attempt_count: 0, attempt_started_at: null, queue_revision: 0, version: 0,
  app_name: null, message: null, error_code: null, sha256: null, size: null,
  result_artifact_id: null, result_expires_at: null, preview_html: null, clarifications: [],
};

test('constructor requires a URL and a service-role key', () => {
  assert.throws(() => createSupabaseRepository({}), /SUPABASE_URL/);
  assert.throws(() => createSupabaseRepository({ supabaseUrl: URL }), /SUPABASE_SECRET_KEY/);
});

test('create maps the RPC envelope to the internal job shape', async () => {
  const seen = [];
  const fetchImpl = async (url, options) => {
    seen.push(url);
    assert.match(options.headers.Authorization, /^Bearer service-role-key$/);
    return jsonResponse(200, { ok: true, job: JOB_ROW, created: true });
  };
  const repo = createSupabaseRepository({ supabaseUrl: URL, serviceRoleKey: KEY, fetchImpl });
  const { job, created } = await repo.create({
    id: UUID, ownerHash: 'owner', prompt: 'Buat kalkulator', context: '', responseMode: 'apk',
  });
  assert.equal(created, true);
  assert.equal(job.id, UUID);
  assert.equal(job.status, 'queued');
  assert.match(seen[0], /\/rest\/v1\/rpc\/yap_create_job$/);
});

test('RPC business errors surface as ApiError with the envelope status', async () => {
  const fetchImpl = async () => jsonResponse(200, {
    ok: false, status: 409, code: 'id_reused', message: 'ID sudah dipakai.',
  });
  const repo = createSupabaseRepository({ supabaseUrl: URL, serviceRoleKey: KEY, fetchImpl });
  await assert.rejects(
    repo.create({ id: UUID, ownerHash: 'owner', prompt: 'x', context: '', responseMode: 'apk' }),
    error => error.status === 409 && error.code === 'id_reused',
  );
});

test('transport failures map to 401/502, not to a silent success', async () => {
  const unauthorizedFetch = async () => jsonResponse(401, { message: 'Invalid API key' });
  const repo401 = createSupabaseRepository({ supabaseUrl: URL, serviceRoleKey: KEY, fetchImpl: unauthorizedFetch });
  await assert.rejects(repo401.signal(), error => error.status === 401);

  const downFetch = async () => { throw new Error('connect ECONNREFUSED'); };
  const repoDown = createSupabaseRepository({ supabaseUrl: URL, serviceRoleKey: KEY, fetchImpl: downFetch });
  await assert.rejects(repoDown.signal(), error => error.status === 502);
});

test('claim replays the stored lease and prepare picks TUS for large APKs', async () => {
  const calls = [];
  const fetchImpl = async (url, options) => {
    calls.push(url);
    if (url.includes('/rpc/yap_claim_job')) {
      return jsonResponse(200, { ok: true, job: { ...JOB_ROW, status: 'building', lease_id: UUID }, replayed: true });
    }
    if (url.includes('/rpc/yap_prepare_artifact')) {
      return jsonResponse(200, {
        ok: true,
        artifact: {
          id: UUID, job_id: UUID, lease_id: UUID, generation_id: UUID, kind: 'apk',
          object_path: `jobs/${UUID}/${UUID}/${UUID}/app.apk`, expected_size: 7_000_000,
          expected_sha256: 'a'.repeat(64), status: 'pending',
        },
        replayed: false,
      });
    }
    if (url.includes('/storage/v1/object/upload/sign/')) {
      // Real Storage REST shape: {url} relative to /storage/v1, token
      // embedded in the query string — NO separate token field.
      return jsonResponse(200, { url: '/object/upload/sign/yap-artifacts/jobs/x?token=tok' });
    }
    throw new Error(`unexpected URL ${url}`);
  };
  const repo = createSupabaseRepository({ supabaseUrl: URL, serviceRoleKey: KEY, fetchImpl });
  const claimed = await repo.claim({ claimRequestId: randomUUID() });
  assert.equal(claimed.replayed, true);
  assert.equal(claimed.job.lease, UUID);
  const prepared = await repo.prepareArtifact({
    jobId: UUID, lease: UUID, operationId: randomUUID(),
    kind: 'apk', size: 7_000_000, sha256: 'a'.repeat(64),
  });
  assert.equal(prepared.artifact.method, 'tus');
  assert.match(prepared.artifact.uploadUrl, /upload\/resumable$/);
  assert.equal(prepared.artifact.uploadToken, 'tok');
  assert.equal(prepared.artifact.objectPath, `jobs/${UUID}/${UUID}/${UUID}/app.apk`);
});

test('prepare replay re-issues upload credentials for the same object path', async () => {
  const future = new Date(Date.now() + 10 * 60_000).toISOString();
  const artifact = {
    id: UUID, job_id: UUID, lease_id: UUID, generation_id: UUID, kind: 'apk',
    object_path: `jobs/${UUID}/${UUID}/${UUID}/app.apk`, expected_size: 1000,
    expected_sha256: 'b'.repeat(64), status: 'pending',
  };
  const fetchImpl = async (url, options) => {
    if (url.includes('/rpc/yap_prepare_artifact')) {
      return jsonResponse(200, { ok: true, artifact, replayed: true });
    }
    if (url.includes('/rest/v1/build_jobs')) {
      return jsonResponse(200, [{ ...JOB_ROW, status: 'building', lease_id: UUID, lease_expires_at: future }]);
    }
    if (url.includes('/storage/v1/object/upload/sign/')) {
      return jsonResponse(200, { url: '/object/upload/sign/yap-artifacts/jobs/x?token=tok2' });
    }
    throw new Error(`unexpected URL ${url}`);
  };
  const repo = createSupabaseRepository({ supabaseUrl: URL, serviceRoleKey: KEY, fetchImpl });
  const replayed = await repo.prepareArtifact({
    jobId: UUID, lease: UUID, operationId: randomUUID(),
    kind: 'apk', size: 1000, sha256: 'b'.repeat(64),
  });
  assert.equal(replayed.replayed, true);
  assert.equal(replayed.artifact.id, UUID);
  assert.equal(replayed.artifact.objectPath, artifact.object_path);
  assert.equal(replayed.artifact.uploadToken, 'tok2');
  assert.match(replayed.artifact.uploadUrl, /\/storage\/v1\/object\/upload\/sign\//);
});

test('prepare replay refuses a verified artifact or a stale lease', async () => {
  const past = new Date(Date.now() - 60_000).toISOString();
  const makeFetch = jobRow => async url => {
    if (url.includes('/rpc/yap_prepare_artifact')) {
      return jsonResponse(200, {
        ok: true,
        artifact: {
          id: UUID, job_id: UUID, lease_id: UUID, generation_id: UUID, kind: 'apk',
          object_path: `jobs/${UUID}/${UUID}/${UUID}/app.apk`, expected_size: 1000,
          expected_sha256: 'b'.repeat(64), status: jobRow.status,
        },
        replayed: true,
      });
    }
    if (url.includes('/rest/v1/build_jobs')) return jsonResponse(200, [jobRow]);
    throw new Error(`unexpected URL ${url}`);
  };
  const repoVerified2 = createSupabaseRepository({
    supabaseUrl: URL, serviceRoleKey: KEY,
    fetchImpl: async url => {
      if (url.includes('/rpc/yap_prepare_artifact')) {
        return jsonResponse(200, {
          ok: true,
          artifact: {
            id: UUID, job_id: UUID, lease_id: UUID, generation_id: UUID, kind: 'apk',
            object_path: `jobs/${UUID}/${UUID}/${UUID}/app.apk`, expected_size: 1000,
            expected_sha256: 'b'.repeat(64), status: 'verified',
          },
          replayed: true,
        });
      }
      if (url.includes('/rest/v1/build_jobs')) {
        return jsonResponse(200, [{ ...JOB_ROW, status: 'building', lease_id: UUID, lease_expires_at: new Date(Date.now() + 600_000).toISOString() }]);
      }
      throw new Error(`unexpected URL ${url}`);
    },
  });
  await assert.rejects(
    repoVerified2.prepareArtifact({ jobId: UUID, lease: UUID, operationId: randomUUID(), kind: 'apk', size: 1000, sha256: 'b'.repeat(64) }),
    error => error.status === 409,
  );
  const repoStale = createSupabaseRepository({
    supabaseUrl: URL, serviceRoleKey: KEY, fetchImpl: makeFetch({ ...JOB_ROW, status: 'building', lease_id: UUID, lease_expires_at: past }),
  });
  await assert.rejects(
    repoStale.prepareArtifact({ jobId: UUID, lease: UUID, operationId: randomUUID(), kind: 'apk', size: 1000, sha256: 'b'.repeat(64) }),
    error => error.status === 409 && error.code === 'lease_expired',
  );
});

test('finalize verifies bytes outbound before calling the finalize RPC', async () => {
  const apk = makeFixtureApk();
  const artifactId = UUID;
  const seenRpc = [];
  const fetchImpl = async (url, options) => {
    if (url.includes('/rest/v1/build_artifacts')) {
      return jsonResponse(200, [{
        id: artifactId, job_id: UUID, lease_id: UUID,
        object_path: `jobs/${UUID}/${UUID}/${UUID}/app.apk`,
        expected_size: apk.length, expected_sha256: sha256Hex(apk), status: 'pending',
      }]);
    }
    if (url.includes('/storage/v1/object/authenticated/')) {
      return {
        ok: true, status: 200,
        body: null,
        arrayBuffer: async () => apk.buffer.slice(apk.byteOffset, apk.byteOffset + apk.byteLength),
      };
    }
    if (url.includes('/rpc/yap_finalize_job')) {
      seenRpc.push(JSON.parse(options.body));
      return jsonResponse(200, {
        ok: true, job: { ...JOB_ROW, status: 'ready', sha256: sha256Hex(apk), size: apk.length },
      });
    }
    throw new Error(`unexpected URL ${url}`);
  };
  const repo = createSupabaseRepository({ supabaseUrl: URL, serviceRoleKey: KEY, fetchImpl });
  const { job } = await repo.finalize({
    jobId: UUID, lease: UUID, operationId: randomUUID(), artifactId,
    previewHtml: '<html><body>hi</body></html>', verification: { apksigner: 'verified' },
  });
  assert.equal(job.status, 'ready');
  assert.equal(job.sha256, sha256Hex(apk));
  assert.equal(seenRpc[0].p_verified_size, apk.length);
  assert.equal(seenRpc[0].p_verified_sha256, sha256Hex(apk));
});

test('storeArtifactBytes on the Supabase backend refuses gateway uploads', async () => {
  const repo = createSupabaseRepository({ supabaseUrl: URL, serviceRoleKey: KEY, fetchImpl: async () => jsonResponse(200, {}) });
  await assert.rejects(
    repo.storeArtifactBytes({ artifactId: UUID, bytes: Buffer.alloc(10) }),
    error => error.status === 426,
  );
});

function randomUUID() {
  return '123e4567-e89b-12d3-a456-426614174001';
}
