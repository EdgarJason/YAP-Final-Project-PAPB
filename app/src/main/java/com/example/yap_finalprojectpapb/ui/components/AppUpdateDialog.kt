package com.example.yap_finalprojectpapb.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap_finalprojectpapb.BuildConfig
import com.example.yap_finalprojectpapb.data.AppUpdates
import com.example.yap_finalprojectpapb.ui.viewmodels.AppUpdateViewModel

@Composable
fun AppUpdateDialog(
    visible: Boolean,
    onShow: () -> Unit,
    onDismiss: () -> Unit,
    model: AppUpdateViewModel = viewModel()
) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(state.update) { if (state.update != null) onShow() }

    fun install() {
        val file = state.file ?: return
        val update = state.update ?: return
        runCatching {
            AppUpdates(context).verify(file, update.versionCode)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.onFailure { model.installationError() }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) install()
    }
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pembaruan YAP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Versi terpasang ${BuildConfig.VERSION_NAME}")
                state.update?.let { Text("Versi ${it.versionName} tersedia. Pembaruan akan mempertahankan data aplikasimu.") }
                when {
                    state.checking -> Text("Memeriksa pembaruan…")
                    state.downloading -> {
                        Text("Mengunduh ${state.percent}%")
                        LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    state.file != null -> Text("Unduhan sudah diverifikasi. Pilih Pasang pembaruan untuk melanjutkan di Android.")
                }
                state.message?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !state.checking && !state.downloading,
                onClick = {
                    when {
                        state.file != null -> {
                            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                                runCatching {
                                    permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${context.packageName}")))
                                }.onFailure { model.installationError() }
                            } else install()
                        }
                        state.update != null -> model.download()
                        else -> model.check()
                    }
                }
            ) {
                Text(when {
                    state.file != null -> "Pasang pembaruan"
                    state.update != null -> "Unduh pembaruan"
                    else -> "Periksa lagi"
                })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Nanti") } }
    )
}
