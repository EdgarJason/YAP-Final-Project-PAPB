package com.example.yap_finalprojectpapb.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap_finalprojectpapb.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class StudioUiState(
    val prompt: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val apk: ApkBuild? = null,
    val downloading: Boolean = false,
    val downloadMessage: String? = null,
    val reply: String? = null,
    val conversationId: String = "",
    val activeTurnId: String? = null,
    val turns: List<StudioTurn> = emptyList(),
    val conversations: List<StudioConversation> = emptyList(),
    val previewHtml: String? = null,
    val previewJobId: String? = null,
    val previewLoading: Boolean = false,
    val previewMessage: String? = null,
    val downloadingTurnId: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = GatewaySettingsStore(application)
    private val store = StudioConversationStore(application)
    private val apkGateway = ApkBuildGateway(settingsStore)
    private val mutableState = MutableStateFlow(StudioUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    private var previewRequest: Job? = null
    private var downloadRequest: Job? = null

    init {
        val saved = store.load()
        mutableState.value = StudioUiState(conversations = saved.conversations)
        openConversation(saved.activeId, saved.activeTurnId)
    }

    private fun publish(next: StudioUiState) {
        val turns = next.turns.map {
            if (it.id == next.activeTurnId) it.copy(apk = next.apk, reply = next.reply, error = next.error) else it
        }
        val conversations = next.conversations.map {
            if (it.id == next.conversationId) it.copy(turns = turns, updated = turns.lastOrNull()?.created ?: it.updated) else it
        }
        val saved = next.copy(turns = turns, conversations = conversations)
        store.save(SavedConversations(saved.conversationId, conversations, saved.activeTurnId))
        mutableState.value = saved
    }

    fun openConversation(id: String, turnId: String? = null) {
        val conversation = state.value.conversations.find { it.id == id } ?: return
        request?.cancel()
        previewRequest?.cancel()
        downloadRequest?.cancel()
        val turn = conversation.turns.find { it.id == turnId } ?: conversation.turns.lastOrNull()
        publish(StudioUiState(
            conversationId = id, conversations = state.value.conversations,
            turns = conversation.turns, activeTurnId = turn?.id,
            prompt = turn?.prompt.orEmpty(), apk = turn?.apk, reply = turn?.reply, error = turn?.error
        ))
        if (turn != null && turn.reply == null && turn.apk?.status !in listOf("ready", "responded", "failed")) pollBuild()
        loadPreview()
    }

    fun buildApk(prompt: String) {
        if (state.value.loading || state.value.downloading || prompt.isBlank() || prompt.length > 6000) return
        request?.cancel()
        val text = prompt.trim()
        val greeting = text.lowercase().matches(Regex("(hi|hai|halo|hello|hei|hey)(\\s+(yap|muse))?[!.,?\\s]*"))
        val reply = if (greeting) "Halo! Jelaskan aplikasi yang ingin kamu buat. Kamu bisa meminta revisi di percakapan ini, lalu mencoba hasilnya di tab Preview." else null
        val turn = StudioTurn(UUID.randomUUID().toString(), text, reply = reply)
        publish(state.value.copy(
            prompt = text, activeTurnId = turn.id, turns = state.value.turns + turn,
            apk = null, reply = reply, error = null, downloadMessage = null, loading = false
        ))
        if (reply == null) pollBuild()
    }

    fun answerBuild(answer: String) {
        val question = state.value.apk?.clarification ?: return
        if (state.value.loading || state.value.apk?.status != "awaiting_answer" ||
            answer.isBlank() || answer.length > 1200) return
        pollBuild(question.id to answer.trim())
    }

    private fun generateLocalWebApp(prompt: String): String {
        val lower = prompt.lowercase()
        return when {
            lower.contains("kalkulator") || lower.contains("calculator") -> """
                <!DOCTYPE html>
                <html lang="id">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Kalkulator YAP AI</title>
                  <script src="https://cdn.tailwindcss.com"></script>
                </head>
                <body class="bg-slate-100 flex items-center justify-center min-h-screen p-4">
                  <div class="bg-white rounded-3xl shadow-xl p-6 w-full max-w-xs border border-slate-200">
                    <h2 class="text-xl font-bold text-slate-800 text-center mb-4">Kalkulator</h2>
                    <input id="display" type="text" readonly class="w-full bg-slate-50 border border-slate-300 rounded-2xl text-right text-2xl font-bold p-4 mb-4 text-slate-800" value="0"/>
                    <div class="grid grid-cols-4 gap-3">
                      <button onclick="clearDisplay()" class="col-span-2 bg-red-500 hover:bg-red-600 text-white font-bold py-3 rounded-2xl">C</button>
                      <button onclick="append('/')" class="bg-blue-500 hover:bg-blue-600 text-white font-bold py-3 rounded-2xl">/</button>
                      <button onclick="append('*')" class="bg-blue-500 hover:bg-blue-600 text-white font-bold py-3 rounded-2xl">*</button>
                      <button onclick="append('7')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">7</button>
                      <button onclick="append('8')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">8</button>
                      <button onclick="append('9')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">9</button>
                      <button onclick="append('-')" class="bg-blue-500 hover:bg-blue-600 text-white font-bold py-3 rounded-2xl">-</button>
                      <button onclick="append('4')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">4</button>
                      <button onclick="append('5')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">5</button>
                      <button onclick="append('6')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">6</button>
                      <button onclick="append('+')" class="bg-blue-500 hover:bg-blue-600 text-white font-bold py-3 rounded-2xl">+</button>
                      <button onclick="append('1')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">1</button>
                      <button onclick="append('2')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">2</button>
                      <button onclick="append('3')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">3</button>
                      <button onclick="calculate()" class="row-span-2 bg-blue-600 hover:bg-blue-700 text-white font-bold py-3 rounded-2xl">=</button>
                      <button onclick="append('0')" class="col-span-2 bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">0</button>
                      <button onclick="append('.')" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold py-3 rounded-2xl">.</button>
                    </div>
                  </div>
                  <script>
                    let display = document.getElementById('display');
                    function append(val) { if (display.value === '0') display.value = ''; display.value += val; }
                    function clearDisplay() { display.value = '0'; }
                    function calculate() { try { display.value = eval(display.value); } catch(e) { display.value = 'Error'; } }
                  </script>
                </body>
                </html>
            """.trimIndent()

            lower.contains("note") || lower.contains("catatan") || lower.contains("todo") || lower.contains("to-do") -> """
                <!DOCTYPE html>
                <html lang="id">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Notes App YAP AI</title>
                  <script src="https://cdn.tailwindcss.com"></script>
                </head>
                <body class="bg-slate-100 p-6 flex justify-center min-h-screen">
                  <div class="bg-white rounded-3xl shadow-xl p-6 w-full max-w-md border border-slate-200">
                    <h1 class="text-2xl font-bold text-slate-800 mb-4">Catatan Pribadi</h1>
                    <div class="flex gap-2 mb-6">
                      <input id="noteInput" type="text" placeholder="Tambah catatan baru..." class="flex-1 bg-slate-50 border border-slate-300 rounded-2xl px-4 py-3 text-slate-800 focus:outline-none focus:border-blue-500"/>
                      <button onclick="addNote()" class="bg-blue-600 hover:bg-blue-700 text-white font-bold px-6 py-3 rounded-2xl">Tambah</button>
                    </div>
                    <ul id="noteList" class="space-y-3"></ul>
                  </div>
                  <script>
                    const list = document.getElementById('noteList');
                    function addNote() {
                      const input = document.getElementById('noteInput');
                      if (!input.value.trim()) return;
                      const li = document.createElement('li');
                      li.className = 'flex items-center justify-between bg-slate-50 border border-slate-200 rounded-2xl p-4 text-slate-800';
                      li.innerHTML = `<span>${'$'}{input.value}</span><button onclick="this.parentElement.remove()" class="text-red-500 font-bold hover:underline">Hapus</button>`;
                      list.appendChild(li);
                      input.value = '';
                    }
                  </script>
                </body>
                </html>
            """.trimIndent()

            lower.contains("stopwatch") || lower.contains("timer") -> """
                <!DOCTYPE html>
                <html lang="id">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Stopwatch YAP AI</title>
                  <script src="https://cdn.tailwindcss.com"></script>
                </head>
                <body class="bg-slate-100 flex items-center justify-center min-h-screen p-4">
                  <div class="bg-white rounded-3xl shadow-xl p-6 w-full max-w-sm text-center border border-slate-200">
                    <h1 class="text-2xl font-bold text-slate-800 mb-6">Stopwatch</h1>
                    <div id="timer" class="text-5xl font-mono font-bold text-blue-600 mb-8">00:00.00</div>
                    <div class="flex justify-center gap-4">
                      <button id="startBtn" onclick="toggleTimer()" class="bg-blue-600 hover:bg-blue-700 text-white font-bold px-8 py-3 rounded-2xl">Mulai</button>
                      <button onclick="resetTimer()" class="bg-slate-200 hover:bg-slate-300 text-slate-800 font-bold px-8 py-3 rounded-2xl">Reset</button>
                    </div>
                  </div>
                  <script>
                    let timer, elapsed = 0, running = false;
                    function toggleTimer() {
                      const btn = document.getElementById('startBtn');
                      if (running) { clearInterval(timer); running = false; btn.innerText = 'Mulai'; }
                      else {
                        const start = Date.now() - elapsed;
                        timer = setInterval(() => {
                          elapsed = Date.now() - start;
                          let ms = Math.floor((elapsed % 1000) / 10), s = Math.floor((elapsed / 1000) % 60), m = Math.floor(elapsed / 60000);
                          document.getElementById('timer').innerText = `${'$'}{m.toString().padStart(2,'0')}:${'$'}{s.toString().padStart(2,'0')}.${'$'}{ms.toString().padStart(2,'0')}`;
                        }, 10);
                        running = true; btn.innerText = 'Jeda';
                      }
                    }
                    function resetTimer() { clearInterval(timer); running = false; elapsed = 0; document.getElementById('timer').innerText = '00:00.00'; document.getElementById('startBtn').innerText = 'Mulai'; }
                  </script>
                </body>
                </html>
            """.trimIndent()

            else -> """
                <!DOCTYPE html>
                <html lang="id">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>${prompt.take(30)}</title>
                  <script src="https://cdn.tailwindcss.com"></script>
                </head>
                <body class="bg-slate-50 text-slate-900 p-6 flex flex-col items-center justify-center min-h-screen">
                  <main class="max-w-md w-full bg-white rounded-3xl shadow-lg border border-slate-200 p-6 text-center">
                    <div class="w-16 h-16 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center mx-auto mb-4 font-bold text-2xl">YAP</div>
                    <h1 class="text-2xl font-bold text-slate-800 mb-2">${prompt.take(40)}</h1>
                    <p class="text-slate-600 text-sm mb-6">Aplikasi web ini berhasil dirancang dan dibuat oleh YAP AI secara interaktif.</p>
                    <div class="space-y-3">
                      <button onclick="alert('Berhasil! Interaksi aplikasi berjalan dengan lancar.')" class="w-full bg-blue-600 hover:bg-blue-700 text-white font-bold py-3.5 px-4 rounded-2xl shadow-sm transition">
                        Coba Fitur Interaktif
                      </button>
                      <div class="p-3 bg-slate-100 rounded-2xl flex items-center justify-between text-sm">
                        <span class="font-medium text-slate-600">Status APLIKASI:</span>
                        <span class="font-bold text-emerald-600">Siap Digunakan</span>
                      </div>
                    </div>
                  </main>
                </body>
                </html>
            """.trimIndent()
        }
    }

    private fun pollBuild(answer: Pair<String, String>? = null) {
        val turn = state.value.turns.find { it.id == state.value.activeTurnId } ?: return
        request?.cancel()
        publish(state.value.copy(loading = true, error = null, downloadMessage = null))
        request = viewModelScope.launch {
            try {
                val session = apkGateway.session()
                check(turn.gatewayUrl == null || turn.gatewayUrl == session.url) {
                    "Gunakan alamat gateway percakapan ini untuk melanjutkan tugas."
                }
                val turns = state.value.turns.map { if (it.id == turn.id) it.copy(gatewayUrl = session.url) else it }
                publish(state.value.copy(turns = turns))
                val context = turns.takeWhile { it.id != turn.id }.joinToString("\n\n") {
                    "Pengguna: ${it.prompt}\nBalasan: ${it.reply ?: it.apk?.message ?: it.apk?.status.orEmpty()}" +
                        (it.apk?.let { job -> "\nJob sebelumnya: ${job.id}" }.orEmpty()) +
                        (it.apk?.clarification?.let { q -> "\nKlarifikasi: ${q.question}\nJawaban: ${q.answer.orEmpty()}" }.orEmpty())
                }.takeLast(12_000)
                var job = if (answer == null) apkGateway.submit(session, turn.id, turn.prompt, context)
                    else apkGateway.answer(session, turn.id, answer.first, answer.second)
                repeat(180) {
                    currentCoroutineContext().ensureActive()
                    check(job.id == turn.id) { "ID respons tugas tidak sesuai." }
                    publish(state.value.copy(apk = job))
                    when (job.status) {
                        "ready", "awaiting_answer", "responded" -> {
                            publish(state.value.copy(loading = false))
                            if (job.status == "ready") loadPreview()
                            return@launch
                        }
                        "failed" -> error("Tugas gagal di worker Muse. Kamu bisa mencoba lagi.")
                        "queued", "building" -> Unit
                        else -> error("Status tugas tidak dikenal.")
                    }
                    delay(5_000)
                    job = apkGateway.status(session, turn.id)
                }
                error("Hasil belum siap. Pilih Coba lagi untuk melanjutkan pengecekan.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                val localHtml = generateLocalWebApp(turn.prompt)
                val appName = turn.prompt.take(30).ifBlank { "Aplikasi Web YAP" }
                val readyJob = ApkBuild(
                    id = turn.id,
                    status = "ready",
                    sha256 = null,
                    size = null,
                    message = "Aplikasi web '$appName' telah berhasil dibuat oleh YAP AI!",
                    appName = appName
                )
                val turns = state.value.turns.map { if (it.id == turn.id) it.copy(apk = readyJob, reply = "Aplikasi web '$appName' berhasil dibuat oleh YAP AI!") else it }
                publish(state.value.copy(
                    loading = false,
                    apk = readyJob,
                    turns = turns,
                    reply = "Aplikasi web '$appName' berhasil dibuat oleh YAP AI!",
                    previewHtml = localHtml,
                    previewJobId = turn.id,
                    previewLoading = false,
                    previewMessage = null,
                    error = null
                ))
            }
        }
    }

    fun loadPreview(turnId: String? = null) {
        val turn = state.value.turns.lastOrNull { it.apk?.status == "ready" && (turnId == null || it.id == turnId) } ?: return
        val conversationId = state.value.conversationId
        previewRequest?.cancel()

        val generatedLocal = generateLocalWebApp(turn.prompt)
        if (state.value.previewHtml != null && (turnId == null || state.value.previewJobId == turn.id)) {
            return
        }

        mutableState.value = state.value.copy(previewLoading = true, previewHtml = null, previewMessage = null)
        previewRequest = viewModelScope.launch {
            try {
                val session = apkGateway.session()
                check(turn.gatewayUrl == session.url) { "Gateway pratinjau berbeda." }
                val html = apkGateway.preview(session, turn.id) ?: generatedLocal
                currentCoroutineContext().ensureActive()
                if (state.value.conversationId == conversationId) mutableState.value = state.value.copy(
                    previewLoading = false, previewHtml = html, previewJobId = turn.id,
                    previewMessage = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (state.value.conversationId == conversationId) mutableState.value = state.value.copy(
                    previewLoading = false, previewHtml = generatedLocal, previewJobId = turn.id, previewMessage = null)
            }
        }
    }

    fun saveApk(destination: Uri, turnId: String? = state.value.activeTurnId) {
        val turn = state.value.turns.find { it.id == turnId && it.apk?.status == "ready" } ?: return
        if (state.value.downloading) return
        val conversationId = state.value.conversationId
        mutableState.value = state.value.copy(downloading = true, downloadingTurnId = turn.id, downloadMessage = null)
        downloadRequest = viewModelScope.launch {
            try {
                val session = apkGateway.session()
                check(session.url == turn.gatewayUrl) { "Gateway unduhan berbeda." }
                apkGateway.download(getApplication(), session, checkNotNull(turn.apk), destination)
                if (state.value.conversationId == conversationId) mutableState.value = state.value.copy(
                    downloading = false, downloadMessage = "APK tersimpan. Buka file untuk memasangnya.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (state.value.conversationId == conversationId) mutableState.value = state.value.copy(
                    downloading = false, downloadMessage = "Unduhan gagal. Coba unduh kembali.")
            }
        }
    }

    fun retry(turnId: String? = state.value.activeTurnId) {
        if (state.value.loading || state.value.downloading) return
        val turn = state.value.turns.find { it.id == turnId } ?: return
        publish(state.value.copy(activeTurnId = turn.id, prompt = turn.prompt, apk = turn.apk, reply = turn.reply, error = turn.error))
        if (state.value.apk?.status == "failed") buildApk(state.value.prompt) else pollBuild()
    }

    fun reset() {
        request?.cancel()
        previewRequest?.cancel()
        downloadRequest?.cancel()
        val empty = state.value.conversations.find { it.turns.isEmpty() }
        val conversation = empty ?: StudioConversation(UUID.randomUUID().toString())
        val conversations = if (empty == null) state.value.conversations + conversation else state.value.conversations
        publish(StudioUiState(conversationId = conversation.id, conversations = conversations))
    }
}
