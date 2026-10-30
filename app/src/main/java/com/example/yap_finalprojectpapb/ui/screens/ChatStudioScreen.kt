package com.example.yap_finalprojectpapb.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap_finalprojectpapb.ui.components.MascotMood
import com.example.yap_finalprojectpapb.ui.components.YapMascot
import com.example.yap_finalprojectpapb.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap_finalprojectpapb.ui.viewmodels.StudioViewModel
import com.example.yap_finalprojectpapb.ui.viewmodels.StudioUiState
import kotlinx.coroutines.launch

@Composable
fun ChatStudioScreen(
    studioViewModel: StudioViewModel = viewModel(),
    onMenuClick: () -> Unit = {},
    onApiSettingsClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onRecentSelect: (String) -> Unit = {}
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var input by rememberSaveable { mutableStateOf("") }
    val generation by studioViewModel.state.collectAsState()
    val submitted = generation.prompt
    var confirmNew by remember { mutableStateOf(false) }

    // Actual provider request state is owned by the ViewModel.
    var showLogDetail by rememberSaveable { mutableStateOf(false) }
    var selectedFile by rememberSaveable { mutableStateOf("index.html") }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val chatScroll = rememberScrollState()
    val codeScrollStates = rememberSaveableStateHolder()
    val previewState = rememberStudioPreviewState()

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val type = MaterialTheme.typography
    val context = LocalContext.current

    val startNew = {
        input = ""
        studioViewModel.reset()
        tab = 0
        showLogDetail = false
        selectedFile = "index.html"
        fullscreen = false
        codeScrollStates.removeState("index.html")
        previewState.reset()
        confirmNew = false
    }

    val defaultHtml = generation.html.ifBlank {
        """<!DOCTYPE html><html lang="id"><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body style="font-family:sans-serif;padding:24px;color:#042e59"><h2>Pratinjau aplikasi</h2><p>Kirim ide di tab Chat. Hasil Muse Spark akan tampil di sini setelah selesai.</p></body></html>"""
    }
    val currentCode = generation.html.ifBlank { "<!-- Kode hasil Muse Spark akan tampil setelah aplikasi selesai dibuat. -->" }

    BackHandler(drawer.isOpen || fullscreen || (tab == 1 && previewState.canGoBack)) {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            tab == 1 && previewState.canGoBack -> previewState.goBack()
            fullscreen -> fullscreen = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                Modifier.widthIn(max = 320.dp),
                drawerContainerColor = NeutralWhite
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    YapMascot(
                        size = 36.dp,
                        showSpeechBubble = false,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Text("Workspace Kamu", Modifier.weight(1f), style = type.titleLarge, fontWeight = FontWeight.Bold, color = BlueDarker)
                    IconButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), onClick = { scope.launch { drawer.close() } }) {
                        Icon(Icons.Outlined.Close, "Tutup menu", tint = BlueDarker)
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Neutral200)
                Spacer(Modifier.height(8.dp))
                NavigationDrawerItem(
                    label = { Text("Obrolan Baru", fontWeight = FontWeight.Medium) },
                    selected = false,
                    icon = { Icon(Icons.Outlined.Add, null, tint = BlueNormal) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = {
                        scope.launch { drawer.close() }
                        if (input.isNotBlank() || submitted.isNotBlank()) confirmNew = true else startNew()
                    }
                )
                NavigationDrawerItem(
                    label = { Text("Riwayat Percakapan", fontWeight = FontWeight.Medium) },
                    selected = false,
                    icon = { Icon(Icons.Outlined.History, null, tint = BlueNormal) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = { scope.launch { drawer.close() }; onMenuClick() }
                )
                NavigationDrawerItem(
                    label = { Text("Pengaturan AI", fontWeight = FontWeight.Medium) },
                    selected = false,
                    icon = { Icon(Icons.Outlined.Tune, null, tint = BlueNormal) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    onClick = { scope.launch { drawer.close() }; onApiSettingsClick() }
                )
                Spacer(Modifier.weight(1f))
                HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Neutral200)
                NavigationDrawerItem(
                    label = { Text("Profil Akun", fontWeight = FontWeight.Medium) },
                    selected = false,
                    icon = { Icon(Icons.Outlined.PersonOutline, null, tint = BlueNormal) },
                    modifier = Modifier.padding(12.dp),
                    onClick = { scope.launch { drawer.close() }; onProfileClick() }
                )
            }
        }
    ) {
        Surface(color = Neutral100, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.imePadding()) {
                if (!fullscreen) {
                    // Header Bar Atas
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = NeutralWhite,
                        shadowElevation = 1.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), onClick = { scope.launch { drawer.open() } }) {
                                Icon(Icons.Outlined.Menu, "Buka menu", tint = BlueDarker)
                            }
                            YapMascot(
                                size = 36.dp,
                                showSpeechBubble = false,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text("YAP Studio", style = type.titleMedium, fontWeight = FontWeight.ExtraBold, color = BlueDarker)
                                Text("Mobile AI Web App Builder", style = type.bodySmall, color = Neutral500)
                            }
                            IconButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), onClick = onApiSettingsClick) {
                                Icon(Icons.Outlined.Tune, "Pengaturan AI", tint = BlueDarker)
                            }
                            IconButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), onClick = { if (input.isNotBlank() || submitted.isNotBlank()) confirmNew = true else startNew() }) {
                                Icon(Icons.Outlined.Add, "Chat Baru", tint = BlueNormal)
                            }
                        }
                    }

                    // Tab Switcher (Chat | Preview | Code)
                    Row(
                        Modifier.fillMaxWidth().background(NeutralWhite).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Chat", "Preview", "Code").forEachIndexed { index, label ->
                            FilterChip(
                                modifier = Modifier.heightIn(min = 48.dp),
                                selected = tab == index,
                                onClick = { tab = index; focus.clearFocus() },
                                label = { Text(label, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Medium) },
                                leadingIcon = {
                                    Icon(
                                        listOf(Icons.Outlined.ChatBubbleOutline, Icons.Outlined.Web, Icons.Outlined.Code)[index],
                                        null,
                                        Modifier.size(18.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BlueLight,
                                    selectedLabelColor = BlueNormal
                                )
                            )
                        }
                    }
                }

                // Konten Sesuai Tab Terpilih
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> {
                            // TAB 0: CHAT & IDEATION
                            Column(
                                Modifier.fillMaxSize().verticalScroll(chatScroll).padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (submitted.isEmpty()) {
                                    // Keadaan Awal (Idle)
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        "Mulai dari ide kecil.\nKita buat jadi nyata.",
                                        style = type.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueDarker,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Jelaskan aplikasi web yang kamu inginkan. Muse Spark akan menulis kodenya untukmu.",
                                        style = type.bodyMedium,
                                        color = Neutral500,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(Modifier.height(16.dp))

                                    YapMascot(
                                        mood = MascotMood.Normal,
                                        size = 140.dp
                                    )

                                    Spacer(Modifier.height(20.dp))
                                    Column(Modifier.fillMaxWidth()) {
                                        Text("Butuh inspirasi ide?", style = type.titleSmall, fontWeight = FontWeight.Bold, color = BlueDarker)
                                        Spacer(Modifier.height(8.dp))
                                        IdeaRow(Icons.Outlined.Calculate, "Kalkulator Portabel", "Hitung cepat dengan tombol besar dan riwayat") {
                                            input = "Buatkan aplikasi kalkulator web dengan riwayat perhitungan dan tema bersih."; requester.requestFocus()
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        IdeaRow(Icons.Outlined.EditNote, "Catatan Pribadi", "Simpan catatan dengan kategori warna dan pencarian") {
                                            input = "Buatkan aplikasi to-do list dan catatan dengan kategori warna dan filter pencarian."; requester.requestFocus()
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        IdeaRow(Icons.Outlined.Timer, "Stopwatch Presisi", "Catat waktu dan putaran putaran lap") {
                                            input = "Buatkan stopwatch digital lengkap dengan tombol lap, reset, dan export waktu."; requester.requestFocus()
                                        }
                                    }
                                } else {
                                    // Keadaan Saat Ide Disubmit (Pipeline Berjalan)
                                    Surface(
                                        color = BlueLight,
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("Ide Kamu:", style = type.labelMedium, color = BlueNormal, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(4.dp))
                                            Text(submitted, style = type.bodyLarge, color = BlueDarker, fontWeight = FontWeight.Medium)
                                        }
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    GenerationStatus(
                                        state = generation,
                                        onRetry = studioViewModel::retry,
                                        expanded = showLogDetail,
                                        onToggle = { showLogDetail = !showLogDetail },
                                        onPreview = { tab = 1 },
                                        onCode = { tab = 2 }
                                    )
                                }
                            }
                        }

                        1 -> {
                            // TAB 1: LIVE INTERACTIVE PREVIEW
                            Column(Modifier.fillMaxSize()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = NeutralWhite,
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Pratinjau Interaktif WebView",
                                            Modifier.weight(1f),
                                            style = type.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = BlueDarker
                                        )
                                        TextButton(onClick = { fullscreen = !fullscreen }, modifier = Modifier.heightIn(min = 48.dp)) {
                                            Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, null, Modifier.size(18.dp), tint = BlueNormal)
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (fullscreen) "Tutup layar penuh" else "Layar Penuh", color = BlueNormal)
                                        }
                                    }
                                }

                                Box(
                                    Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, Neutral300, RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                ) {
                                    StudioWebPreview(
                                        html = defaultHtml,
                                        state = previewState,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        2 -> {
                            // TAB 2: CODE VIEWER
                            Column(Modifier.fillMaxSize()) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = NeutralWhite,
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(selectedFile, Modifier.weight(1f), style = type.titleSmall, color = BlueDarker)

                                        IconButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText(selectedFile, currentCode)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "$selectedFile disalin!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Icon(Icons.Outlined.ContentCopy, "Salin Kode", tint = BlueNormal)
                                        }

                                        IconButton(modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), onClick = { fullscreen = !fullscreen }) {
                                            Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (fullscreen) "Tutup layar penuh" else "Layar penuh kode", tint = BlueDarker)
                                        }
                                    }
                                }

                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("index.html").forEach { file ->
                                        FilterChip(
                                            selected = selectedFile == file,
                                            onClick = { selectedFile = file },
                                            label = { Text(file) },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        )
                                    }
                                }
                                codeScrollStates.SaveableStateProvider(selectedFile) {
                                    Box(
                                        Modifier.weight(1f).fillMaxWidth().padding(12.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF0F172A))
                                            .padding(14.dp)
                                    ) {
                                        val lines = currentCode.lines()
                                        Row(
                                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                                        ) {
                                            Column(Modifier.padding(end = 12.dp)) {
                                                lines.indices.forEach { idx ->
                                                    Text("${idx + 1}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF94A3B8))
                                                }
                                            }
                                            Column {
                                                lines.forEach { line ->
                                                    Text(if (line.isEmpty()) " " else line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFFF1F5F9))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Input Bar Bawah (Khusus Tab Chat)
                if (tab == 0) {
                    Surface(color = NeutralWhite, shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.fillMaxWidth().focusRequester(requester),
                                placeholder = { Text("Ketik ide aplikasi web yang ingin kamu buat...", fontSize = 14.sp) },
                                maxLines = 4,
                                isError = input.length > 6000,
                                supportingText = {
                                    if (input.length > 6000) Text("Instruksi maksimal 6000 karakter.")
                                },
                                shape = RoundedCornerShape(14.dp),
                                trailingIcon = {
                                    IconButton(
                                        enabled = input.isNotBlank() && input.length <= 6000 && !generation.loading,
                                        modifier = Modifier.size(48.dp),
                                        onClick = {
                                            showLogDetail = false
                                            studioViewModel.generate(input.trim())
                                            input = ""
                                            focus.clearFocus()
                                        }
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Outlined.Send,
                                            "Kirim ide",
                                            tint = if (input.isNotBlank()) BlueNormal else Neutral400
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            title = { Text("Mulai Obrolan Baru?", fontWeight = FontWeight.Bold) },
            text = { Text("Ide dan aplikasi yang sedang dibuat saat ini akan dibersihkan dari layar studio.") },
            confirmButton = { TextButton(onClick = startNew, modifier = Modifier.heightIn(min = 48.dp)) { Text("Mulai Baru", color = Color(0xFFEF4444)) } },
            dismissButton = { TextButton(onClick = { confirmNew = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Tetap Lanjut") } }
        )
    }
}

@Composable
private fun GenerationStatus(
    state: StudioUiState,
    onRetry: () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    onCode: () -> Unit
) {
    Surface(color = NeutralWhite, shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Neutral200), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                YapMascot(mood = if (state.loading) MascotMood.Thinking else MascotMood.Happy,
                    size = 56.dp, showSpeechBubble = false)
                Text(when {
                    state.loading -> "Muse Spark sedang menulis aplikasi…"
                    state.error != null -> "Aplikasi belum berhasil diperbarui"
                    else -> "Aplikasi siap dicoba"
                }, modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.titleSmall, color = BlueDarker)
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Proses dapat memerlukan beberapa menit.", style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Coba lagi") }
            }
            TextButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (expanded) "Sembunyikan rincian" else "Rincian pembuatan")
            }
            if (expanded) {
                Text("Model: Muse Spark 1.3\nKode HTML, CSS, dan JavaScript digabung dalam index.html. Hasil belum menjalani pengujian otomatis.",
                    style = MaterialTheme.typography.bodySmall, color = Neutral500)
            }
            if (state.html.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPreview, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Coba Aplikasi") }
                    OutlinedButton(onClick = onCode, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Lihat Kode") }
                }
            }
        }
    }
}

@Composable
private fun IdeaRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = NeutralWhite,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Neutral200)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = BlueNormal)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = BlueDarker)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Neutral500)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = Neutral400)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatStudioScreenPreview() {
    com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme {
        ChatStudioScreen()
    }
}
