import glob
import subprocess
import sys

bin_list = glob.glob(r'C:\Users\kuahs\AppData\Local\OpenAI\Codex\bin\*\codex.exe')
if not bin_list:
    print('NO CODEX BIN FOUND')
    sys.exit(1)

bin_p = bin_list[0]
print('Using Astra (Codex) binary:', bin_p)

prompt = """
Kamu adalah Agent Astra, spesialis Senior UI/UX dan Software Engineering.
Tugasmu:
1. Review arsitektur UI/UX terbaru aplikasi YAP Mobile AI Studio (Jetpack Compose).
2. Kami telah membuat:
   - YapMascot dengan 3 mood (Normal, Happy, Thinking) menggunakan vektor resmi pir biru.
   - ChatStudioScreen yang memiliki 3 tab: Chat (dengan 5-fase agent pipeline tracker: Architect, Coder, Impeccable UI, Playwright QA, Strix Pentest), Tab Preview (menggunakan WebView interaktif live), dan Tab Code (multi-file monospace code viewer dengan selector file dan copy button).
   - PreviewScreen (full screen WebView dengan toggle viewport HP vs Desktop).
   - CodeViewerScreen (full screen source code editor dengan line numbers).
3. Berikan evaluasi UI/UX profesional (prinsip anti-AI slop, estetika, kenyamanan pengguna mobile) dan berikan saran spesifik untuk penyempurnaan berikutnya.
Jawab secara ringkas, berbobot, lugas, dan to the point tanpa basa-basi!
"""

cmd = [
    bin_p,
    'exec',
    '--ephemeral',
    '-c', 'model_reasoning_effort="high"',
    '-s', 'read-only',
    prompt
]

try:
    res = subprocess.run(cmd, capture_output=True, text=True, timeout=120, encoding='utf-8', errors='replace')
    print('=== ASTRA (CODEX) RESPONSE ===')
    print(res.stdout)
    if res.stderr:
        print('STDERR:', res.stderr)
except Exception as e:
    print('Error calling Astra:', e)
