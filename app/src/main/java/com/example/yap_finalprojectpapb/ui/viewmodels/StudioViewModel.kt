package com.example.yap_finalprojectpapb.ui.viewmodels

import android.app.Application
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
    val error: String? = null
)

class StudioViewModel(application: Application) : AndroidViewModel(application) {
    private val gateway = StudioGateway(GatewaySettingsStore(application))
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
        mutableState.update { it.copy(prompt = prompt.trim(), loading = true, error = null) }
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

    fun retry() = generate(mutableState.value.prompt)

    fun reset() {
        request?.cancel()
        mutableState.value = StudioUiState()
    }
}
