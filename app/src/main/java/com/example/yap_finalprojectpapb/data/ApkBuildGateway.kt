package com.example.yap_finalprojectpapb.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

data class ApkBuild(val id: String, val status: String, val sha256: String?, val size: Long?)

class ApkBuildGateway(private val settingsStore: GatewaySettingsStore) {
    fun session(): GatewaySettings = settingsStore.load().also {
        check(gatewayUrlError(it.url) == null) { "Atur alamat server di Pengaturan AI terlebih dahulu." }
        check(it.token.length >= 32) { "Isi token akses YAP di Pengaturan AI." }
    }

    private fun connect(settings: GatewaySettings, path: String, method: String): HttpURLConnection {
        return (URI(settings.url.trimEnd('/') + path).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer ${settings.token}")
        }
    }

    private fun checkResponse(connection: HttpURLConnection) {
        val status = connection.responseCode
        check(status in 200..299) {
            when (status) {
                401, 403 -> "Token akses YAP tidak valid."
                503 -> "Antrean APK belum dikonfigurasi di gateway."
                else -> "Gateway APK mengembalikan HTTP $status."
            }
        }
    }

    suspend fun submit(settings: GatewaySettings, id: String, prompt: String): ApkBuild =
        request(settings, "/api/build", buildJsonObject { put("id", id); put("prompt", prompt) }.toString())

    suspend fun status(settings: GatewaySettings, id: String): ApkBuild = request(settings, "/api/build/$id")

    private suspend fun request(settings: GatewaySettings, path: String, body: String? = null): ApkBuild =
        withContext(Dispatchers.IO) {
            val connection = connect(settings, path, if (body == null) "GET" else "POST")
            try {
                if (body != null) {
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                checkResponse(connection)
                val raw = connection.inputStream.use { input ->
                    val buffer = ByteArray(4097)
                    var size = 0
                    while (size < buffer.size) {
                        val read = input.read(buffer, size, buffer.size - size)
                        if (read == -1) break
                        size += read
                    }
                    check(size < buffer.size) { "Respons status APK terlalu besar." }
                    String(buffer, 0, size, Charsets.UTF_8)
                }
                val json = Json.parseToJsonElement(raw).jsonObject
                ApkBuild(json.getValue("id").jsonPrimitive.content, json.getValue("status").jsonPrimitive.content,
                    json["sha256"]?.jsonPrimitive?.contentOrNull, json["size"]?.jsonPrimitive?.longOrNull)
            } finally {
                connection.disconnect()
            }
        }

    suspend fun download(context: Context, settings: GatewaySettings, job: ApkBuild, destination: Uri) =
        withContext(Dispatchers.IO) {
            val expectedSize = checkNotNull(job.size)
            val expectedHash = checkNotNull(job.sha256)
            check(expectedSize in 1..33_554_432) { "Ukuran APK tidak valid." }
            val temp = File.createTempFile("yap-apk-", ".tmp", context.cacheDir)
            try {
                val connection = connect(settings, "/api/build/${job.id}/apk", "GET")
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                try {
                    checkResponse(connection)
                    connection.inputStream.use { input ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count == -1) break
                                size += count
                                check(size <= expectedSize) { "Ukuran APK tidak cocok." }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                check(size == expectedSize && hash == expectedHash) { "Unduhan APK tidak utuh. Coba lagi." }
                val output = checkNotNull(context.contentResolver.openOutputStream(destination, "wt")) {
                    "Lokasi penyimpanan tidak dapat dibuka."
                }
                output.use { target -> temp.inputStream().use { it.copyTo(target) } }
            } finally {
                temp.delete()
            }
        }
}
