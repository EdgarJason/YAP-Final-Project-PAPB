# YAP cloud backend — Vercel + Supabase (scaffold)

Status: **scaffold di branch lokal `codex/vercel-supabase-plan-implementation` — belum di-deploy, belum ada proyek Vercel/Supabase, belum ada token.** Semua yang bisa diuji lokal sudah diuji; semua yang butuh proyek live ditandai di bawah dan TIDAK diklaim lolos.

Rancangan: `~/workspace/user/files/RANCANGAN-VERCEL-SUPABASE.md` (§6–§9, §15–§16).

## Arsitektur singkat

- Vercel Functions hanya membawa JSON pendek (batas body 4,5 MB). APK **tidak pernah** lewat Vercel: worker mengunggah langsung ke Supabase Storage (PUT standar ≤ 6 MiB, TUS resumable > 6 MiB), Android mengunduh langsung dari Storage lewat signed URL.
- Supabase Postgres adalah sumber kebenaran antrean/lease/klarifikasi. Semua mutasi lewat RPC `SECURITY DEFINER` di `supabase/migrations/` — claim atomik (`FOR UPDATE SKIP LOCKED`), slot satu worker, lease dicek terhadap waktu database, idempotensi via `build_operations` (`claimRequestId`/`operationId`).
- Backend disk lama (`server/build-jobs.mjs`) **tidak diubah** dan tetap menjadi backend `filesystem` untuk rollback dan tes regresi. `server/local.mjs` memakai disk secara default; set `YAP_STORAGE_BACKEND=supabase` untuk menjalankan API v2 dari proses lokal, atau `YAP_API_VERSION=v2` untuk menjalankan API v2 di atas backend filesystem lokal (uji end-to-end kontrak v2 tanpa proyek live — perhatikan: pada mode ini upload biner legacy ke `/result` ditolak 426, sesuai kontrak v2; helper lama `worker.sh` tetap memakai backend default).

## File yang dibuat

| File | Isi |
|---|---|
| `server/lib/build-service.mjs` | Validasi + kontrak bersama (batas: prompt 6000, context 12000, question 600, answer 1200, message 6000, appName 80, APK 32 MiB, klarifikasi maks 5, lease 10 menit, preview 2 MiB / JSON 3 MiB). |
| `server/lib/artifact-storage.mjs` | Verifikasi byte server-side: hash/size, inspeksi ZIP terbatas, ekstraksi `assets/index.html`, object path yang ditentukan server. |
| `server/lib/build-repository-filesystem.mjs` | Repository v2 di atas format disk lama (`{id}.json` + `{id}.apk` + sidecar `{id}.artifacts.json`). |
| `server/lib/build-repository-supabase.mjs` | Repository Supabase via REST/RPC (tanpa koneksi Postgres langsung). |
| `server/lib/build-api.mjs` | Router v2 tanpa framework: capabilities, signal, submit/status/answer/download/preview, claim/resume, renew, question/message/metadata/fail, artifacts/prepare, result JSON (biner legacy → 426). |
| `server/lib/vercel-adapter.mjs` | Adapter Node/Vercel → router; pemilihan backend dari env; batas body per route. |
| `server/api/capabilities.mjs`, `server/api/build/...`, `server/api/worker/resume.mjs` | Handler Vercel tipis (satu baris delegasi per file). |
| `server/supabase/migrations/0001_init.sql` | Tabel `build_jobs`, `build_questions`, `build_artifacts`, `build_events`, `worker_slots`, `usage_buckets`, `build_operations`; index; RLS (anon/authenticated ditolak). |
| `server/supabase/migrations/0002_rpc.sql` | RPC transaksi: create, signal, claim, answer, question, message, metadata, fail, renew, prepare, finalize. |
| `server/supabase/migrations/0003_storage.sql` | Bucket privat `yap-artifacts` (batas 32 MiB), kebijakan Storage. |
| `server/muse/worker-v2.sh` | Helper worker v2: claim (claimRequestId persisten, `reuse`/`resume`), renew, prepare, upload (standar/TUS), finalize dengan `apksigner verify` + hash/size + preview. Token hanya dari env, tidak pernah ditulis ke file/log. |
| `server/test/build-service.test.mjs`, `build-repository-filesystem.test.mjs`, `build-api-v2.test.mjs`, `build-repository-supabase.test.mjs` | Tes kontrak lokal (lihat bawah). |
| `server/test/supabase-live.test.mjs` | Tes integrasi live — di-skip tanpa kredensial. |
| `server/vercel.json` | Route baru ditambahkan; `api/generate.mjs` (maxDuration 300) dipertahankan. |
| `server/local.mjs` | Saklar `YAP_STORAGE_BACKEND=supabase` (default filesystem). |

## Environment variables

| Variabel | Keterangan |
|---|---|
| `YAP_STORAGE_BACKEND` | `filesystem` (default) atau `supabase`. Di Vercel, nilai kosong = 503 (tidak ada fallback diam-diam ke disk ephemeral). |
| `SUPABASE_URL` | Origin proyek Supabase, mis. `https://xyz.supabase.co`. |
| `SUPABASE_SECRET_KEY` | Service-role key (alias `SUPABASE_SERVICE_ROLE_KEY` diterima). **Hanya di secret Vercel** — tidak di repo, APK, hook, atau chat. |
| `YAP_WORKER_TOKEN` | Token worker (min. 32 karakter, berbeda dari access token). Di secret Vercel + kartu aman konektor Muse. |
| `YAP_ACCESS_TOKEN` | Token pengelola (min. 32 karakter). |
| `YAP_PUBLIC_BUILDS` | `1` untuk guest otomatis. |
| `YAP_SIGNAL_SECRET` | Opsional; bila diisi, wakeId adalah HMAC atas kandidat + jendela 2 menit. Tanpa ini, wakeId memakai SHA-256 legacy. |

