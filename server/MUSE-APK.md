# Build APK lewat Muse Secure VM

Tombol panah **Kirim** mengirim tugas ke gateway dan mengecek hasil setiap lima detik. Tombol **Unduh APK** menyimpan file melalui pemilih file Android setelah ukuran dan SHA-256 cocok dengan status tugas.

## Menjalankan gateway

Build Android terbaru otomatis memakai gateway YAP bawaan dan membuat identitas acak yang disimpan terenkripsi pada instalasi pertama. Aktifkan `YAP_PUBLIC_BUILDS=1` untuk menerima pengguna baru langsung tanpa konfigurasi manual. Identitas tersebut hanya dapat mengakses tugas milik instalasi itu. Token worker tetap diperlukan untuk klaim, pertanyaan, dan unggah APK. Pengaturan gateway khusus dan token pengelola tetap didukung. Gateway dan hook Muse perlu aktif dan dapat dijangkau internet.

Jalankan `npm start` dari folder `server`. Isi `YAP_ACCESS_TOKEN` dan `YAP_WORKER_TOKEN` dengan dua token acak berbeda, masing-masing minimal 32 karakter, melalui `.env` atau secret pada host. Masukkan URL dasar HTTPS dan token Android di **Pengaturan AI**. Simpan token worker hanya di pengaturan konektor atau secret worker Muse. Jalur APK ini menggunakan hook Muse AI.

Antrean dan APK disimpan di `YAP_BUILD_DIR` (default `.build-jobs`). Gunakan satu proses Node dengan disk persisten. Route antrean dilayani oleh `local.mjs` dan memerlukan host atau tunnel HTTPS yang mengarah ke proses itu. Konfigurasi Vercel yang ada hanya melayani generator web. Pindahkan folder antrean ke disk persisten jika host mempunyai filesystem sementara.

## Keluar dari YAP saat build berjalan

Gateway dan Muse menangani build di luar proses Android. YAP menyimpan tugas terakhir sebelum mengirimnya. Setelah dibuka ulang dan masuk ke Chat Studio, YAP mengambil status tugas yang sama, termasuk APK yang sudah siap. Membuka aplikasi kembali tidak membuat tugas kedua. Untuk melanjutkan tugas lama, gunakan alamat gateway yang sama.

## Handoff ke Betakaroten

Ganti `BASE` dengan URL HTTPS gateway yang sudah aktif. Pakai interval 30 detik yang sudah diuji. Ambil `GET BASE/api/build/signal`. Saat ada tugas, respons publik memuat `pending: true` dan `wakeId` yang opak. Bangunkan sesi untuk setiap `wakeId` baru dan simpan ID tersebut setelah berhasil membangunkan sesi. Reset penanda saat `pending: false`. Jawaban pengguna memberi ID sinyal baru sehingga hook bisa melanjutkan walau tidak sempat melihat sinyal false. Abaikan respons selain HTTP 200 atau format yang tidak valid.

### Skrip yang dipakai di Secure VM

Clone atau update repo YAP di VM. Jalankan `bash server/muse/check-signal.sh --json` dari hook, dengan `YAP_BASE_URL` sebagai origin HTTPS gateway. Pertahankan mekanisme wake yang sudah terbukti di hook Muse. Skrip sinyal tidak membutuhkan token. Jika skrip keluar dengan status selain nol, jangan membangunkan sesi.

Di sesi kerja, isi `YAP_WORKER_TOKEN` dari secret store. Skrip berikut mengharapkan token URL-safe, misalnya hasil `openssl rand -hex 32`. Tetapkan `APKSIGNER` ke path alat SDK jika belum ada di PATH.

```bash
bash server/muse/worker.sh claim
bash server/muse/worker.sh question "$id" "$lease" "Riwayatnya direset setiap hari atau setiap minggu?"
bash server/muse/worker.sh result "$id" "$lease" ./app.apk
bash server/muse/worker.sh fail "$id" "$lease"
```

Klaim kosong menghasilkan output kosong. Gunakan `result` setelah APK berhasil ditandatangani, atau `fail` bila build gagal. Helper memverifikasi tanda tangan APK sebelum mengirim hasil.

Instruksi untuk sesi yang dibangunkan:

Sebelum mengerjakan setiap tugas, baca [panduan kualitas APK](muse/skills/yap-apk-quality/SKILL.md) dari checkout YAP. Tambahkan langkah itu ke prompt hook yang sudah aktif dan pertahankan helper konektor yang berfungsi di Secure VM.

