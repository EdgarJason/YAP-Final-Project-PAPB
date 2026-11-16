// Supabase Postgres regression tests — REAL PostgreSQL only, loopback.
// Runs migrations 0001 -> 0002 -> 0003 on TWO databases:
//   yap_cloud_test1 — pgcrypto in the default (public) schema
//   yap_cloud_test2 — pgcrypto pre-installed in the `extensions` schema
// (the layout Supabase uses; the original migration failed there).
// Requires a local PostgreSQL (PG16) with trust auth on loopback for the
// test databases — see server/supabase/README.md "Menjalankan tes Postgres
// lokal". If psql or the databases are unavailable the whole file is
// SKIPPED with a clear message; it never claims to pass without a DB.
// No npm dependency is added: psql is driven via child_process.
import assert from 'node:assert/strict';
import test, { describe, before, beforeEach } from 'node:test';
import { execFile, execFileSync } from 'node:child_process';
import { randomUUID, createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { join, dirname } from 'node:path';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const MIGRATIONS = join(ROOT, 'supabase', 'migrations');
const PGHOST = process.env.YAP_TEST_PGHOST || '127.0.0.1';
const PGPORT = process.env.YAP_TEST_PGPORT || '5432';
const PGUSER = process.env.YAP_TEST_PGUSER || 'yap_test';
const DBS = [
  { name: 'yap_cloud_test1', pgcryptoSchema: 'public' },
  { name: 'yap_cloud_test2', pgcryptoSchema: 'extensions' },
];
const SHA256_ABC = 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad';

const lit = value => `'${String(value).replace(/'/g, "''")}'`;

function psqlArgs(db, user = PGUSER) {
  return ['-h', PGHOST, '-p', PGPORT, '-U', user, '-d', db, '-X', '-v', 'ON_ERROR_STOP=1', '-t', '-A'];
}

function psql(db, sql, user = PGUSER) {
  return execFileSync('psql', [...psqlArgs(db, user), '-c', sql], {
    encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, PGPASSWORD: '' },
  }).trim();
}

function psqlAsync(db, sql, user = PGUSER) {
  return new Promise((resolve, reject) => {
    execFile('psql', [...psqlArgs(db, user), '-c', sql], {
      encoding: 'utf8', env: { ...process.env, PGPASSWORD: '' },
    }, (error, stdout, stderr) => {
      if (error) reject(new Error(stderr.trim() || error.message));
      else resolve(stdout.trim());
    });
  });
}

function psqlFile(db, file) {
  execFileSync('psql', [...psqlArgs(db), '-f', file], {
    encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env, PGPASSWORD: '' },
  });
}

// Call an RPC and parse its JSON envelope.
function rpc(db, fn, args) {
  const rendered = args.map(arg => {
    if (arg === null || arg === undefined) return 'NULL';
    if (typeof arg === 'number') return String(arg);
    if (typeof arg === 'object' && arg.raw) return arg.raw;
    return lit(arg);
  }).join(', ');
  return JSON.parse(psql(db, `SELECT ${fn}(${rendered})`));
}

function resetDb(db) {
  psql(db, `TRUNCATE build_jobs, build_questions, build_artifacts, build_events, build_operations
    RESTART IDENTITY CASCADE;
    TRUNCATE worker_slots;
    INSERT INTO worker_slots (id) VALUES ('muse-primary');`);
}

