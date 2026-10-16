import sys
sys.path.insert(0, r'C:\Users\kuahs\Documents\joints\src')
from codex_agent_bridge import ask_codex

review_prompt = """
Halo Astra (Codex). Mohon berikan peer review UI/UX untuk update aplikasi YAP Mobile AI Studio (Android Jetpack Compose):
1. YapMascot: Resmi dari vektor Git, 3 mood (Normal, Happy, Thinking), ada haptik & animasi bounce.
2. ChatStudio: Tab Chat (ada 5-fase agent tracker: Architect -> Coder 3.7 -> Impeccable -> Playwright -> Strix), Tab Preview (Android WebView interaktif), Tab Code (Monospace dark editor + file tabs + copy).
3. Layar Baru: PreviewScreen (fullscreen web view) & CodeViewerScreen (fullscreen code).

Berikan:
- Verdict singkat mengenai kepatuhan anti-AI slop dan ergonomi mobile.
- 3 rekomendasi teknis utama untuk iterasi berikutnya.
Jawab padat, tajam, dan langsung ke intinya!
"""

print("[*] Menghubungi Astra (Codex) dengan reasoning_effort medium...")
try:
    response = ask_codex(review_prompt, reasoning_effort="medium", timeout=300)
    print("\n=== VERDICT DARI AGENT ASTRA ===")
    print(response)
    with open("astra_ui_ux_verdict.md", "w", encoding="utf-8") as f:
        f.write(response)
    print("\n[OK] Verdict tersimpan di astra_ui_ux_verdict.md")
except Exception as e:
    print("Error:", e)
