package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.example.yap_finalprojectpapb.data.GatewaySettings
import com.example.yap_finalprojectpapb.data.GatewaySettingsStore
import com.example.yap_finalprojectpapb.data.gatewayUrlError
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

@Composable
fun APISettingsScreen(
    onBackClick: () -> Unit = {},
    onApplyClick: () -> Unit = {},
    onCancelClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val store = remember { GatewaySettingsStore(context) }
    val saved = remember { store.load() }
    var serverUrl by rememberSaveable { mutableStateOf(saved.url) }
    // Access tokens must not enter saved-instance-state bundles.
    var accessToken by remember { mutableStateOf(saved.token) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NeutralWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                HeaderWithTitle(
                    title = "Pengaturan AI",
                    onBackClick = onBackClick
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text("Muse Spark 1.3", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Hubungkan ke server YAP milik pengelola. Kunci API Muse disimpan di server.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(24.dp))
                AppTextField(label = "Alamat server YAP", value = serverUrl,
                    onValueChange = { serverUrl = it; error = null }, placeholder = "https://server-yap.example")
                Spacer(modifier = Modifier.height(16.dp))
                AppTextField(label = "Token akses YAP", value = accessToken,
                    onValueChange = { accessToken = it; error = null }, placeholder = "Token dari pengelola",
                    visualTransformation = PasswordVisualTransformation())
                error?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    AppButton(
                        text = "Cancel",
                        onClick = onCancelClick,
                        variant = AppButtonVariant.Secondary,
                        colorStyle = AppButtonColorStyle.Blue,
                        fullWidth = true
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    AppButton(
                        text = "Apply",
                        onClick = {
                            error = gatewayUrlError(serverUrl)
                                ?: when {
                                    accessToken.trim().startsWith("LLM|") -> "Masukkan token YAP dari pengelola, bukan kunci API Muse."
                                    accessToken.trim().length < 32 -> "Token akses minimal 32 karakter."
                                    else -> null
                                }
                            if (error == null) {
                                runCatching { store.save(GatewaySettings(serverUrl.trim(), accessToken.trim())) }
                                    .onSuccess { onApplyClick() }
                                    .onFailure { error = "Pengaturan tidak dapat disimpan. Coba lagi." }
                            }
                        },
                        variant = AppButtonVariant.Primary,
                        colorStyle = AppButtonColorStyle.Blue,
                        fullWidth = true
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun APISettingsScreenPreview() {
    YAPFinalProjectPAPBTheme {
        APISettingsScreen()
    }
}
