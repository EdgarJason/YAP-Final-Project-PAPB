import base64
import os

images = {
    "1": "extracted_images/screen_1_Welcome_screen.png",
    "2": "extracted_images/screen_2_Registe_screen.png",
    "3": "extracted_images/screen_3_Profile_screen.png",
    "4": "extracted_images/screen_4_OTP_screen.png",
    "5": "extracted_images/screen_5_Login_screen.png",
    "6": "extracted_images/screen_6_Forgot_password_screen.png",
    "7": "extracted_images/screen_7_Delete_account_screen.png",
    "8": "extracted_images/screen_8_Chat_studio_screen.png",
    "9": "extracted_images/screen_9_Chat_history_screen.png",
    "10": "extracted_images/screen_10_Change_password_screen.png",
    "11": "extracted_images/screen_11_Change_name_screen.png",
    "12": "extracted_images/screen_12_Change_email_screen.png",
    "13": "extracted_images/screen_13_API_settings_screen.png",
    "14": "extracted_images/screen_14_All_set_screen.png",
}

b64_dict = {}
for k, path in images.items():
    if os.path.exists(path):
        with open(path, "rb") as f:
            b64_dict[k] = base64.b64encode(f.read()).decode("utf-8")

screens_info = [
    ("1", "Welcome Screen", "Layar selamat datang yang pertama kali ditampilkan kepada pengguna. Menampilkan identitas visual YAP, maskot speech-bubble yang ramah, serta tombol navigasi utama menuju Log In dan Sign Up."),
    ("2", "Register Screen", "Layar pendaftaran akun baru. Menyediakan formulir input nama lengkap, email, kata sandi, dan konfirmasi kata sandi dengan validasi interaktif sebelum akun dibuat."),
    ("3", "Profile Screen", "Layar profil akun pengguna. Menampilkan informasi akun, avatar pengguna, opsi navigasi ubah nama, ganti email, perbarui kata sandi, tombol logout, serta opsi hapus akun."),
    ("4", "OTP Verification Screen", "Layar verifikasi keamanan berupa kode One-Time Password empat digit yang dikirimkan ke email pengguna untuk keperluan autentikasi ganda atau pemulihan sandi."),
    ("5", "Login Screen", "Layar autentikasi masuk pengguna terdaftar. Memuat kolom isian email, kata sandi, opsi Remember Me, tautan bantuan lupa kata sandi, serta tombol masuk."),
    ("6", "Forgot Password Screen", "Layar permintaan pemulihan kata sandi. Pengguna memasukkan alamat email terdaftar untuk menerima tautan atau kode reset kata sandi."),
    ("7", "Delete Account Screen", "Layar konfirmasi penghapusan akun permanen. Dilengkapi peringatan konsekuensi kehilangan data dan konfirmasi sandi untuk mencegah tindakan yang tidak disengaja."),
    ("8", "Chat Studio Screen", "Layar kerja utama studio AI. Menyediakan bilah drawer ruang kerja, kolom masukan instruksi atau ide web, tombol pengiriman prompt, dan tabs navigasi antarmuka studio."),
    ("9", "Chat History Screen", "Layar arsip percakapan terdahulu. Memuat daftar riwayat interaksi pengguna dengan AI, fitur pencarian riwayat percakapan, serta pintasan memulai sesi baru."),
    ("10", "Change Password Screen", "Layar pembaruan kata sandi pengguna. Memerlukan input kata sandi saat ini, kata sandi baru, dan konfirmasi kata sandi baru untuk menjaga integritas akun."),
    ("11", "Change Name Screen", "Layar penyuntingan nama profil pengguna. Menyediakan kolom pembaruan nama tampilan yang langsung tersimpan ke sistem profil."),
    ("12", "Change Email Screen", "Layar pembaruan alamat email akun. Memfasilitasi pergantian email dengan protokol verifikasi keamanan ke email baru."),
    ("13", "API Settings Screen", "Layar konfigurasi penyedia layanan kecerdasan buatan. Memungkinkan pengguna mengatur kunci API dan endpoint model guna menghubungkan studio dengan AI pihak ketiga."),
    ("14", "All Set Screen", "Layar konfirmasi penyelesaian registrasi dan verifikasi. Menandakan bahwa akun telah terkonfigurasi secara penuh dan pengguna siap menjelajahi studio pembuatan web.")
]

