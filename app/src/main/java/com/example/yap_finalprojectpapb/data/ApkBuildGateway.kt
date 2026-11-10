package com.example.yap_finalprojectpapb.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

@Serializable
data class BuildClarification(val id: String, val question: String, val answer: String?)

@Serializable
data class ApkBuild(
    val id: String, val status: String, val sha256: String?, val size: Long?,
    val clarification: BuildClarification? = null,
    val message: String? = null,
    val clarifications: List<BuildClarification> = emptyList(),
    val appName: String? = null
)

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
            if (settings.url == defaultGatewayUrl) {
                setRequestProperty("ngrok-skip-browser-warning", "1")
            }
        }
    }

    private fun checkResponse(connection: HttpURLConnection) {
        val status = connection.responseCode
        if (status in listOf(429, 502, 503, 504)) {
            throw IOException("Layanan YAP sementara tidak tersedia.")
        }
        check(status in 200..299) {
            when (status) {
                401, 403 -> "Token akses YAP tidak valid."
                else -> "Gateway APK mengembalikan HTTP $status."
            }
        }
    }

    suspend fun submit(settings: GatewaySettings, id: String, prompt: String, context: String = ""): ApkBuild =
        request(settings, "/api/build", buildJsonObject {
            put("id", id)
            put("prompt", prompt)
            put("context", context)
            put("responseMode", "chat")
        }.toString())

    suspend fun status(settings: GatewaySettings, id: String): ApkBuild = request(settings, "/api/build/$id")

    suspend fun answer(settings: GatewaySettings, id: String, questionId: String, answer: String): ApkBuild =
        request(settings, "/api/build/$id/answer", buildJsonObject {
            put("questionId", questionId)
            put("answer", answer)
        }.toString())

    private suspend fun request(settings: GatewaySettings, path: String, body: String? = null): ApkBuild =
        retryNetwork { requestOnce(settings, path, body) }

    private suspend fun <T> retryNetwork(operation: suspend () -> T): T {
        repeat(3) { attempt ->
            try { return operation() }
            catch (error: IOException) {
                currentCoroutineContext().ensureActive()
                if (attempt == 2) throw error
                delay((attempt + 1) * 1000L)
            }
        }
        error("Percobaan koneksi selesai tanpa hasil.")
    }

    private suspend fun requestOnce(settings: GatewaySettings, path: String, body: String?): ApkBuild =
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
                    val buffer = ByteArray(131_073)
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
                val clarification = (json["clarification"] as? JsonObject)?.let {
                    BuildClarification(it.getValue("id").jsonPrimitive.content,
                        it.getValue("question").jsonPrimitive.content, it["answer"]?.jsonPrimitive?.contentOrNull)
                }
                val clarifications = (json["clarifications"] as? JsonArray)?.map {
                    val entry = it.jsonObject
                    BuildClarification(entry.getValue("id").jsonPrimitive.content,
                        entry.getValue("question").jsonPrimitive.content, entry["answer"]?.jsonPrimitive?.contentOrNull)
                }.orEmpty()
                ApkBuild(json.getValue("id").jsonPrimitive.content, json.getValue("status").jsonPrimitive.content,
                    json["sha256"]?.jsonPrimitive?.contentOrNull, json["size"]?.jsonPrimitive?.longOrNull, clarification,
                    json["message"]?.jsonPrimitive?.contentOrNull, clarifications,
                    json["appName"]?.jsonPrimitive?.contentOrNull)
            } finally {
                connection.disconnect()
            }
        }

    suspend fun preview(settings: GatewaySettings, id: String): String? =
        retryNetwork { previewOnce(settings, id) }

    private suspend fun previewOnce(settings: GatewaySettings, id: String): String? = withContext(Dispatchers.IO) {
        val connection = connect(settings, "/api/build/$id/preview", "GET")
        try {
            if (connection.responseCode == 404) return@withContext null
            checkResponse(connection)
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 13_000_000) { "Pratinjau terlalu besar." }
                    output.write(buffer, 0, count)
                }
            }
            Json.parseToJsonElement(output.toString("UTF-8")).jsonObject.getValue("html").jsonPrimitive.content
        } finally { connection.disconnect() }
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
