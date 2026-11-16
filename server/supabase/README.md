# YAP cloud backend — Vercel + Supabase

Status per 17 November 2026: gateway produksi `https://yap-gateway.vercel.app` sudah dideploy pada Vercel Hobby, Supabase sudah dimigrasi, dan Android `main` sudah memakai origin cloud serta signed download. Source integrasi `d166336` ada pada main kedua repo. Suite backend: 99 tes, 96 lulus, 3 opt-in dilewati; tes live terpisah telah membuktikan submit/claim/clarification/upload/finalize/preview/download dengan fixture dan TUS 32 MiB. Ini bukan bukti seluruh build Muse berhasil: tes aplikasi sungguhan sedang berlangsung, pemulihan VM/lease dan instalasi APK tetap harus diverifikasi. Bagian daftar staging di bawah adalah checklist historis, bukan status akses terkini.

Revisi ini memuat perbaikan 9 penghalang + catatan kuota dari review scaffold (lihat `~/workspace/yap-worker/MIGRATION-REVIEW.md`): helper hash pgcrypto yang menyelesaikan schema extension, replay claim yang tidak memberi lease pengganti, deadline renew 30 menit, prepare POST, prepare replay yang menerbitkan ulang kredensial upload, kontrak TUS resmi (chunk 6 MiB, `x-signature`, metadata bucket/object), inspeksi ZIP lewat central directory, urutan cabang adapter biner, owner exact-match + namespace `admin`, dan advisory lock pada create.

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
| `server/api/gateway.mjs`, `server/vercel.json` | Satu dispatcher v2 dengan rewrites; bersama `api/generate.mjs` menjadi dua Function agar sesuai deployment Hobby. |
| `server/supabase/migrations/0001_init.sql` | Tabel `build_jobs`, `build_questions`, `build_artifacts`, `build_events`, `worker_slots`, `usage_buckets`, `build_operations`; index; RLS (anon/authenticated ditolak). |
| `server/supabase/migrations/0002_rpc.sql` | RPC transaksi: create, signal, claim, answer, question, message, metadata, fail, renew, prepare, finalize. |
| `server/supabase/migrations/0003_storage.sql` | Bucket privat `yap-artifacts` (batas 32 MiB), kebijakan Storage. |
| `server/muse/worker-v2.sh` | Helper worker v2: claim (claimRequestId persisten, `reuse`/`resume`), renew, prepare, upload (standar/TUS), finalize dengan `apksigner verify` + hash/size + preview. Token hanya dari env, tidak pernah ditulis ke file/log. |
| `server/test/build-service.test.mjs`, `build-repository-filesystem.test.mjs`, `build-api-v2.test.mjs`, `build-repository-supabase.test.mjs` | Tes kontrak lokal (lihat bawah). |
| `server/test/supabase-postgres.test.mjs` | Tes regresi SQL terhadap PostgreSQL 16 lokal nyata (dua database: pgcrypto di `public` dan di `extensions`); di-skip jelas bila Postgres tidak tersedia. |
| `server/test/http-adapter.test.mjs` | Tes HTTP nyata: `local.mjs` mode v2 + `worker-v2.sh` (prepare POST + finalize), byte APK via adapter, owner, claim replay mati, renew deadline. |
| `server/test/worker-tus.test.mjs` | Tes klien TUS `worker-v2.sh` terhadap endpoint TUS lokal yang merekam request (chunk 6 MiB, `x-signature`, metadata, resume, 32 MiB, 401). |
| `server/test/artifact-storage.test.mjs` | Tes ZIP terbatas: ZIP bomb, duplicate name, path traversal, preview. |
| `server/test/fixtures/test-app.apk` + `build-fixture-apk.sh` | APK fixture nyata (javac → d8 → aapt2 → zipalign → apksigner, keystore tes di /tmp): package `com.example.fixture`, label "Fixture Uji", `assets/index.html` berisi `preview-marker-7f3a9`. |
| `server/test/setup-local-pg.sh` | Setup PostgreSQL lokal yang idempotent (cluster/role/database/trust loopback) — jalankan ulang setelah VM restart. |
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
- Repository filesystem: retry submit idempoten (payload sama → 200, berubah → 409), 10 claim bersamaan → tepat 1 pemenang, replay `claimRequestId`, replay claim lama setelah reclaim → `job null` (tidak pernah memberi lease pengganti), lease usang ditolak lalu reclaim, question/answer/requeue, maks 5 klarifikasi, message hanya untuk `chat`, prepare → store → finalize dengan hash/size/preview yang cocok, finalize dengan preview tidak cocok ditolak, renew memperpanjang lease yang sama, renew di-cap di deadline attempt 30 menit (29 → diperpanjang ke deadline, setelah deadline → 409 `attempt_expired`), owner exact-match (guest lain/owner kosong ditolak, `admin` boleh).
- Router v2: capabilities, auth guest/worker, isolasi owner (404), claim/resume, renew, 426 untuk biner legacy, alur prepare → finalize → preview, `/download` pada backend filesystem → 501 (tidak ada signed URL), `/apk` legacy tetap melayani file pada backend filesystem.
- Repository Supabase dengan `fetch` mock: pemetaan envelope RPC, error bisnis 409, transport 401/502, prepare memilih TUS untuk APK > 6 MiB, prepare replay menerbitkan ulang kredensial upload untuk object path yang sama (artifact verified / lease usang → 409, tanpa izin menimpa), finalize memverifikasi byte outbound sebelum memanggil RPC finalize, `storeArtifactBytes` menolak upload lewat gateway (426).
- **PostgreSQL nyata (lokal, PG16)** — `supabase-postgres.test.mjs`: migrasi 0001→0002→0003 berjalan bersih pada dua database (pgcrypto di `public` dan di `extensions`), `yap_sha256_hex('abc')` cocok dengan SHA-256 yang dikenal, signal, create idempoten + kuota (5/hari/owner, antrean 20), kuota di bawah submit paralel (10 submit owner sama → tepat 5; 25 owner berbeda → tepat 20), claim + replay setelah reclaim, renew di boundary 29/30/31 menit, question/answer (owner salah → 404, jawab ganda idempoten), prepare replay + finalize (SHA salah ditolak, SHA benar → ready, finalize setelah lease habis ditolak), RLS anon ditolak (tabel + RPC), namespace admin (job admin, admin menjawab job guest).
- **HTTP nyata** — `http-adapter.test.mjs`: `local.mjs` mode v2 di loopback + `worker-v2.sh` asli (claim → prepare POST → finalize), `GET /api/build/{id}/apk` mengembalikan byte APK lengkap dengan Content-Length/size/hash yang cocok, owner via HTTP (guest lain 404, tanpa auth 401, job admin tak terbaca guest), claim replay mati via HTTP (200, `job null`), renew deadline via HTTP.
- **TUS** — `worker-tus.test.mjs`: endpoint TUS lokal merekam bahwa klien mengirim chunk 6 MiB, token di header `x-signature` (tanpa Authorization Bearer), metadata `bucketName`/`objectName`/`contentType`, resume dari offset server, upload 32 MiB penuh, dan 401 pada PATCH menggagalkan upload.
- **ZIP terbatas** — `artifact-storage.test.mjs`: arsip zeros yang mengembang ke 300 MiB ditolak tanpa full unzip, duplicate name dan path traversal ditolak, preview `assets/index.html` hanya diekstrak setelah batas ukuran dicek.