js_code = """/**
 * SCRIPT OTOMATIS PEMBARUAN LAPORAN TUGAS 1 PAPB KE GOOGLE DOCS (STANDAR JARNIK)
 * Buka dokumen Google Docs -> Menu Ekstensi -> Apps Script -> Paste kode ini -> Klik Run (Jalankan)
 */

function updateLaporanGDocs() {
  var doc = DocumentApp.getActiveDocument();
  var body = doc.getBody();
  
  // 1. Bersihkan dokumen
  body.clear();
  
  // 2. Set Margin 1 Inch (72 pt)
  body.setMarginTop(72);
  body.setMarginBottom(72);
  body.setMarginLeft(72);
  body.setMarginRight(72);
  
  // Helper styling
  function styleP(p, align, size, bold, spaceAfter, spaceBefore) {
    p.setAlignment(align || DocumentApp.HorizontalAlignment.JUSTIFY);
    var text = p.editAsText();
    text.setFontFamily("Times New Roman");
    text.setFontSize(size || 12);
    text.setBold(bold || false);
    text.setItalic(false);
    text.setForegroundColor("#000000");
    p.setSpacingBefore(spaceBefore !== undefined ? spaceBefore : 0);
    p.setSpacingAfter(spaceAfter !== undefined ? spaceAfter : 4);
    p.setLineSpacing(1.15);
    return p;
  }
  
  // ==========================================
  // JUDUL & IDENTITAS (COVER)
  // ==========================================
  var p1 = body.appendParagraph("TUGAS 1 - USER INTERFACE");
  styleP(p1, DocumentApp.HorizontalAlignment.CENTER, 15, true, 4, 6);
  
  var p2 = body.appendParagraph("YAP MOBILE AI STUDIO: PERANCANGAN DAN SLICING ANTARMUKA PENGGUNA BERBASIS JETPACK COMPOSE");
  styleP(p2, DocumentApp.HorizontalAlignment.CENTER, 12, true, 4, 2);
  
  var p3 = body.appendParagraph("Mata Kuliah: Pemrograman Aplikasi Perangkat Bergerak / 2026");
  styleP(p3, DocumentApp.HorizontalAlignment.CENTER, 11, true, 14, 2);
  
  var p4 = body.appendParagraph("Disusun Oleh:\\nKELOMPOK 2");
  styleP(p4, DocumentApp.HorizontalAlignment.CENTER, 11, true, 6, 2);
  
  // Tabel Anggota
  var tAnggota = body.appendTable([
    ["Nama Anggota", "NIM", "Peran dan Tanggung Jawab"],
    ["Edgar Jason Husin", "245150201111047", "UI Designer dan Frontend Slicing"],
    ["Joshua Nathanael Purba", "245150201111001", "Frontend Slicing dan Interaktivitas"],
    ["Tobias Andra Valentino", "245150200111076", "Koordinator Tim dan Slicing Compose"],
    ["Willy Gregory", "245150201111089", "Validasi Desain dan Pengujian UI"]
  ]);
  formatTable(tAnggota, [170, 110, 180]);
  
  body.appendParagraph("").setSpacingAfter(10);
  
  // ==========================================
  // BAGIAN 1: INFORMASI DAN TAUTAN PROYEK
  // ==========================================
  var h1 = body.appendParagraph("1. Informasi dan Tautan Proyek");
  styleP(h1, DocumentApp.HorizontalAlignment.LEFT, 13, true, 6, 14);
  
  var pInfo = body.appendParagraph("Seluruh sumber daya pengembangan antarmuka pengguna aplikasi YAP Mobile AI Studio telah diunggah dan dapat diakses melalui tautan repositori kode dan desain berikut:");
  styleP(pInfo, DocumentApp.HorizontalAlignment.JUSTIFY, 12, false, 4, 0);
  
  var tLinks = body.appendTable([
    ["Komponen Proyek", "Tautan Resmi", "Keterangan"],
    ["Repositori GitHub", "https://github.com/EdgarJason/YAP-Final-Project-PAPB", "Kode Sumber Jetpack Compose"],
    ["Rancangan Figma", "https://www.figma.com/design/odHg0ZJ4Vu64v4RRieNj7U/YAP---Final-Project-PAPB?node-id=20-379&t=NHgl5ypcp4RNecBd-1", "Master Desain UI dan Alur Navigasi"]
  ]);
  formatTable(tLinks, [130, 210, 120]);
  
  // ==========================================
  // BAGIAN 2: OVERVIEW PROYEK
  // ==========================================
  var h2 = body.appendParagraph("2. Overview Proyek");
  styleP(h2, DocumentApp.HorizontalAlignment.LEFT, 13, true, 6, 14);
  
  var pOver1 = body.appendParagraph("YAP Mobile AI Studio merupakan aplikasi perangkat bergerak berbasis sistem operasi Android yang dirancang untuk membantu pengguna menghasilkan prototipe aplikasi berbasis web dengan memanfaatkan kecerdasan buatan atau Artificial Intelligence. Aplikasi ini memungkinkan pengguna menyampaikan kebutuhan dan spesifikasi pembuatan aplikasi secara interaktif melalui percakapan alami, kemudian menelaah kode program yang dihasilkan untuk menyempurnakan aplikasi web sesuai kebutuhan pengguna. Target pengguna aplikasi ini mencakup mahasiswa, pelajar, pengembang perangkat lunak tingkat awal, serta masyarakat umum yang memerlukan sarana perancangan web cepat tanpa keharusan menggunakan komputer meja secara terus-menerus.");
  styleP(pOver1, DocumentApp.HorizontalAlignment.JUSTIFY, 12, false, 4, 0);
  
  var pOver2 = body.appendParagraph("Arsitektur antarmuka YAP Mobile AI Studio dibangun dengan mengedepankan beberapa fitur inti yang saling terintegrasi:");
  styleP(pOver2, DocumentApp.HorizontalAlignment.JUSTIFY, 12, false, 4, 0);
  
  var fiturs = [
    "1. Autentikasi Pengguna: Mengelola pendaftaran akun baru, proses masuk, verifikasi kode One-Time Password, serta pemulihan kata sandi.",
    "2. Profil Pengguna: Memfasilitasi pengelolaan informasi akun seperti pembaruan nama pengguna, alamat email, kata sandi, serta penonaktifan akun.",
    "3. Chat AI Studio: Ruang kerja utama komunikasi pengguna dengan mesin kecerdasan buatan guna menyampaikan instruksi pembuatan dan pembaruan kode aplikasi.",
    "4. Code Inspector: Penampil kode program hasil generasi AI yang memudahkan pengguna memeriksa struktur kode HTML, CSS, maupun JavaScript.",
    "5. Pengaturan API: Konfigurasi antarmuka pemrograman aplikasi pihak ketiga untuk menghubungkan aplikasi dengan penyedia model kecerdasan buatan.",
    "6. Pratinjau Web: Fitur penampil tampilan web secara langsung sehingga hasil rancangan dapat dievaluasi sebelum diunduh atau dipublikasikan.",
    "7. Riwayat Percakapan: Penyimpanan arsip instruksi dan percakapan lampau agar proses iterasi pengembangan dapat dilanjutkan kapan saja."
  ];
  for (var i = 0; i < fiturs.length; i++) {
    var pF = body.appendParagraph(fiturs[i]);
    styleP(pF, DocumentApp.HorizontalAlignment.LEFT, 12, false, 2, 1);
    pF.setIndentLeft(20);
    pF.setIndentFirstLine(-15);
  }
  
  var pOver3 = body.appendParagraph("Integrasi seluruh fungsi tersebut ke dalam satu aplikasi bergerak memberikan alur kerja perancangan web yang lebih fleksibel, portabel, dan mudah dipahami oleh pengguna pemula.");
  styleP(pOver3, DocumentApp.HorizontalAlignment.JUSTIFY, 12, false, 6, 4);
  
  // ==========================================
  // BAGIAN 3: TAMPILAN ANTARMUKA HASIL SLICING
  // ==========================================
  var h3 = body.appendParagraph("3. Tampilan Antarmuka Pengguna Hasil Slicing");
  styleP(h3, DocumentApp.HorizontalAlignment.LEFT, 13, true, 6, 14);
  
  var pScDesc = body.appendParagraph("Seluruh rancangan antarmuka pengguna pada Figma telah diimplementasikan ke dalam komponen Jetpack Compose pada Android Studio. Tabel berikut menyajikan tangkapan layar hasil slicing beserta penjelasan fungsi tiap antarmuka:");
  styleP(pScDesc, DocumentApp.HorizontalAlignment.JUSTIFY, 12, false, 4, 0);
  
  // Buat Tabel Screens
  var tScreens = body.appendTable();
  var headerRow = tScreens.appendTableRow();
  headerRow.appendTableCell("No.");
  headerRow.appendTableCell("Tangkapan Layar");
  headerRow.appendTableCell("Keterangan dan Deskripsi Antarmuka");
  
  // Data gambar dan deskripsi
"""

