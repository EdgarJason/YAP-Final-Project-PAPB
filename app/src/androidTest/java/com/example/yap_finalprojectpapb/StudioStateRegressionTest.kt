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
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StudioStateRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before fun openStudio() {
        compose.onNode(hasText("Log In") and hasClickAction()).performClick()
        compose.onNode(hasText("Log In") and hasClickAction()).performClick()
    }

    @Test fun draftAndFileSurviveTabsFullscreenAndRecreation() {
        compose.onNode(hasSetTextAction()).performTextInput("Draft yang belum dikirim")
        compose.onNodeWithText("Code").performClick()
        compose.onNodeWithContentDescription("Salin Kode").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertTrue(clipboard.primaryClip!!.getItemAt(0).text.contains("Kode hasil Muse Spark"))
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

    @Test fun missingConfigurationShowsAnActionableError() {
        compose.runOnIdle {
            compose.activity.getSharedPreferences("yap_gateway", Context.MODE_PRIVATE).edit().clear().commit()
        }
        compose.onNode(hasSetTextAction()).performTextInput("Contoh aplikasi")
        compose.onNodeWithContentDescription("Kirim ide").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Atur alamat server di Pengaturan AI terlebih dahulu.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Coba lagi").assertIsDisplayed()
        compose.onNodeWithText("Rincian pembuatan").performClick()
        compose.onNodeWithText("Model: Muse Spark 1.3", substring = true).assertExists()
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