function setupDb(db) {
  // Minimal local stand-in for the Supabase storage schema (only the
  // tables 0003 touches); never a real Storage service.
  psql(db.name, `CREATE SCHEMA IF NOT EXISTS storage;
    CREATE TABLE IF NOT EXISTS storage.buckets (
      id text PRIMARY KEY, name text, public boolean,
      file_size_limit bigint, allowed_mime_types text[]);
    CREATE TABLE IF NOT EXISTS storage.objects (
      id uuid PRIMARY KEY DEFAULT gen_random_uuid(), bucket_id text, name text,
      owner uuid, created_at timestamptz DEFAULT now(),
      updated_at timestamptz DEFAULT now(),
      last_accessed_at timestamptz DEFAULT now(), metadata jsonb, version text);`);
  if (db.pgcryptoSchema === 'extensions') {
    psql(db.name, `CREATE SCHEMA IF NOT EXISTS extensions;
      CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;`);
  }
  for (const file of ['0001_init.sql', '0002_rpc.sql', '0003_storage.sql']) {
    psqlFile(db.name, join(MIGRATIONS, file));
  }
}

let PG_AVAILABLE = false;
try {
  execFileSync('psql', ['--version'], { stdio: 'ignore' });
  psql(DBS[0].name, 'SELECT 1');
  PG_AVAILABLE = true;
} catch {
  PG_AVAILABLE = false;
}

const ownerHash = seed => createHash('sha256').update(`guest-${seed}`).digest('hex');

function createJob(db, { id = randomUUID(), owner = ownerHash('a'), prompt = 'Buat kalkulator', context = '', mode = 'apk' } = {}) {
  return rpc(db, 'yap_create_job', [id, owner, prompt, context, mode]);
}

function claimJob(db, claimRequestId = randomUUID(), workerId = 'muse-primary') {
  return rpc(db, 'yap_claim_job', [claimRequestId, workerId]);
}

function expireLease(db, jobId) {
  psql(db, `UPDATE build_jobs SET lease_expires_at = now() - interval '1 minute' WHERE id = ${lit(jobId)}`);
}

function setAttemptStarted(db, jobId, minutesAgo) {
  psql(db, `UPDATE build_jobs SET attempt_started_at = now() - interval '${minutesAgo} minutes' WHERE id = ${lit(jobId)}`);
}

