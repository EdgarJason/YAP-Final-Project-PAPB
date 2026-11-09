package com.example.yap_finalprojectpapb

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.os.SystemClock
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.lifecycle.ViewModelProvider
import com.example.yap_finalprojectpapb.data.GatewaySettings
import com.example.yap_finalprojectpapb.data.GatewaySettingsStore
import com.example.yap_finalprojectpapb.ui.viewmodels.StudioViewModel
import java.net.ServerSocket
import org.json.JSONObject
import kotlin.concurrent.thread

class StudioStateRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var gatewayBackup = emptyMap<String, String?>()
    private var conversationBackup: String? = null
    private var fixture: ServerSocket? = null
    private val model get() = ViewModelProvider(compose.activity)[StudioViewModel::class.java]

    @Before fun openStudio() {
        compose.runOnIdle {
            val context = compose.activity
            val gateway = context.getSharedPreferences("yap_gateway", 0)
            gatewayBackup = listOf("url", "token", "active_apk_build").associateWith { gateway.getString(it, null) }
            conversationBackup = context.getSharedPreferences("yap_conversations", 0).getString("conversations", null)
            model.reset()
        }
        compose.onNode(hasText("Log In") and hasClickAction()).performClick()
        compose.onNode(hasText("Log In") and hasClickAction()).performClick()
    }

    @After fun restoreStoredData() {
        fixture?.close()
        compose.runOnIdle {
            model.reset()
            val gateway = compose.activity.getSharedPreferences("yap_gateway", 0).edit()
            gatewayBackup.forEach { (name, value) -> if (value == null) gateway.remove(name) else gateway.putString(name, value) }
            gateway.commit()
            val saved = compose.activity.getSharedPreferences("yap_conversations", 0).edit()
            if (conversationBackup == null) saved.remove("conversations") else saved.putString("conversations", conversationBackup)
            saved.commit()
        }
    }

    @Test fun draftAndFileSurviveTabsFullscreenAndRecreation() {
        compose.onNode(hasSetTextAction()).performTextInput("Draft yang belum dikirim")
        compose.onNodeWithText("Code").performClick()
        compose.onNodeWithContentDescription("Salin Kode").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertTrue(clipboard.primaryClip!!.getItemAt(0).text.isNotBlank())
        }
        compose.onNodeWithContentDescription("Layar penuh kode").performClick()
        compose.onNode(hasText("index.html") and isSelectable()).assertIsSelected()
        compose.onNodeWithContentDescription("Tutup layar penuh").performClick()
        compose.onNodeWithText("Chat").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Draft yang belum dikirim")
        compose.onNodeWithText("Code").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("index.html") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Chat").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Draft yang belum dikirim")
    }

    @Test fun webDocumentAndHistorySurviveTabsAndFullscreen() {
        preparePreview()
        compose.onNodeWithText("Preview").performClick()
        compose.waitUntil(20_000) { script("document.readyState") == "\"complete\"" }
        val browser = browser()
        // Chromium can skip history entries created without user activation.
        // Exercise a real button tap, as a generated web application would.
        script("""window.studioMarker = 42;
            document.body.innerHTML = '<button style="position:fixed;inset:0" onclick="history.pushState({}, String(), String.fromCharCode(35)+String.fromCharCode(115))">Next</button>'; 'ok'""")
        compose.runOnIdle {
            val time = SystemClock.uptimeMillis()
            browser.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, 20f, 20f, 0).also { it.source = android.view.InputDevice.SOURCE_TOUCHSCREEN })
            browser.dispatchTouchEvent(MotionEvent.obtain(time, time + 100, MotionEvent.ACTION_UP, 20f, 20f, 0).also { it.source = android.view.InputDevice.SOURCE_TOUCHSCREEN })
        }
        compose.waitUntil(5_000) { script("location.hash") == "\"#s\"" }
        compose.onNodeWithText("Code").performClick()
        compose.onNodeWithText("Preview").performClick()
        assertSame(browser, browser())
        assertEquals("42", script("window.studioMarker"))
        assertEquals("\"#s\"", script("location.hash"))
        compose.onNodeWithText("Layar Penuh").performClick()
        assertSame(browser, browser())
        assertEquals("42", script("window.studioMarker"))
        compose.runOnIdle { assertTrue("WebView must have an internal history entry", browser.canGoBack()) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { script("location.hash") == "\"\"" }
        compose.onNodeWithText("Tutup layar penuh").assertExists().performClick()
        assertEquals("42", script("window.studioMarker"))
    }

    @Test fun greetingRepliesImmediatelyAndThePreviousMessageRemains() {
        compose.onNode(hasSetTextAction()).performTextInput("hi")
        compose.onNodeWithContentDescription("Kirim ide").performClick()
        compose.onNodeWithText("hi").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("halo")
        compose.onNodeWithContentDescription("Kirim ide").performClick()
        compose.onNodeWithText("hi").assertExists()
        compose.onNodeWithText("halo").assertExists()
        compose.runOnIdle { assertFalse(model.state.value.loading) }
    }

    private fun preparePreview() {
        val server = ServerSocket(0)
        fixture = server
        thread(isDaemon = true) {
            repeat(2) {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val input = socket.getInputStream().bufferedReader()
                    val route = input.readLine()
                    var length = 0
                    var line = input.readLine()
                    while (!line.isNullOrEmpty()) {
                        if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
                        line = input.readLine()
                    }
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) read += input.read(body, read, length - read)
                    val response = if (route.contains("/preview")) {
                        JSONObject().put("html", "<!DOCTYPE html><html><body>Preview fixture</body></html>")
                    } else {
                        JSONObject().put("id", JSONObject(String(body)).getString("id"))
                            .put("status", "ready").put("sha256", "0".repeat(64)).put("size", 1)
                    }.toString().toByteArray()
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(response)
                    output.flush()
                }
            }
        }
        compose.runOnIdle {
            GatewaySettingsStore(compose.activity).save(GatewaySettings("http://127.0.0.1:${server.localPort}", "fixture-token".repeat(4)))
            model.buildApk("Buat aplikasi uji")
        }
        compose.waitUntil(10_000) { model.state.value.previewHtml != null }
    }

    private fun browser(): WebView {
        fun find(view: View): WebView? {
            if (view is WebView) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                find(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        var result: WebView? = null
        compose.runOnIdle { result = find(compose.activity.window.decorView) }
        return requireNotNull(result)
    }

    private fun script(source: String): String {
        val view = browser()
        val done = CountDownLatch(1)
        var result = ""
        compose.runOnIdle { view.evaluateJavascript(source) { result = it; done.countDown() } }
        assertTrue("WebView JavaScript timed out", done.await(5, TimeUnit.SECONDS))
        return result
    }
}
