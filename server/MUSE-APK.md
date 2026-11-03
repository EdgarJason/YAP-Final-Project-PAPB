# Build APK lewat Muse Secure VM

Tombol **Generate APK** mengirim tugas ke gateway dan mengecek hasil setiap lima detik. Tombol **Unduh APK** menyimpan file melalui pemilih file Android setelah ukuran dan SHA-256 cocok dengan status tugas. Pembuatan web langsung lewat Muse Spark tetap tersedia melalui tombol kirim di kolom chat.

## Menjalankan gateway

Jalankan `npm start` dari folder `server`. Isi `YAP_ACCESS_TOKEN` dan `YAP_WORKER_TOKEN` dengan dua token acak berbeda, masing-masing minimal 32 karakter, melalui `.env` atau secret pada host. Masukkan URL dasar HTTPS dan token Android di **Pengaturan AI**. Simpan token worker hanya di pengaturan konektor atau secret worker Muse. `META_API_KEY` hanya diperlukan untuk pembuatan web langsung.

Antrean dan APK disimpan di `YAP_BUILD_DIR` (default `.build-jobs`). Gunakan satu proses Node dengan disk persisten. Route antrean dilayani oleh `local.mjs` dan memerlukan host atau tunnel HTTPS yang mengarah ke proses itu. Konfigurasi Vercel yang ada hanya melayani generator web. Pindahkan folder antrean ke disk persisten jika host mempunyai filesystem sementara.

## Handoff ke Betakaroten

Ganti `BASE` dengan URL HTTPS gateway yang sudah aktif. Pakai interval 30 detik yang sudah diuji. Adaptasi hook yang sebelumnya membaca file lokal agar mengambil `GET BASE/api/build/signal`. Respons publik hanya `{"pending":true}` atau `{"pending":false}`. Perlakukan respons selain HTTP 200 sebagai kegagalan pengecekan. Saat sinyal true, gunakan mekanisme wake dari hook yang sudah diuji.

Instruksi untuk sesi yang dibangunkan:

> Ambil tugas dengan POST /api/build/claim memakai Bearer token worker dari koneksi aman. Jika status 204, antrean kosong. Respons 200 membawa id, prompt, dan lease. Gunakan prompt sebagai spesifikasi aplikasi. Buat satu index.html mandiri, lalu bungkus menjadi APK dengan alat Android SDK yang sudah berhasil dipakai di Secure VM. Verifikasi APK dengan apksigner. Unggah biner APK dengan PUT /api/build/{id}/result, Content-Type application/vnd.android.package-archive, Authorization Bearer token worker, dan X-YAP-Lease berisi lease klaim. Batas APK 32 MiB. Jika build gagal, kirim PUT /api/build/{id}/fail dengan token dan lease yang sama. Jangan tulis token di log, prompt, atau berkas hasil. Setelah menyelesaikan tugas, ambil klaim berikutnya sampai respons 204.

Klaim berlaku sepuluh menit. Klaim kedaluwarsa bisa diambil ulang. Hasil dari klaim lama ditolak. Untuk demo pertama, kirim satu tugas dan cek bahwa hook kembali melihat sinyal false setelah klaim.

## Uji demo

1. Dari YAP, masukkan ide kalkulator dan tekan **Generate APK**.
2. Tunggu status berubah dari **Menunggu Muse mengambil tugas** menjadi **Muse sedang membuat APK**.
3. Pilih **Unduh APK**, simpan file, dan buka dari pengelola file Android untuk memasang dan mencoba aplikasi.
4. Catat waktu mulai hingga tombol unduh muncul dan periksa apakah Muse meminta izin saat upload.

Uji lokal memakai APK kalkulator sungguhan dan memeriksa pengiriman ulang dengan ID yang sama, klaim bersamaan, token berbeda, restart gateway, unggahan, serta integritas unduhan. Uji dari hook Muse pada URL HTTPS perlu dijalankan dengan akun Muse yang sudah terhubung.
