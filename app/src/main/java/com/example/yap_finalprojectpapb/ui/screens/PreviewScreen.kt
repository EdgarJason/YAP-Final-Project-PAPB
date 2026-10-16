package com.example.yap_finalprojectpapb.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.MascotMood
import com.example.yap_finalprojectpapb.ui.components.YapMascot
import com.example.yap_finalprojectpapb.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PreviewScreen(
    htmlContent: String = "",
    appName: String = "Generated Web App",
    onBackClick: () -> Unit = {},
    onGoToChatClick: () -> Unit = {}
) {
    var isMobileViewport by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val sampleDemoHtml = if (htmlContent.isNotBlank()) htmlContent else """
        <!DOCTYPE html>
        <html lang="id">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>YAP Demo Studio</title>
          <script src="https://cdn.tailwindcss.com"></script>
        </head>
        <body class="bg-slate-50 text-slate-900 font-sans p-6 min-h-screen flex flex-col items-center justify-center">
          <div class="max-w-md w-full bg-white rounded-2xl shadow-sm border border-slate-200 p-6 text-center">
            <div class="w-16 h-16 bg-blue-100 text-blue-600 rounded-2xl flex items-center justify-center mx-auto mb-4 text-2xl font-bold">
              ⚡
            </div>
            <h1 class="text-2xl font-bold text-slate-900 mb-2">Aplikasi Web Aktif</h1>
            <p class="text-slate-600 text-sm mb-6 leading-relaxed">
              Ini adalah pratinjau langsung yang dirender di Android WebView. Ketik instruksi di Chat Studio untuk mengubah fitur!
            </p>
            <div class="space-y-3">
              <button onclick="alert('Halo dari YAP Mobile Web! Tombol berfungsi normal.')" class="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-3 px-4 rounded-xl transition duration-150 shadow-sm active:scale-95">
                Uji Interaksi Tombol
              </button>
              <div id="counter-box" class="p-3 bg-slate-100 rounded-xl flex items-center justify-between">
                <span class="text-sm font-medium text-slate-700">Penghitung Interaktif:</span>
                <span id="counter" class="text-lg font-bold text-blue-600">0</span>
              </div>
              <button onclick="document.getElementById('counter').innerText = parseInt(document.getElementById('counter').innerText) + 1" class="w-full bg-slate-200 hover:bg-slate-300 text-slate-800 font-medium py-2 px-4 rounded-xl transition text-sm">
                + Tambah Angka
              </button>
            </div>
          </div>
        </body>
        </html>
    """.trimIndent()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Neutral100
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Toolbar Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeutralWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Kembali",
                            tint = BlueDarker
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp)
                    ) {
                        Text(
                            text = appName,
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BlueDarker
                        )
                        Text(
                            text = if (isMobileViewport) "Viewport: Ponsel (360px)" else "Viewport: Layar Penuh",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 11.sp,
                            color = Neutral500
                        )
                    }

                    // Toggle Viewport
                    IconButton(onClick = { isMobileViewport = !isMobileViewport }) {
                        Icon(
                            imageVector = if (isMobileViewport) Icons.Outlined.StayCurrentPortrait else Icons.Outlined.Computer,
                            contentDescription = "Ubah Viewport",
                            tint = BlueNormal
                        )
                    }

                    // Reload
                    IconButton(onClick = { reloadTrigger++ }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Muat Ulang",
                            tint = BlueDarker
                        )
                    }
                }
            }

            // Area Tampilan WebView / Empty State
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(if (isMobileViewport) 16.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                val containerModifier = if (isMobileViewport) {
                    Modifier
                        .widthIn(max = 390.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .border(2.dp, Neutral300, RoundedCornerShape(20.dp))
                } else {
                    Modifier.fillMaxSize()
                }

                Box(
                    modifier = containerModifier.background(Color.White)
                ) {
                    key(reloadTrigger) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    webViewClient = WebViewClient()
                                    loadDataWithBaseURL(
                                        "https://yap.local/",
                                        sampleDemoHtml,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            },
                            update = { webView ->
                                webView.loadDataWithBaseURL(
                                    "https://yap.local/",
                                    sampleDemoHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Bottom Action Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = NeutralWhite,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppButton(
                            text = "Buka di Chat",
                            onClick = onGoToChatClick,
                            variant = AppButtonVariant.Secondary,
                            colorStyle = AppButtonColorStyle.Blue,
                            fullWidth = true
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AppButton(
                            text = "Refresh Layar",
                            onClick = { reloadTrigger++ },
                            variant = AppButtonVariant.Primary,
                            colorStyle = AppButtonColorStyle.Blue,
                            fullWidth = true
                        )
                    }
                }
            }
        }
    }
}