> Ambil tugas dengan POST /api/build/claim memakai Bearer token worker dari koneksi aman. Jika status 204, antrean kosong. Respons 200 membawa id, prompt, dan lease. Gunakan prompt sebagai spesifikasi aplikasi. Buat satu index.html mandiri, lalu bungkus menjadi APK dengan alat Android SDK yang sudah berhasil dipakai di Secure VM. Verifikasi APK dengan apksigner. Unggah biner APK dengan PUT /api/build/{id}/result, Content-Type application/vnd.android.package-archive, Authorization Bearer token worker, dan X-YAP-Lease berisi lease klaim. Batas APK 32 MiB. Jika build gagal, kirim PUT /api/build/{id}/fail dengan token dan lease yang sama. Jangan tulis token di log, prompt, atau berkas hasil. Setelah menyelesaikan tugas, ambil klaim berikutnya sampai respons 204.

Klaim berlaku sepuluh menit. Klaim kedaluwarsa bisa diambil ulang. Hasil dari klaim lama ditolak. Untuk demo pertama, kirim satu tugas dan cek bahwa hook kembali melihat sinyal false setelah klaim.

### Tanya pengguna dari tugas Muse

Klaim juga membawa `context`, riwayat pesan sebelumnya dan ID hasil terdahulu. Gunakan untuk revisi di percakapan yang sama. Untuk `responseMode: chat`, jika pengguna sedang berdiskusi atau menanyakan sesuatu, balas melalui `PUT /api/build/{id}/message` dengan JSON `{"message":"..."}`, token worker dan lease aktif. Batas balasan 6000 karakter, status akhirnya `responded`. Mode `apk` menandai klien lama yang menerima hasil APK atau klarifikasi. Buat APK ketika permintaan membutuhkan pembuatan atau revisi aplikasi. Simpan sumber tiap tugas di direktori job agar revisi berikutnya dapat melanjutkannya. Sapaan singkat ditangani lokal oleh YAP dan ditandai sebagai balasan YAP.

Untuk APK WebView, sediakan satu `assets/index.html` mandiri dengan CSS dan JavaScript inline. Gateway melayani HTML tersebut melalui `GET /api/build/{id}/preview` dengan autentikasi pengguna yang sama. YAP memuatnya di WebView terisolasi untuk preview dan menampilkan HTML-nya di tab Code. APK terdahulu juga bisa dipratinjau jika memiliki aset ini. APK native tanpa aset HTML tetap bisa diunduh.

Tentukan nama aplikasi yang sesuai fungsi, tulis pada label Android serta judul HTML, dan sertakan ikon launcher yang relevan dalam resource APK. Gunakan ikon kompatibel minSdk dan ikon adaptive bila didukung toolchain. Periksa label dan referensi ikon dengan `aapt2 dump badging`. Sebelum mengunggah hasil, kirim `PUT /api/build/{id}/metadata` dengan JSON `{"appName":"Nama Aplikasi"}`, token worker dan lease aktif. Nama maksimal 80 karakter. YAP akan memakai nama ini untuk kartu hasil dan nama file unduhan.

Jika keputusan penting belum jelas, kirim `PUT /api/build/{id}/question` memakai kredensial worker yang sama dengan klaim, header `X-YAP-Lease`, dan JSON `{"question":"..." }` (maksimal 600 karakter). Setelah respons sukses, tugas menjadi `awaiting_answer` dan lease berakhir. Selesaikan sesi untuk tugas itu tanpa polling atau mengirim hasil. YAP menampilkan pertanyaan di Chat Studio dan tombol **Kirim** mengirim jawaban melalui `POST /api/build/{id}/answer`. Gateway mengantrekan kembali ID yang sama dan klaim berikutnya membawa lease baru serta `clarifications` berisi pertanyaan dan jawaban. Terapkan seluruh riwayat saat melanjutkan. Pengguna bisa membuka kembali YAP untuk melihat dan menjawab pertanyaan yang masih menunggu.

## Uji demo

1. Dari YAP, masukkan ide kalkulator dan tekan panah **Kirim**.
2. Tunggu status berubah dari **Menunggu Muse mengambil tugas** menjadi **Muse sedang membuat APK**.
3. Pilih **Unduh APK**, simpan file, dan buka dari pengelola file Android untuk memasang dan mencoba aplikasi.
4. Catat waktu mulai hingga tombol unduh muncul dan periksa apakah Muse meminta izin saat upload.

Uji lokal memakai APK kalkulator sungguhan dan memeriksa pengiriman ulang dengan ID yang sama, klaim bersamaan, token berbeda, restart gateway, unggahan, serta integritas unduhan. Uji dari hook Muse pada URL HTTPS perlu dijalankan dengan akun Muse yang sudah terhubung.
