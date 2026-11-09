package com.example.yap_finalprojectpapb.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.example.yap_finalprojectpapb.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

data class AppUpdate(val versionCode: Long, val versionName: String, val url: String, val size: Long, val sha256: String)

class AppUpdates(private val context: Context) {
    private fun connect(url: String): HttpURLConnection {
        check(URI(url).scheme == "https") { "Alamat pembaruan tidak valid." }
        return (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "YAP-Android/${BuildConfig.VERSION_NAME}")
        }
    }

    private fun json(url: String): JsonObject {
        val connection = connect(url)
        try {
            check(connection.responseCode == 200) { "Pembaruan belum dapat diperiksa. Coba lagi." }
            val bytes = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(output.size() + count <= 2_000_000) { "Informasi pembaruan terlalu besar." }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            return Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        } finally {
            connection.disconnect()
        }
    }

    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        val release = json("https://api.github.com/repos/TOBIANDA/YAP/releases/latest")
        val assets = release.getValue("assets").jsonArray.map { it.jsonObject }
        val manifest = assets.find { it["name"]?.jsonPrimitive?.content == "update.json" }
            ?: return@withContext null
        val info = json(manifest.getValue("browser_download_url").jsonPrimitive.content)
        val version = info.getValue("versionCode").jsonPrimitive.long
        if (version <= BuildConfig.VERSION_CODE) return@withContext null
        val name = if (BuildConfig.DEBUG) "YAP-update-debug.apk" else "YAP-release.apk"
        val asset = assets.find { it["name"]?.jsonPrimitive?.content == name }
            ?: error("Paket pembaruan belum tersedia. Coba lagi nanti.")
        val digest = asset["digest"]?.jsonPrimitive?.content.orEmpty()
        check(digest.matches(Regex("sha256:[a-fA-F0-9]{64}"))) { "Verifikasi pembaruan belum tersedia." }
        val size = asset.getValue("size").jsonPrimitive.long
        check(size in 1..80_000_000) { "Ukuran pembaruan tidak valid." }
        AppUpdate(version, info.getValue("versionName").jsonPrimitive.content,
            asset.getValue("browser_download_url").jsonPrimitive.content, size, digest.substringAfter(':'))
    }

    suspend fun download(update: AppUpdate, progress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File.createTempFile("yap-", ".apk", directory)
        val connection = connect(update.url)
        try {
            check(connection.responseCode == 200) { "Unduhan gagal. Coba lagi." }
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            var lastProgress = -1
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(32_768)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        check(size <= update.size) { "Ukuran unduhan tidak cocok." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val percent = (size * 100 / update.size).toInt()
                        if (percent != lastProgress) {
                            progress(percent)
                            lastProgress = percent
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            check(size == update.size && hash.equals(update.sha256, true)) { "Unduhan tidak lengkap. Unduh ulang." }
            verify(file, update.versionCode)
            file
        } catch (error: Throwable) {
            file.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    fun verify(file: File, versionCode: Long) {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val installed = context.packageManager.getPackageInfo(context.packageName, flags)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
            ?: error("Paket pembaruan tidak dapat dibaca.")
        check(candidate.packageName == context.packageName &&
            PackageInfoCompat.getLongVersionCode(candidate) == versionCode &&
            versionCode > PackageInfoCompat.getLongVersionCode(installed)) { "Versi atau aplikasi pembaruan tidak cocok." }
        val currentSigners = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        val newSigners = if (Build.VERSION.SDK_INT >= 28) candidate.signingInfo?.apkContentsSigners else candidate.signatures
        check(!currentSigners.isNullOrEmpty() && !newSigners.isNullOrEmpty() &&
            currentSigners.toSet() == newSigners.toSet()) { "Tanda tangan pembaruan tidak cocok dengan YAP ini." }
    }
}