## Yang sudah diuji lokal (`npm test` di `server/`)

- Validasi semua batas, preview setelah JSON escaping, hash kanonik, wakeId.
- Repository filesystem: retry submit idempoten (payload sama → 200, berubah → 409), 10 claim bersamaan → tepat 1 pemenang, replay `claimRequestId`, lease usang ditolak lalu reclaim, question/answer/requeue, maks 5 klarifikasi, message hanya untuk `chat`, prepare → store → finalize dengan hash/size/preview yang cocok, finalize dengan preview tidak cocok ditolak, renew memperpanjang lease yang sama.
- Router v2: capabilities, auth guest/worker, isolasi owner (404), claim/resume, renew, 426 untuk biner legacy, alur prepare → finalize → preview, `/download` pada backend filesystem → 501 (tidak ada signed URL), `/apk` legacy tetap melayani file pada backend filesystem.
- Repository Supabase dengan `fetch` mock: pemetaan envelope RPC, error bisnis 409, transport 401/502, prepare memilih TUS untuk APK > 6 MiB, finalize memverifikasi byte outbound sebelum memanggil RPC finalize, `storeArtifactBytes` menolak upload lewat gateway (426).

## Yang BELUM bisa diuji tanpa proyek Supabase/Vercel live

1. **Migrasi SQL terhadap Postgres nyata.** Sintaks ditulis hati-hati dan mengikuti pola `0001`/`0002`, tapi belum pernah dijalankan (`psql`/`supabase db push` belum tersedia di VM ini untuk proyek live). Jalankan di staging dengan `supabase db push` / SQL editor, lalu periksa tidak ada error dan RPC `yap_signal`/`yap_claim_job` merespons.
2. **Alur Storage nyata.** Signed upload URL, TUS resumable (implementasi bash di `worker-v2.sh` mengikuti spesifikasi TUS, belum pernah melawan endpoint Supabase nyata), signed download URL, dan verifikasi outbound 32 MiB di Vercel (timeout/durasi function). Uji dengan fixture APK 32 MiB di staging.
3. **Routing Vercel nyata.** File `api/build/[id].mjs` dan kawan-kawan mengikuti konvensi file-based routing Vercel, tapi dispatch aktual (`/api/build`, `/signal`, `/claim`, `/{id}/...`) belum dibuktikan di deployment. Uji di deployment preview: `GET /api/capabilities`, `GET /api/build/signal`.
4. **Concurrency lintas instance.** Tes claim ganda lokal hanya satu proses. Di Supabase, atomicity bergantung pada RPC + `FOR UPDATE SKIP LOCKED` — uji dengan dua klien bersamaan melawan database staging (§19 rancangan).
5. **Quota.** `yap_create_job` menegakkan antrean maks 20 dan 5 build/hari per owner di SQL; `usage_buckets` ada sebagai tabel, tapi rate-limit per menit dan budget Storage 600 MiB belum diimplementasikan di API (ditandai sebagai pekerjaan staging).
6. **Hook + worker end-to-end.** `worker-v2.sh` belum pernah claim/prepare/upload/finalize melawan origin live; `check-signal.sh` lama tetap berlaku untuk sinyal publik.

## Langkah staging berikutnya

1. Buat proyek Supabase (Free) + proyek Vercel (Hobby) — butuh akun pemilik; jangan aktifkan layanan berbayar.
2. Terapkan migrasi `0001`–`0003` di proyek staging, buat bucket `yap-artifacts` bila migrasi storage tidak membuatkannya.
3. Isi secret Vercel: `SUPABASE_URL`, `SUPABASE_SECRET_KEY`, `YAP_WORKER_TOKEN`, `YAP_ACCESS_TOKEN`, `YAP_PUBLIC_BUILDS=1`, `YAP_STORAGE_BACKEND=supabase`.
4. Deploy branch ini ke deployment preview, uji `GET /api/capabilities` dan `GET /api/build/signal`.
5. Jalankan `server/test/supabase-live.test.mjs` dengan `YAP_SUPABASE_LIVE_TEST=1` melawan staging (membuat satu job + satu objek Storage nyata).
6. Uji transfer 32 MiB, dua claim bersamaan, lease usang, dan download signed URL dari HP.
7. Baru setelah itu: buat konektor Yap Worker, masukkan token lewat kartu aman, dry run hook, lalu aktifkan satu hook.

## Rollback

- Backend disk tetap ada dan tidak diubah: jalankan gateway lama (`YAP_STORAGE_BACKEND=filesystem` atau `node local.mjs` tanpa variabel itu) dengan antrean `.build-jobs` yang sama.
- **Catatan:** rollback data cloud → disk (importer) belum ada — sesuai rancangan §18, jangan janjikan rollback data setelah cloud menerima tugas sebelum importer itu dibuat dan diuji. Untuk demo, rencanakan drain: selesaikan tugas lama di backend disk sebelum cutover.