describe('Supabase Postgres (PostgreSQL nyata, loopback)', { skip: !PG_AVAILABLE }, () => {
  before(() => {
    for (const db of DBS) setupDb(db);
  });

  for (const db of DBS) {
    describe(`database ${db.name} (pgcrypto di ${db.pgcryptoSchema})`, () => {
      beforeEach(() => resetDb(db.name));

      test('migrasi berjalan; helper hash benar; bucket privat ada', () => {
        assert.equal(psql(db.name, `SELECT public.yap_sha256_hex('abc')`), SHA256_ABC);
        const fnCount = Number(psql(db.name, `SELECT count(*) FROM pg_proc WHERE proname LIKE 'yap\\_%'`));
        assert.ok(fnCount >= 10, `diharapkan >= 10 fungsi yap_*, didapat ${fnCount}`);
        const bucket = psql(db.name, `SELECT public FROM storage.buckets WHERE id = 'yap-artifacts'`);
        assert.equal(bucket, 'f');
        assert.deepEqual(rpc(db.name, 'yap_signal', []), { ok: true, pending: false });
      });

      test('signal + create idempotent (payload sama 200, berubah 409, owner lain 404)', () => {
        const id = randomUUID();
        const owner = ownerHash('a');
        const first = createJob(db.name, { id, owner });
        assert.equal(first.ok, true);
        assert.equal(first.created, true);
        assert.equal(first.job.status, 'queued');
        assert.deepEqual(rpc(db.name, 'yap_signal', []).pending, true);
        const retry = createJob(db.name, { id, owner });
        assert.equal(retry.ok, true);
        assert.equal(retry.created, false);
        const changed = createJob(db.name, { id, owner, prompt: 'Buat yang lain' });
        assert.equal(changed.ok, false);
        assert.equal(changed.status, 409);
        assert.equal(changed.code, 'id_reused');
        const otherOwner = createJob(db.name, { id, owner: ownerHash('b') });
        assert.equal(otherOwner.ok, false);
        assert.equal(otherOwner.status, 404);
      });

      test('kuota harian: 5 build per owner, ke-6 ditolak 429', () => {
        const owner = ownerHash('quota');
        for (let i = 0; i < 5; i++) {
          const res = createJob(db.name, { owner, prompt: `Aplikasi ${i}` });
          assert.equal(res.ok, true, `build ke-${i + 1} harus lolos`);
        }
        const sixth = createJob(db.name, { owner, prompt: 'Aplikasi 6' });
        assert.equal(sixth.ok, false);
        assert.equal(sixth.status, 429);
        assert.equal(sixth.code, 'daily_quota_exceeded');
      });

      test('batas antrean global 20, ke-21 ditolak 409', () => {
        for (let i = 0; i < 20; i++) {
          const res = createJob(db.name, { owner: ownerHash(`q${i}`), prompt: `Aplikasi ${i}` });
          assert.equal(res.ok, true, `build ke-${i + 1} harus lolos`);
        }
        const extra = createJob(db.name, { owner: ownerHash('extra'), prompt: 'Satu lagi' });
        assert.equal(extra.ok, false);
        assert.equal(extra.status, 409);
        assert.equal(extra.code, 'queue_full');
      });

      test('kuota bertahan saat submit paralel', async () => {
        // 10 submit bersamaan dari satu owner -> tepat 5 lolos.
        const owner = ownerHash('race');
        const sameOwner = await Promise.allSettled(
          Array.from({ length: 10 }, (_, i) => psqlAsync(db.name,
            `SELECT yap_create_job(${lit(randomUUID())}, ${lit(owner)}, ${lit(`Paralel ${i}`)}, '', 'apk')`)),
        );
        const sameOk = sameOwner.filter(r => r.status === 'fulfilled' && JSON.parse(r.value).ok).length;
        const sameDenied = sameOwner.filter(r => r.status === 'fulfilled' && JSON.parse(r.value).code === 'daily_quota_exceeded').length;
        assert.equal(sameOk, 5);
        assert.equal(sameDenied, 5);
        resetDb(db.name);
        // 25 submit bersamaan dari owner berbeda -> tepat 20 lolos.
        const distinct = await Promise.allSettled(
          Array.from({ length: 25 }, (_, i) => psqlAsync(db.name,
            `SELECT yap_create_job(${lit(randomUUID())}, ${lit(ownerHash(`d${i}`))}, ${lit(`Paralel ${i}`)}, '', 'apk')`)),
        );
        const distinctOk = distinct.filter(r => r.status === 'fulfilled' && JSON.parse(r.value).ok).length;
        const distinctDenied = distinct.filter(r => r.status === 'fulfilled' && JSON.parse(r.value).code === 'queue_full').length;
        assert.equal(distinctOk, 20);
        assert.equal(distinctDenied, 5);
      });

      test('claim replay tidak memberi lease pengganti setelah reclaim', () => {
        const id = randomUUID();
        createJob(db.name, { id });
        const claimA = randomUUID();
        const first = claimJob(db.name, claimA);
        assert.equal(first.ok, true);
        assert.equal(first.replayed, false);
        const leaseA = first.job.lease_id;
        assert.ok(leaseA, 'claim pertama harus punya lease');
        const replayA = claimJob(db.name, claimA);
        assert.equal(replayA.replayed, true);
        assert.equal(replayA.job.lease_id, leaseA);
        // Lease A kedaluwarsa, lalu diklaim ulang menjadi lease B.
        expireLease(db.name, id);
        const claimB = randomUUID();
        const second = claimJob(db.name, claimB);
        assert.equal(second.ok, true);
        const leaseB = second.job.lease_id;
        assert.ok(leaseB && leaseB !== leaseA, 'reclaim harus memberi lease baru');
        // Replay claim A yang lama: job null, replayed true — BUKAN lease B.
        const stale = claimJob(db.name, claimA);
        assert.equal(stale.replayed, true);
        assert.equal(stale.job, null);
        // Replay claim B yang masih aktif tetap mengembalikan lease B.
        const replayB = claimJob(db.name, claimB);
        assert.equal(replayB.replayed, true);
        assert.equal(replayB.job.lease_id, leaseB);
      });

      test('replay claim setelah job terminal mengembalikan job null', () => {
        const id = randomUUID();
        createJob(db.name, { id });
        const claimId = randomUUID();
        const claimed = claimJob(db.name, claimId);
        const failed = rpc(db.name, 'yap_fail_job', [id, claimed.job.lease_id, randomUUID(), 'build_failed']);
        assert.equal(failed.ok, true);
        assert.equal(failed.job.status, 'failed');
        const replay = claimJob(db.name, claimId);
        assert.equal(replay.replayed, true);
        assert.equal(replay.job, null);
      });

      test('renew dibatasi deadline attempt 30 menit (29 lolos, 30/31 ditolak)', () => {
        const id = randomUUID();
        createJob(db.name, { id });
        const claimed = claimJob(db.name, randomUUID());
        const lease = claimed.job.lease_id;
        // 29 menit: renew lolos, tapi tidak melewati deadline.
        setAttemptStarted(db.name, id, 29);
        const at29 = rpc(db.name, 'yap_renew_lease', [id, lease]);
        assert.equal(at29.ok, true, 'renew di menit 29 harus lolos');
        const capped = psql(db.name,
          `SELECT lease_expires_at <= attempt_started_at + interval '30 minutes' FROM build_jobs WHERE id = ${lit(id)}`);
        assert.equal(capped, 't', 'lease tidak boleh melewati deadline attempt');
        // 30 menit: deadline tercapai -> ditolak.
        setAttemptStarted(db.name, id, 30);
        const at30 = rpc(db.name, 'yap_renew_lease', [id, lease]);
        assert.equal(at30.ok, false);
        assert.equal(at30.status, 409);
        assert.equal(at30.code, 'attempt_expired');
        // 31 menit: juga ditolak.
        setAttemptStarted(db.name, id, 31);
        const at31 = rpc(db.name, 'yap_renew_lease', [id, lease]);
        assert.equal(at31.ok, false);
        assert.equal(at31.code, 'attempt_expired');
      });

      test('question/answer: owner salah 404, jawab dua kali idempotent', () => {
        const id = randomUUID();
        const owner = ownerHash('q');
        createJob(db.name, { id, owner });
        const claimed = claimJob(db.name, randomUUID());
        const asked = rpc(db.name, 'yap_ask_question', [id, claimed.job.lease_id, randomUUID(), 'Warna apa?']);
        assert.equal(asked.ok, true);
        assert.equal(asked.job.status, 'awaiting_answer');
        const questionId = asked.job.clarifications[0].id;
        const wrongOwner = rpc(db.name, 'yap_answer_question', [id, ownerHash('lain'), questionId, 'Biru']);
        assert.equal(wrongOwner.ok, false);
        assert.equal(wrongOwner.status, 404);
        const answered = rpc(db.name, 'yap_answer_question', [id, owner, questionId, 'Biru']);
        assert.equal(answered.ok, true);
        assert.equal(answered.already_answered, false);
        assert.equal(answered.job.status, 'queued');
        const again = rpc(db.name, 'yap_answer_question', [id, owner, questionId, 'Biru']);
        assert.equal(again.ok, true);
        assert.equal(again.already_answered, true);
      });

      test('prepare replay + finalize: replay id artifact sama, finalize cocok', () => {
        const id = randomUUID();
        createJob(db.name, { id });
        const claimed = claimJob(db.name, randomUUID());
        const lease = claimed.job.lease_id;
        const opId = randomUUID();
        const sha = 'b'.repeat(64);
        const prepared = rpc(db.name, 'yap_prepare_artifact', [id, lease, opId, 'apk', 1234, sha]);
        assert.equal(prepared.ok, true);
        assert.equal(prepared.replayed, false);
        const artifactId = prepared.artifact.id;
        const objectPath = prepared.artifact.object_path;
        assert.match(objectPath, new RegExp(`^jobs/${id}/${lease}/`));
        const replay = rpc(db.name, 'yap_prepare_artifact', [id, lease, opId, 'apk', 1234, sha]);
        assert.equal(replay.ok, true);
        assert.equal(replay.replayed, true);
        assert.equal(replay.artifact.id, artifactId);
        assert.equal(replay.artifact.object_path, objectPath);
        // Finalize dengan hash salah ditolak.
        const wrong = rpc(db.name, 'yap_finalize_job', [id, lease, randomUUID(), artifactId, '<html/>', 1234, 'c'.repeat(64), { raw: `'{"apksigner": "verified"}'::jsonb` }]);
        assert.equal(wrong.ok, false);
        assert.equal(wrong.code, 'artifact_mismatch');
        // Finalize dengan hash benar -> ready.
        const done = rpc(db.name, 'yap_finalize_job', [id, lease, randomUUID(), artifactId, '<html/>', 1234, sha, { raw: `'{"apksigner": "verified"}'::jsonb` }]);
        assert.equal(done.ok, true);
        assert.equal(done.job.status, 'ready');
        assert.equal(done.job.sha256, sha);
        assert.equal(done.job.size, 1234);
        // Finalize setelah lease kedaluwarsa ditolak.
        const id2 = randomUUID();
        createJob(db.name, { id: id2 });
        const claimed2 = claimJob(db.name, randomUUID());
        const lease2 = claimed2.job.lease_id;
        const prep2 = rpc(db.name, 'yap_prepare_artifact', [id2, lease2, randomUUID(), 'apk', 10, 'd'.repeat(64)]);
        expireLease(db.name, id2);
        const late = rpc(db.name, 'yap_finalize_job', [id2, lease2, randomUUID(), prep2.artifact.id, null, 10, 'd'.repeat(64), { raw: 'NULL' }]);
        assert.equal(late.ok, false);
        assert.equal(late.code, 'lease_expired');
      });

      test('RLS: anon tidak bisa membaca tabel maupun memanggil RPC', () => {
        assert.throws(() => psql(db.name, 'SELECT count(*) FROM build_jobs', 'anon'), /permission denied/);
        assert.throws(() => psql(db.name, 'SELECT yap_signal()', 'anon'), /permission denied/);
      });

      test('namespace admin: job admin bisa dibuat; admin bisa menjawab job guest', () => {
        // Guest job dibuat dulu supaya claim mengambil job itu (claim
        // mengambil antrean tertua); job admin dibuat sesudahnya.
        const id = randomUUID();
        createJob(db.name, { id, owner: ownerHash('g') });
        const claimed = claimJob(db.name, randomUUID());
        assert.equal(claimed.job.id, id, 'claim harus mengambil job guest, bukan job lain');
        const adminJob = createJob(db.name, { owner: 'admin', prompt: 'Job admin' });
        assert.equal(adminJob.ok, true);
        assert.equal(adminJob.job.owner_hash, 'admin');
        const asked = rpc(db.name, 'yap_ask_question', [id, claimed.job.lease_id, randomUUID(), 'Lanjut?']);
        assert.equal(asked.ok, true, JSON.stringify(asked));
        const questionId = asked.job.clarifications[0].id;
        const answered = rpc(db.name, 'yap_answer_question', [id, 'admin', questionId, 'Ya']);
        assert.equal(answered.ok, true);
        assert.equal(answered.job.status, 'queued');
      });
    });
  }
});

test('PostgreSQL lokal tersedia untuk tes Supabase', async (t) => {
  if (!PG_AVAILABLE) {
    t.skip('PostgreSQL tidak tersedia di VM ini (psql atau database yap_cloud_test1/2 tidak dapat dihubungi). Jalankan setup di server/supabase/README.md, lalu ulangi npm test — tes ini TIDAK boleh diklaim lolos bila di-skip.');
  }
});
