// Live Supabase integration test — SKIPPED unless all three are set:
//   SUPABASE_URL, SUPABASE_SECRET_KEY (service role), YAP_SUPABASE_LIVE_TEST=1
// This test creates a real job and a real Storage object in the live project.
// It is intentionally skipped in CI/local runs: do NOT claim it passed when
// it was skipped. See server/supabase/README.md for the staging checklist.
import assert from 'node:assert/strict';
import test from 'node:test';
import { randomUUID } from 'node:crypto';
import { zipSync, strToU8 } from 'fflate';
import { createSupabaseRepository } from '../lib/build-repository-supabase.mjs';
import { sha256Hex } from '../lib/build-service.mjs';

const LIVE = Boolean(
  process.env.SUPABASE_URL && process.env.SUPABASE_SECRET_KEY && process.env.YAP_SUPABASE_LIVE_TEST === '1',
);

test('live: submit -> claim -> prepare -> upload -> finalize -> download', { skip: !LIVE }, async () => {
  const repo = createSupabaseRepository({
    supabaseUrl: process.env.SUPABASE_URL,
    serviceRoleKey: process.env.SUPABASE_SECRET_KEY,
  });
  const id = randomUUID();
  const apk = Buffer.from(zipSync({
    'AndroidManifest.xml': strToU8('<manifest package="com.example.test"/>'),
    'classes.dex': strToU8('dex\n035\0'),
    'assets/index.html': strToU8('<html><body>live test</body></html>'),
  }));
  const preview = '<html><body>live test</body></html>';

  const created = await repo.create({
    id, ownerHash: 'live-test-owner', prompt: 'Live test kalkulator', context: '', responseMode: 'apk',
  });
  assert.equal(created.created, true);
  const claimed = await repo.claim({ claimRequestId: randomUUID() });
  assert.equal(claimed.job.id, id);
  const lease = claimed.job.lease;

  const prepared = await repo.prepareArtifact({
    jobId: id, lease, operationId: randomUUID(),
    kind: 'apk', size: apk.length, sha256: sha256Hex(apk),
  });
  assert.ok(prepared.artifact.uploadUrl);
  // Direct upload to the signed URL (standard method for this small fixture).
  const upload = await fetch(prepared.artifact.uploadUrl, { method: 'PUT', body: apk });
  assert.ok(upload.ok, `upload failed: ${upload.status}`);

  const finalized = await repo.finalize({
    jobId: id, lease, operationId: randomUUID(), artifactId: prepared.artifact.id,
    previewHtml: preview, verification: { apksigner: 'verified', note: 'live-test fixture, not a real signed APK' },
  });
  assert.equal(finalized.job.status, 'ready');
  assert.equal(finalized.job.sha256, sha256Hex(apk));

  const download = await repo.createSignedDownloadUrl({ jobId: id, ownerHash: 'live-test-owner' });
  assert.ok(download.url);
  const bytes = Buffer.from(await (await fetch(download.url)).arrayBuffer());
  assert.equal(sha256Hex(bytes), sha256Hex(apk));
  // NOTE: the live job/artifact is left in the project on purpose (72h
  // retention). Delete it from the Supabase dashboard if the project is
  // reused for a clean demo.
});