### Menjalankan tes Postgres lokal

Tes Postgres butuh PostgreSQL 16 terinstal dan database/role tes di loopback.
Instalasi sistem di VM ini **tidak bertahan setelah restart** — setelah restart,
instal ulang lalu jalankan setup, baru jalankan tes:

```bash
apt-get update && apt-get install -y postgresql   # Ubuntu noble -> PG16 (lambat via proxy, beri waktu)
bash server/test/setup-local-pg.sh                # cluster/role/database/trust loopback (idempotent)
cd server && npm test                             # supabase-postgres.test.mjs jalan; bila Postgres
                                                  # tidak tersedia, file itu di-skip dengan pesan jelas
```

Variabel yang bisa diubah: `PG_VERSION` untuk skrip setup; `YAP_TEST_PGHOST`,
`YAP_TEST_PGPORT`, `YAP_TEST_PGUSER` untuk tes. Semua koneksi loopback lokal,
tanpa kredensial proyek Supabase user.

## Checklist staging historis dan batas pengujian

Migrasi Supabase nyata, routing Vercel, signed upload/download, dan TUS 32 MiB pada daftar berikut sudah diuji dengan fixture pada 16 November. Concurrency lintas instance, rate limit per menit, budget/retensi, signing vault, dan ketahanan worker tetap perlu pemeriksaan tersendiri. Untuk build sungguhan, periksa isi APK akhir dan instalasi: tanda tangan valid saja tidak cukup. Lihat `MUSE-APK.md` untuk kontrak worker yang berlaku.

1. **Migrasi SQL terhadap Supabase nyata.** Migrasi sudah diuji terhadap PostgreSQL 16 lokal nyata pada dua layout pgcrypto (`public` dan `extensions`) — termasuk claim/replay/renew/kuota/RLS — tapi belum pernah dijalankan di proyek Supabase user (versi Postgres dan perilaku Storage Supabase bisa berbeda). Jalankan 0001→0002→0003 di SQL Editor staging, lalu periksa tidak ada error dan RPC `yap_signal`/`yap_claim_job` merespons.
2. **Alur Storage nyata.** Klien TUS sudah diuji terhadap endpoint TUS lokal yang merekam request (chunk 6 MiB, `x-signature`, metadata, resume, 32 MiB), dan bentuk respons signed upload URL REST sudah diverifikasi dari dokumentasi + SDK Supabase — tapi signed upload URL, TUS, dan signed download URL belum pernah melawan Storage Supabase nyata, dan verifikasi outbound 32 MiB di Vercel (timeout/durasi function) belum diuji. Uji dengan fixture APK 32 MiB di staging.
3. **Routing Vercel nyata.** File `api/build/[id].mjs` dan kawan-kawan mengikuti konvensi file-based routing Vercel, tapi dispatch aktual (`/api/build`, `/signal`, `/claim`, `/{id}/...`) belum dibuktikan di deployment. Uji di deployment preview: `GET /api/capabilities`, `GET /api/build/signal`.
4. **Concurrency lintas instance.** Tes claim ganda lokal hanya satu proses; kuota create sudah diuji paralel melawan Postgres lokal (advisory lock). Di Supabase, atomicity bergantung pada RPC + `FOR UPDATE SKIP LOCKED` — uji dengan dua klien bersamaan melawan database staging (§19 rancangan).
5. **Quota.** `yap_create_job` menegakkan antrean maks 20 dan 5 build/hari per owner di SQL (dengan advisory lock, diuji paralel lokal); `usage_buckets` ada sebagai tabel, tapi rate-limit per menit dan budget Storage 600 MiB belum diimplementasikan di API (ditandai sebagai pekerjaan staging).
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
