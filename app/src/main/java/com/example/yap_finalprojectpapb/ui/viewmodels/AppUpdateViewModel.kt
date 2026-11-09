package com.example.yap_finalprojectpapb.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yap_finalprojectpapb.data.AppUpdate
import com.example.yap_finalprojectpapb.data.AppUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AppUpdateState(
    val checking: Boolean = false, val downloading: Boolean = false,
    val update: AppUpdate? = null, val file: File? = null,
    val percent: Int = 0, val message: String? = null
)

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val updates = AppUpdates(application)
    private val mutableState = MutableStateFlow(AppUpdateState())
    val state = mutableState.asStateFlow()

    init { check() }

    fun check() {
        if (state.value.checking || state.value.downloading) return
        viewModelScope.launch {
            mutableState.update { it.copy(checking = true, message = null) }
            try {
                val update = updates.check()
                mutableState.update { it.copy(checking = false, update = update,
                    message = if (update == null) "YAP sudah versi terbaru." else null) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(checking = false, message = "Belum dapat memeriksa versi. Periksa internet, lalu coba lagi.") }
            }
        }
    }

    fun download() {
        val update = state.value.update ?: return
        if (state.value.downloading || state.value.checking) return
        viewModelScope.launch {
            mutableState.update { it.copy(downloading = true, message = null, percent = 0) }
            try {
                val file = updates.download(update) { percent -> mutableState.update { it.copy(percent = percent) } }
                mutableState.update { it.copy(downloading = false, file = file) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutableState.update { it.copy(downloading = false,
                    message = "Pembaruan belum dapat diunduh atau diverifikasi. Coba unduh lagi.") }
            }
        }
    }

    fun installationError() {
        mutableState.update { it.copy(message = "Pemasangan belum dapat dibuka. Periksa izin pemasangan YAP, lalu coba lagi.") }
    }
}
