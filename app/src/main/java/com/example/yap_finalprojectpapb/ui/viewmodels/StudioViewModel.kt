package com.example.yap_finalprojectpapb.ui.viewmodels

import android.app.Application
import android.net.Uri
import com.example.yap_finalprojectpapb.data.ApkBuild
import com.example.yap_finalprojectpapb.data.ApkBuildGateway
import com.example.yap_finalprojectpapb.data.GatewaySettings
import kotlinx.coroutines.delay
import java.util.UUID
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap_finalprojectpapb.data.GatewaySettingsStore
import com.example.yap_finalprojectpapb.data.StudioGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudioUiState(
    val prompt: String = "",
    val html: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val apkMode: Boolean = false,
    val apk: ApkBuild? = null,
    val downloading: Boolean = false,
    val downloadMessage: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = GatewaySettingsStore(application)
    private val gateway = StudioGateway(settingsStore)
    private val apkGateway = ApkBuildGateway(settingsStore)
    private var apkSession: GatewaySettings? = null
    private var buildId: String? = null
    private var downloadRequest: Job? = null
    private val mutableState = MutableStateFlow(StudioUiState())
    val state = mutableState.asStateFlow()
    private var request: Job? = null

    fun generate(prompt: String) {
        if (mutableState.value.loading || prompt.isBlank()) return
        if (prompt.length > 6000) {
            mutableState.update { it.copy(error = "Instruksi maksimal 6000 karakter.") }
            return
        }
        val previous = mutableState.value.html
        mutableState.update { it.copy(prompt = prompt.trim(), loading = true, error = null, apkMode = false, apk = null) }
        request = viewModelScope.launch {
            try {
                val html = gateway.generate(prompt.trim(), previous)
                mutableState.update { it.copy(html = html, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                val message = if (error is IllegalStateException) error.message
                    else "Tidak dapat menghubungi server. Periksa koneksi dan coba lagi."
                mutableState.update { it.copy(loading = false, error = message) }
            }
        }
    }

    fun buildApk(prompt: String) {
        if (mutableState.value.loading || mutableState.value.downloading || prompt.isBlank() || prompt.length > 6000) return
        buildId = UUID.randomUUID().toString()
        apkSession = null
        pollBuild(prompt.trim())
    }

    private fun pollBuild(prompt: String) {
        val id = checkNotNull(buildId)
        mutableState.update { it.copy(prompt = prompt, apkMode = true, apk = null, loading = true, error = null, downloadMessage = null) }
        request = viewModelScope.launch {
            try {
                val session = apkSession ?: apkGateway.session().also { apkSession = it }
                var job = apkGateway.submit(session, id, prompt)
                repeat(180) {
                    check(job.id == id) { "ID respons APK tidak sesuai." }
                    mutableState.update { it.copy(apk = job) }
                    when (job.status) {
                        "ready" -> {
                            mutableState.update { it.copy(loading = false) }
                            return@launch
                        }
                        "failed" -> error("Build APK gagal di worker Muse.")
                        "queued", "building" -> Unit
                        else -> error("Status build tidak dikenal.")
                    }
                    delay(5_000)
                    job = apkGateway.status(session, id)
                }
                error("Hasil APK belum siap. Pilih Coba lagi untuk melanjutkan pengecekan.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(loading = false, error = error.message.takeIf { _ -> error is IllegalStateException }
                    ?: "Tidak dapat menghubungi gateway APK. Coba lagi.") }
            }
        }
    }

    fun saveApk(destination: Uri) {
        val job = mutableState.value.apk?.takeIf { it.status == "ready" } ?: return
        val session = apkSession ?: return
        if (mutableState.value.downloading) return
        mutableState.update { it.copy(downloading = true, downloadMessage = null) }
        downloadRequest = viewModelScope.launch {
            try {
                apkGateway.download(getApplication(), session, job, destination)
                mutableState.update { it.copy(downloading = false, downloadMessage = "APK tersimpan. Buka file untuk memasangnya.") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                mutableState.update { it.copy(downloading = false, downloadMessage = "Unduhan gagal. Pilih Unduh APK untuk mencoba lagi.") }
            }
        }
    }

    fun retry() {
        val current = mutableState.value
        if (current.loading) return
        if (!current.apkMode) generate(current.prompt)
        else if (current.apk?.status == "failed") buildApk(current.prompt)
        else pollBuild(current.prompt)
    }

    fun reset() {
        request?.cancel()
        downloadRequest?.cancel()
        buildId = null
        apkSession = null
        mutableState.value = StudioUiState()
    }
}
