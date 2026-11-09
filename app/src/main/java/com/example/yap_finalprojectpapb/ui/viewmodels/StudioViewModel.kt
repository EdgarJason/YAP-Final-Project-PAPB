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
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                publish(state.value.copy(loading = false,
                    error = if (error is IllegalStateException) error.message else "Tidak dapat menghubungi Muse. Coba lagi."))
            }
        }
    }

    fun loadPreview(turnId: String? = null) {
        val turn = state.value.turns.lastOrNull { it.apk?.status == "ready" && (turnId == null || it.id == turnId) } ?: return
        val conversationId = state.value.conversationId
        previewRequest?.cancel()
        mutableState.value = state.value.copy(previewLoading = true, previewHtml = null, previewMessage = null)
        previewRequest = viewModelScope.launch {
            try {
                val session = apkGateway.session()
                check(turn.gatewayUrl == session.url) { "Gateway pratinjau berbeda." }
                val html = apkGateway.preview(session, turn.id)
                currentCoroutineContext().ensureActive()
                if (state.value.conversationId == conversationId) mutableState.value = state.value.copy(
                    previewLoading = false, previewHtml = html, previewJobId = turn.id,
                    previewMessage = if (html == null) "APK ini tidak menyediakan pratinjau web. Unduh APK untuk mencobanya." else null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (state.value.conversationId == conversationId) mutableState.value = state.value.copy(
                    previewLoading = false, previewMessage = "Pratinjau belum dapat dimuat. Coba lagi.")
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
