package com.example.yap_finalprojectpapb

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.yap_finalprojectpapb.data.*
import com.example.yap_finalprojectpapb.ui.viewmodels.StudioViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/** Explicit opt-in: creates one real cloud job, handled by Muse, never a fixture. */
class CloudBuilderLiveTest {
    @Test fun promptThroughAndroidReceivesMusePreviewAndVerifiedApk() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("cloudLive") == "true")
        val context = instrumentation.targetContext
        val resumeSuffix = InstrumentationRegistry.getArguments().getString("cloudResumeSuffix")
        require(resumeSuffix == null || runCatching { UUID.fromString(resumeSuffix) }.isSuccess)
        val suffix = resumeSuffix ?: UUID.randomUUID().toString()
        val app = object : Application() {
            init { attachBaseContext(context) }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                context.getSharedPreferences("cloud-live-$suffix-$name", mode)
        }
        val settings = GatewaySettingsStore(app).load()
        assertEquals(defaultGatewayUrl, settings.url)
        assertTrue(settings.token.startsWith("guest_"))
        lateinit var model: StudioViewModel
        instrumentation.runOnMainSync {
            model = StudioViewModel(app)
            if (resumeSuffix == null) model.buildApk("Buat APK kalkulator offline bernama Hitung Saku dengan ikon aplikasi sendiri. " +
                "Angka 0-9, desimal, tambah, kurang, kali, bagi, hapus, dan pesan pembagian nol. " +
                "Tampilan rapi dan nyaman di HP; sediakan assets/index.html mandiri agar preview sama dengan APK. " +
                "Gunakan pilihan desain terbaikmu, tidak perlu bertanya lagi untuk prompt uji ini.")
        }
        val id = model.state.value.activeTurnId!!
        Log.i("YAP_CLOUD_LIVE", "Submitted real Muse job $id")
        val output = File(context.getExternalFilesDir(null), "cloud-live-$suffix").apply { mkdirs() }
        File(output, "job-id.txt").writeText(id)
        val deadline = System.currentTimeMillis() + 12 * 60_000
        var lastStatus = ""
        while (System.currentTimeMillis() < deadline) {
            val state = model.state.value
            state.error?.let { fail("Job $id: $it") }
            val job = state.apk
            if (job?.status != null && job.status != lastStatus) {
                lastStatus = job.status
                Log.i("YAP_CLOUD_LIVE", "Job $id status=$lastStatus")
            }
            if (job?.status == "awaiting_answer") fail("Unexpected clarification for job $id: ${job.clarification?.question}")
            if (job?.status == "ready") {
                assertFalse("Result needs an automatic app name", job.appName.isNullOrBlank())
                val gateway = ApkBuildGateway(GatewaySettingsStore(app))
                val html = gateway.preview(settings, id)
                assertNotNull("Muse must provide a preview for this WebView app", html)
                assertTrue(html!!.contains("<html", ignoreCase = true))
                File(output, "preview.html").writeText(html)
                val apk = File(output, "result.apk")
                gateway.download(context, settings, job, Uri.fromFile(apk))
                assertEquals(job.size, apk.length())
                File(output, "evidence.txt").writeText("job=$id\nstatus=ready\nappName=${job.appName}\nsize=${job.size}\nsha256=${job.sha256}\n")
                Log.i("YAP_CLOUD_LIVE", "PASS real Muse build job=$id output=${output.absolutePath}")
                return@runBlocking
            }
            Thread.sleep(5_000)
        }
        fail("Timed out waiting for real Muse job $id; last status=$lastStatus")
    }
}
