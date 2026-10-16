package com.example.yap_finalprojectpapb

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StudioStateRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun draftAndFileSurviveTabsFullscreenAndRecreation() {
        compose.onNode(hasSetTextAction()).performTextInput("Draft yang belum dikirim")
        compose.onNodeWithText("Code").performClick()
        compose.onNodeWithText("style.css").performClick()
        compose.onNodeWithContentDescription("Salin Kode").assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertTrue(clipboard.primaryClip!!.getItemAt(0).text.contains("belum ada CSS terpisah"))
        }
        compose.onNodeWithContentDescription("Layar penuh kode").performClick()
        compose.onNode(hasText("style.css") and isSelectable()).assertIsSelected()
        compose.onNodeWithContentDescription("Tutup layar penuh").performClick()
        compose.onNodeWithText("Chat").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Draft yang belum dikirim")
        compose.onNodeWithText("Code").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("style.css") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Chat").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Draft yang belum dikirim")
    }

    @Test fun webDocumentAndHistorySurviveTabsAndFullscreen() {
        compose.onNodeWithText("Preview").performClick()
        compose.waitUntil(20_000) { script("document.readyState") == "\"complete\"" }
        val browser = browser()
        script("window.studioMarker = 42; history.pushState({}, '', '#second'); 'ok'")
        compose.onNodeWithText("Code").performClick()
        compose.onNodeWithText("Preview").performClick()
        assertSame(browser, browser())
        assertEquals("42", script("window.studioMarker"))
        assertEquals("\"#second\"", script("location.hash"))
        compose.onNodeWithText("Layar Penuh").performClick()
        assertSame(browser, browser())
        assertEquals("42", script("window.studioMarker"))
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { script("location.hash") == "\"\"" }
        compose.onNodeWithText("Tutup layar penuh").assertExists().performClick()
        assertEquals("42", script("window.studioMarker"))
    }

    @Test fun pipelineStartsCollapsedAndExposesAccessibleDetails() {
        compose.onNode(hasSetTextAction()).performTextInput("Contoh aplikasi")
        compose.onNodeWithContentDescription("Kirim ide").performClick()
        compose.onNodeWithText("1. Architect", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Rincian tahapan").assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithText("1. Architect", substring = true).assertExists()
        compose.onNodeWithText("5. Strix", substring = true).assertExists()
        compose.onNodeWithText("Sembunyikan tahapan").performClick()
        compose.onNodeWithText("1. Architect", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Preview").assertHeightIsAtLeast(48.dp)
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
