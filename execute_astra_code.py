import glob
import subprocess
import os
import sys

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')
bin_list = glob.glob(r'C:\Users\kuahs\AppData\Local\OpenAI\Codex\bin\*\codex.exe')
if not bin_list:
    print('NO CODEX BIN FOUND')
    sys.exit(1)

bin_p = bin_list[0]
print('Using Astra (Codex) binary:', bin_p)

prompt = """
Kamu adalah Agent Astra (Senior Android UI/UX Engineer & Staff Kotlin Specialist).
Tugasmu adalah MENGODING LANGSUNG (CODE IMPLEMENTATION) untuk memperbaiki UI/UX aplikasi YAP (Jetpack Compose) di repositori ini, sesuai dengan rekomendasi verdictmu di `astra_ui_ux_verdict.md`.

Fokus pengerjaan di `app/src/main/java/com/example/yap_finalprojectpapb/ui/screens/ChatStudioScreen.kt` dan berkas pendukungnya:

1. REFAKTOR PIPELINE TRACKER (ANTI-PADAT & ERGONOMIS):
   - Ubah checklist 5-fase yang kaku dan memakan layar ponsel menjadi status pekerjaan ringkas (misal: progress bar + status ringkas "Tahap 2/5 · Menulis kode").
   - Sediakan tombol expand/collapse (accordion) halus jika pengguna ingin melihat rincian tahapan (Architect, Coder, Impeccable, Playwright, Strix).
   - Pastikan YapMascot tetap tampil interaktif dan proporsional.

2. KONTINUITAS STATE & WEBVIEW/CODE TAB:
   - Jaga agar state input, pilihan file code, dan riwayat WebView tidak reload/reset saat recomposition atau berpindah tab.
   - Berikan feedback konfirmasi singkat (Toast/Snackbar) saat pengguna menekan tombol salin kode (Copy).

3. ERGONOMI MOBILE & TARGET SENTUH (MINIMAL 48 DP):
   - Pastikan seluruh tombol aksi utama (tab chip, copy button, expand button, close drawer, navigation) memenuhi touch target minimum 48 dp sesuai panduan Material 3 / Android Accessibility.
   - Perbaiki semantik TalkBack pada status proses.

4. VERIFIKASI BUILD:
   - Pastikan setelah kamu mengedit, jalankan `.\\gradlew.bat compileDebugKotlin --console=plain` dan pastikan BUILD SUCCESSFUL (0 errors).

Koding langsung perbaikannya ke dalam file sekarang juga!
"""

cmd = [
    bin_p,
    'exec',
    '--dangerously-bypass-approvals-and-sandbox',
    '-c', 'model_reasoning_effort="high"',
    '-C', r'C:\Users\kuahs\Documents\Yap',
    prompt
]

print("[*] Meluncurkan Agent Astra (Codex) untuk coding perbaikan...")
sys.stdout.flush()

try:
    process = subprocess.Popen(
        cmd,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding='utf-8',
        errors='replace',
        cwd=r'C:\Users\kuahs\Documents\Yap'
    )
    
    for line in iter(process.stdout.readline, ''):
        print(line, end='', flush=True)
        
    process.wait()
    print(f"\n[OK] Selesai dengan exit code: {process.returncode}")
except Exception as e:
    print('Error calling Astra:', e)
