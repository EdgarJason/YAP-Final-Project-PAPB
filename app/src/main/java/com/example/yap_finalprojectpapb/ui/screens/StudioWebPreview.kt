package com.example.yap_finalprojectpapb.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Owns the browser for the studio's lifetime, independently of tab composition. */
internal class StudioPreviewState {
    var webView: WebView? = null
    var loadedHtml: String? = null
    var savedHistory: Bundle? = null
    var canGoBack by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun goBack() {
        webView?.let { if (it.canGoBack()) it.goBack() }
    }

    fun reset() {
        webView?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        loadedHtml = null
        savedHistory = null
        canGoBack = false
        loading = false
        error = null
    }

    companion object {
        val StateSaver = Saver<StudioPreviewState, Bundle>(
            save = { state ->
                Bundle().apply {
                    putString("html", state.loadedHtml)
                    val history = state.webView?.let { view -> Bundle().also { view.saveState(it) } }
                        ?: state.savedHistory
                    putBundle("history", history)
                }
            },
            restore = { bundle ->
                StudioPreviewState().apply {
                    loadedHtml = bundle.getString("html")
                    savedHistory = bundle.getBundle("history")
                }
            }
        )
    }
}

@Composable
internal fun rememberStudioPreviewState(): StudioPreviewState {
    val state = rememberSaveable(saver = StudioPreviewState.StateSaver) { StudioPreviewState() }
    DisposableEffect(state) {
        onDispose { state.reset() }
    }
    return state
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun StudioWebPreview(html: String, state: StudioPreviewState, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        state.error?.let { message ->
            Column(Modifier.fillMaxWidth().padding(12.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                TextButton(
                    onClick = { state.error = null; state.webView?.reload() },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Text("Coba lagi") }
            }
        }
        AndroidView(
            factory = { context ->
                // The host may leave composition. The browser remains owned by the studio.
                FrameLayout(context).apply {
                    val browser = state.webView ?: WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                state.loading = true
                                state.error = null
                                state.canGoBack = view.canGoBack()
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                state.loading = false
                                state.canGoBack = view.canGoBack()
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                state.canGoBack = view.canGoBack()
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) {
                                    state.loading = false
                                    state.error = "Pratinjau gagal dimuat. Periksa koneksi lalu coba lagi."
                                }
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame) {
                                    state.loading = false
                                    state.error = "Halaman tidak tersedia (${response.statusCode}). Coba lagi."
                                }
                            }
                        }
                        val restored = state.loadedHtml == html && state.savedHistory?.let { restoreState(it) } != null
                        state.savedHistory = null
                        if (!restored) state.loadedHtml = null
                    }.also { state.webView = it }
                    (browser.parent as? ViewGroup)?.removeView(browser)
                    addView(browser, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    browser.onResume()
                    state.canGoBack = browser.canGoBack()
                }
            },
            update = {
                if (state.loadedHtml != html) {
                    state.loadedHtml = html
                    state.error = null
                    state.webView?.loadDataWithBaseURL("https://yap.local/", html, "text/html", "UTF-8", null)
                }
            },
            onRelease = { host ->
                (host.getChildAt(0) as? WebView)?.onPause()
                host.removeAllViews()
            },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
    }
}
