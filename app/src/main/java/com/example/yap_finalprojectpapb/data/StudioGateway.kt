package com.example.yap_finalprojectpapb.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.yap_finalprojectpapb.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class GatewaySettings(val url: String = "", val token: String = "")

/** Only the gateway access token is stored on-device. The Muse key stays on the server. */
class GatewaySettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("yap_gateway", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("yap_gateway_token", null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder("yap_gateway_token", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
    }

    fun load(): GatewaySettings {
        val token = runCatching {
            val bytes = Base64.decode(preferences.getString("token", ""), Base64.NO_WRAP)
            if (bytes.size < 13) "" else Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                String(doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
            }
        }.getOrDefault("")
        return GatewaySettings(preferences.getString("url", "") ?: "", token)
    }

    fun save(settings: GatewaySettings) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(settings.token.toByteArray(Charsets.UTF_8))
        check(preferences.edit().putString("url", settings.url.trim().trimEnd('/'))
            .putString("token", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
}

fun gatewayUrlError(value: String): String? {
    val uri = runCatching { URI(value.trim()) }.getOrNull()
    val localDebug = BuildConfig.DEBUG && uri?.scheme == "http" && uri.host in listOf("10.0.2.2", "127.0.0.1", "localhost")
    return if (uri?.host.isNullOrBlank() || uri?.userInfo != null || uri?.query != null || uri?.fragment != null
        || (uri?.scheme != "https" && !localDebug) || uri?.path !in listOf("", "/"))
        "Gunakan URL dasar server HTTPS, tanpa path, query, atau kredensial." else null
}

class StudioGateway(private val settingsStore: GatewaySettingsStore) {
    suspend fun generate(prompt: String, previousHtml: String): String = withContext(Dispatchers.IO) {
        val settings = settingsStore.load()
        gatewayUrlError(settings.url)?.let { throw IllegalStateException("Atur alamat server di Pengaturan AI terlebih dahulu.") }
        check(settings.token.length >= 32) { "Isi token akses YAP di Pengaturan AI." }
        val connection = URI(settings.url.trimEnd('/') + "/api/generate").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 270_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${settings.token}")
            val body = buildJsonObject {
                put("prompt", prompt)
                if (previousHtml.isNotEmpty()) put("previousHtml", previousHtml)
            }.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { reader ->
                val buffer = CharArray(4096)
                val text = StringBuilder()
                var count = reader.read(buffer)
                while (count != -1) {
                    check(text.length + count <= 300_000) { "Respons server terlalu besar." }
                    text.append(buffer, 0, count)
                    count = reader.read(buffer)
                }
                text.toString()
            }.orEmpty()
            val json = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
            check(status in 200..299) {
                // Do not display arbitrary proxy responses, which can contain upstream credentials.
                when (status) {
                    401, 403 -> "Token akses YAP tidak valid. Periksa Pengaturan AI."
                    429 -> "Server sedang sibuk. Coba lagi sebentar."
                    504 -> "Muse membutuhkan waktu terlalu lama. Coba lagi."
                    else -> "Pembuatan aplikasi gagal (HTTP $status). Coba lagi."
                }
            }
            val html = json?.get("html")?.jsonPrimitive?.content.orEmpty()
            check(html.length <= 125_000 && html.contains("<html", ignoreCase = true)
                && html.contains("</html>", ignoreCase = true)) { "Server mengembalikan HTML yang tidak lengkap." }
            html
        } finally {
            connection.disconnect()
        }
    }
}