# Append screens data and image base64 injection
js_code += f"\n  var imgData = {repr(b64_dict)};\n"
js_code += f"  var screensList = {repr(screens_info)};\n\n"

js_code += """  for (var s = 0; s < screensList.length; s++) {
    var sc = screensList[s];
    var row = tScreens.appendTableRow();
    
    // Cell No
    var cNo = row.appendTableCell(sc[0]);
    cNo.getChild(0).asParagraph().setAlignment(DocumentApp.HorizontalAlignment.CENTER);
    cNo.editAsText().setFontFamily("Times New Roman").setFontSize(10).setForegroundColor("#000000");
    
    // Cell Image
    var cImg = row.appendTableCell();
    cImg.getChild(0).asParagraph().setAlignment(DocumentApp.HorizontalAlignment.CENTER);
    if (imgData[sc[0]]) {
      try {
        var blob = Utilities.newBlob(Utilities.base64Decode(imgData[sc[0]]), "image/png", "screen_" + sc[0] + ".png");
        var img = cImg.getChild(0).asParagraph().appendInlineImage(blob);
        img.setWidth(140);
        img.setHeight(300);
      } catch (e) {
        cImg.getChild(0).asParagraph().appendText("[Gambar Screen " + sc[0] + "]");
      }
    }
    
    // Cell Desc
    var cDesc = row.appendTableCell();
    var pTit = cDesc.getChild(0).asParagraph();
    pTit.setText(sc[1]);
    styleP(pTit, DocumentApp.HorizontalAlignment.LEFT, 10.5, true, 3, 2);
    
    var pD = cDesc.appendParagraph(sc[2]);
    styleP(pD, DocumentApp.HorizontalAlignment.JUSTIFY, 10, false, 2, 0);
  }
  
  formatTable(tScreens, [35, 175, 250]);
  
  // ==========================================
  // BAGIAN 4: CATATAN KOMPONEN DAN NAVIGASI
  // ==========================================
  var h4 = body.appendParagraph("4. Catatan Komponen dan Navigasi Antarmuka");
  styleP(h4, DocumentApp.HorizontalAlignment.LEFT, 13, true, 6, 14);
  
  var pNav = body.appendParagraph("Komponen navigasi seperti Modal Navigation Drawer, pop-up dialog konfirmasi, dan bilah input dirancang secara reusable pada paket komponen Jetpack Compose. Implementasi detail kode interaksi dan animasi responsif dapat ditinjau langsung pada berkas repositori GitHub, serta visualisasi alur perpindahan layar dapat diperiksa secara komprehensif pada dokumen rancangan Figma.");
  styleP(pNav, DocumentApp.HorizontalAlignment.JUSTIFY, 12, false, 4, 0);
}

function formatTable(table, colWidths) {
  table.setBorderColor("#000000");
  table.setBorderWidth(1);
  var numRows = table.getNumRows();
  
  // Header Row
  var header = table.getRow(0);
  for (var c = 0; c < header.getNumCells(); c++) {
    var cell = header.getCell(c);
    cell.setBackgroundColor("#E5E7EB");
    cell.setPaddingTop(6);
    cell.setPaddingBottom(6);
    cell.setPaddingLeft(8);
    cell.setPaddingRight(8);
    var p = cell.getChild(0).asParagraph();
    p.setAlignment(DocumentApp.HorizontalAlignment.CENTER);
    var t = cell.editAsText();
    t.setFontFamily("Times New Roman");
    t.setFontSize(10);
    t.setBold(false);
    t.setForegroundColor("#000000");
    if (colWidths && colWidths[c]) {
      cell.setWidth(colWidths[c]);
    }
  }
  
  // Data Rows
  for (var r = 1; r < numRows; r++) {
    var row = table.getRow(r);
    for (var c = 0; c < row.getNumCells(); c++) {
      var cell = row.getCell(c);
      cell.setBackgroundColor("#FFFFFF");
      cell.setPaddingTop(5);
      cell.setPaddingBottom(5);
      cell.setPaddingLeft(8);
      cell.setPaddingRight(8);
      if (colWidths && colWidths[c]) {
        cell.setWidth(colWidths[c]);
      }
    }
  }
}
"""

with open("Update_Laporan_GDocs.js", "w", encoding="utf-8") as f:
    f.write(js_code)

print("Generated Update_Laporan_GDocs.js successfully! File size:", os.path.getsize("Update_Laporan_GDocs.js"), "bytes")
