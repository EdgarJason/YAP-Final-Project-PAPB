package com.example.yap_finalprojectpapb.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.yap_finalprojectpapb.R
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.components.MascotMood
import com.example.yap_finalprojectpapb.ui.components.YapMascot
import com.example.yap_finalprojectpapb.ui.theme.*
import com.example.yap_finalprojectpapb.ui.viewmodels.AuthViewModel
import com.example.yap_finalprojectpapb.ui.viewmodels.StudioUiState
import com.example.yap_finalprojectpapb.ui.viewmodels.StudioViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatStudioScreen(
    studioViewModel: StudioViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    onMenuClick: () -> Unit = {},
    onApiSettingsClick: () -> Unit = {},
    onUpdateClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onRecentSelect: (String) -> Unit = {}
) {
    val generation by studioViewModel.state.collectAsState()
    val authState by authViewModel.uiState.collectAsState()

    // Data Profile Asli Pengguna (Tersinkronisasi 100% dengan Profile Screen)
    val userName = authState.userProfile?.fullName?.takeIf { it.isNotBlank() } ?: "Pengguna YAP"
    val userEmail = authState.userProfile?.email?.takeIf { it.isNotBlank() } ?: "user@yap.app"
    val photoUrl = authState.userProfile?.photoUrl.orEmpty()
    val initials = userName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase().ifBlank { "Y" }

    var tab by rememberSaveable(generation.conversationId) { mutableIntStateOf(0) }
    var input by rememberSaveable(generation.conversationId) { mutableStateOf("") }
    val previewHtml = generation.previewHtml
    val submitted = generation.prompt
    var downloadTurnId by rememberSaveable { mutableStateOf<String?>(null) }
    val saveApk = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { destination -> destination?.let { studioViewModel.saveApk(it, downloadTurnId) } }
    var confirmNew by remember { mutableStateOf(false) }

    // State Popup Menu Dropdown & Dialogs (Rename & Delete)
    var showMenuDropdown by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var currentChatTitle by remember(submitted) { mutableStateOf(if (submitted.isNotBlank()) submitted else "Chat Utama") }
    var renameInputText by remember { mutableStateOf("") }

    var selectedFile by rememberSaveable { mutableStateOf("index.html") }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val chatScroll = rememberScrollState()
    LaunchedEffect(generation.turns.size, generation.apk?.status, generation.apk?.clarification?.id, chatScroll.maxValue) {
        chatScroll.animateScrollTo(chatScroll.maxValue)
    }
    val awaitingAnswer = generation.apk?.status == "awaiting_answer"
    val inputLimit = if (awaitingAnswer) 1200 else 6000
    LaunchedEffect(generation.apk?.clarification?.answer) {
        val sentAnswer = generation.apk?.clarification?.answer
        if (sentAnswer != null && input.trim() == sentAnswer) input = ""
    }
    val codeScrollStates = rememberSaveableStateHolder()
    val previewState = rememberStudioPreviewState()

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val requester = remember { FocusRequester() }
    val context = LocalContext.current

    val startNew = {
        input = ""
        studioViewModel.reset()
        tab = 0
        selectedFile = "index.html"
        fullscreen = false
        codeScrollStates.removeState("index.html")
        previewState.reset()
        confirmNew = false
    }

    val currentCode = generation.previewHtml ?: "<!-- Kode tersedia setelah pratinjau berhasil dimuat. -->"

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    BackHandler {
        when {
            drawer.isOpen -> scope.launch { drawer.close() }
            tab == 1 && previewState.canGoBack -> previewState.goBack()
            fullscreen -> fullscreen = false
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000L) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(context, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Background Pure White #FFFFFF
    val pureWhiteBg = Color(0xFFFFFFFF)

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                Modifier.widthIn(max = 320.dp),
                drawerContainerColor = pureWhiteBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Bagian Atas Drawer: Header & RECENTS
                    Column {
                        Spacer(Modifier.height(20.dp))

                        // Header Drawer: Mascot + "YAP" (24px Bold) + Close Icon (ic_x)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                YapMascot(
                                    mood = MascotMood.Normal,
                                    size = 36.dp,
                                    showSpeechBubble = false,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = "YAP",
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BlueDarker
                                )
                            }

                            IconButton(
                                modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                                onClick = { scope.launch { drawer.close() } }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_x),
                                    contentDescription = "Tutup menu",
                                    tint = BlueDarker,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(28.dp))

                        // RECENTS Header (Mengarah langsung ke Chat History)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                scope.launch { drawer.close() }
                                onMenuClick()
                            }
                        ) {
                            Text(
                                text = "RECENTS",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Neutral900
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_right),
                                contentDescription = "Recents",
                                tint = Neutral900,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        // Daftar Chat Riwayat Asli dari Seluruh Percakapan (generation.conversations)
                        val recentItems = remember(generation.conversations) {
                            generation.conversations
                                .filter { it.turns.isNotEmpty() }
                                .sortedByDescending { it.updated }
                                .map { conv ->
                                    val firstTurn = conv.turns.firstOrNull { it.reply == null } ?: conv.turns.first()
                                    Pair(conv.id, firstTurn.prompt.take(60))
                                }
                                .take(5)
                        }

                        if (recentItems.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                recentItems.forEach { (convId, title) ->
                                    Text(
                                        text = title,
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Neutral900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch { drawer.close() }
                                                studioViewModel.openConversation(convId)
                                            }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No Recent Chat",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 14.sp,
                                color = Neutral500
                            )
                        }
                    }

                    // Bagian Bawah Drawer: Profile Card dipindah tepat di atas tombol New Chat
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 48.dp)
                    ) {
                        // Profile Card Putih Bersih konsisten dengan Profile Screen
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Neutral200, RoundedCornerShape(16.dp))
                                .clickable {
                                    scope.launch { drawer.close() }
                                    onProfileClick()
                                },
                            color = NeutralWhite,
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(BlueLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (photoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = photoUrl,
                                            contentDescription = "Foto Profil $userName",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = initials,
                                            fontFamily = PlusJakartaSansFamily,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BlueNormal
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = userName,
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Neutral900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = userEmail,
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 12.sp,
                                        color = Neutral500,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Icon(
                                    painter = painterResource(id = R.drawable.ic_chevron_right),
                                    contentDescription = "Profile",
                                    tint = Neutral900,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Tombol New Chat
                        AppButton(
                            text = "New Chat",
                            onClick = {
                                scope.launch { drawer.close() }
                                if (input.isNotBlank()) confirmNew = true else startNew()
                            },
                            variant = AppButtonVariant.Primary,
                            colorStyle = AppButtonColorStyle.Blue
                        )
                    }
                }
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = pureWhiteBg
        ) {
            Column(Modifier.fillMaxSize().imePadding()) {
                if (!fullscreen) {
                    // Header Bar Atas
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                            onClick = { scope.launch { drawer.open() } }
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_menu),
                                contentDescription = "Buka menu",
                                tint = BlueDarker,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Title Dropdown (API Settings)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { onApiSettingsClick() }
                        ) {
                            Text(
                                text = "API Settings",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueDarker
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_down),
                                contentDescription = "Dropdown",
                                tint = BlueDarker,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Actions (New Chat & Options Menu)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Tombol New Chat (Square Pen Icon)
                            IconButton(
                                modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                                onClick = { if (input.isNotBlank()) confirmNew = true else startNew() }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_square_pen),
                                    contentDescription = "New Chat",
                                    tint = BlueDarker,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Tombol 3-Dot Menu (Ellipsis Vertical Icon) untuk Rename & Delete
                            Box {
                                IconButton(
                                    modifier = Modifier.sizeIn(minWidth = 44.dp, minHeight = 44.dp),
                                    onClick = { showMenuDropdown = true }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_ellipsis_vertical),
                                        contentDescription = "Opsi Chat",
                                        tint = BlueDarker,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMenuDropdown,
                                    onDismissRequest = { showMenuDropdown = false },
                                    modifier = Modifier.background(NeutralWhite)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(painter = painterResource(id = R.drawable.ic_pencil), contentDescription = null, tint = BlueDarker, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Rename Chat", fontFamily = PlusJakartaSansFamily, fontSize = 14.sp, color = BlueDarker)
                                            }
                                        },
                                        onClick = {
                                            showMenuDropdown = false
                                            renameInputText = currentChatTitle
                                            showRenameDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(painter = painterResource(id = R.drawable.ic_trash), contentDescription = null, tint = RedNormal, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(8.dp))
                                                Text("Delete Chat", fontFamily = PlusJakartaSansFamily, fontSize = 14.sp, color = RedNormal, fontWeight = FontWeight.SemiBold)
                                            }
                                        },
                                        onClick = {
                                            showMenuDropdown = false
                                            showDeleteDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Main Tab Content (Chat | Preview | Code)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> {
                            // TAB 0: CHAT & IDEATION (Vertikal Ketengah pada State Idle)
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(chatScroll)
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = if (submitted.isEmpty()) Arrangement.Center else Arrangement.Top
                            ) {
                                if (submitted.isEmpty()) {
                                    // Idle Start State: 1. Judul & Subtitle di Atas, 2. Maskot di Tengah, 3. Chips di Bawah Maskot
                                    Spacer(Modifier.height(16.dp))

                                    Text(
                                        text = "YAP Mobile AI Studio",
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueDarker,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(Modifier.height(8.dp))

                                    Text(
                                        text = "Plan & Build interactive web\ndirectly using free AI API!",
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 14.sp,
                                        color = BlueDarker,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(Modifier.height(24.dp))

                                    // Maskot di Tengah dengan Speech Bubble Sapaan Interaktif
                                    YapMascot(
                                        mood = MascotMood.Normal,
                                        size = 140.dp,
                                        showSpeechBubble = true
                                    )

                                    Spacer(Modifier.height(24.dp))

                                    // Prompt Chips di Bawah Maskot
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PromptChipItem(text = "Simple Calculator") {
                                            input = "Buatkan aplikasi web kalkulator sederhana dengan tema bersih."
                                            requester.requestFocus()
                                        }
                                        PromptChipItem(text = "Notes App") {
                                            input = "Buatkan aplikasi web catatan dan to-do list interaktif."
                                            requester.requestFocus()
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    PromptChipItem(text = "Simple Stopwatch") {
                                        input = "Buatkan aplikasi web digital stopwatch lengkap dengan tombol lap dan reset."
                                        requester.requestFocus()
                                    }
                                } else {
                                    generation.turns.forEach { turn ->
                                        key(turn.id) {
                                            Surface(
                                                color = BlueNormal,
                                                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp),
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                                    Text(turn.prompt, fontFamily = PlusJakartaSansFamily, fontSize = 15.sp, color = NeutralWhite, fontWeight = FontWeight.Normal)
                                                }
                                            }

                                            Spacer(Modifier.height(16.dp))

                                            GenerationStatus(
                                                state = if (turn.id == generation.activeTurnId) generation else StudioUiState(
                                                    prompt = turn.prompt, apk = turn.apk, reply = turn.reply, error = turn.error,
                                                    downloading = generation.downloading && generation.downloadingTurnId == turn.id,
                                                    downloadMessage = generation.downloadMessage.takeIf { generation.downloadingTurnId == turn.id }
                                                ),
                                                onRetry = { studioViewModel.retry(turn.id) },
                                                onDownload = {
                                                    downloadTurnId = turn.id
                                                    val name = turn.apk?.appName ?: "Aplikasi-${turn.id.take(8)}"
                                                    val filename = name.replace(Regex("[\\p{Cntrl}/\\\\:*?\"<>|]"), "").trim().take(80)
                                                    saveApk.launch("${filename.ifBlank { "Aplikasi" }}.apk")
                                                },
                                                onPreview = { studioViewModel.loadPreview(turn.id); tab = 1 }
                                            )
                                            Spacer(Modifier.height(20.dp))
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            // TAB 1: LIVE INTERACTIVE PREVIEW
                            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                                Spacer(Modifier.height(16.dp))

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Preview",
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueDarker
                                    )
                                    TextButton(onClick = { fullscreen = !fullscreen }) {
                                        Icon(
                                            imageVector = if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                            contentDescription = if (fullscreen) "Tutup Layar Penuh" else "Layar Penuh",
                                            tint = BlueNormal,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (fullscreen) "Exit Fullscreen" else "Layar Penuh", color = BlueNormal, fontFamily = PlusJakartaSansFamily)
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Box(
                                    Modifier.weight(1f).fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, BlueLightActive, RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                ) {
                                    if (previewHtml != null) StudioWebPreview(
                                        html = previewHtml,
                                        state = previewState,
                                        documentId = generation.previewJobId ?: "default",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    else Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (generation.previewLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                        Text(generation.previewMessage ?: if (generation.previewLoading) "Memuat pratinjau…"
                                            else "Pratinjau hasil aplikasi akan muncul di sini setelah AI selesai membuatnya.",
                                            fontFamily = PlusJakartaSansFamily, fontSize = 15.sp, color = BlueDarker)
                                        if (generation.turns.any { it.apk?.status == "ready" }) {
                                            TextButton(onClick = { studioViewModel.loadPreview() }) { Text("Muat ulang pratinjau", fontFamily = PlusJakartaSansFamily) }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                            }
                        }

                        2 -> {
                            // TAB 2: CODE VIEWER (Source Code Editor)
                            val defaultCss = """
                                /* Custom Styles generated by YAP Studio */
                                body {
                                  margin: 0;
                                  font-family: system-ui, -apple-system, sans-serif;
                                  -webkit-font-smoothing: antialiased;
                                }

                                button:active {
                                  transform: scale(0.98);
                                }
                            """.trimIndent()

                            val defaultJs = """
                                // Application logic generated by YAP
                                document.addEventListener('DOMContentLoaded', () => {
                                  const btn = document.getElementById('btnClick');
                                  if (btn) {
                                    btn.addEventListener('click', () => {
                                      console.log('Button clicked successfully!');
                                    });
                                  }
                                });
                            """.trimIndent()

                            val files = listOf(
                                Pair("index.html", currentCode),
                                Pair("style.css", defaultCss),
                                Pair("app.js", defaultJs)
                            )

                            val activeFileCode = when (selectedFile) {
                                "style.css" -> defaultCss
                                "app.js" -> defaultJs
                                else -> currentCode
                            }

                            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                                Spacer(Modifier.height(16.dp))

                                // Header Section: Title tanpa icon di kanan atas
                                Column {
                                    Text(
                                        "Source Code Editor",
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueDarker
                                    )
                                    Text(
                                        "Berkas: $selectedFile",
                                        fontFamily = PlusJakartaSansFamily,
                                        fontSize = 12.sp,
                                        color = Neutral500
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                // File Switcher Tabs (index.html | style.css | app.js)
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    files.forEach { item ->
                                        FilterChip(
                                            selected = selectedFile == item.first,
                                            onClick = { selectedFile = item.first },
                                            label = {
                                                Text(
                                                    text = item.first,
                                                    fontFamily = PlusJakartaSansFamily,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (selectedFile == item.first) FontWeight.Bold else FontWeight.Medium
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = BlueLight,
                                                selectedLabelColor = BlueNormal,
                                                containerColor = NeutralWhite,
                                                labelColor = Neutral800
                                            )
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // Monospace Dark Slate Code Editor Box (#0F172A)
                                codeScrollStates.SaveableStateProvider(selectedFile) {
                                    Box(
                                        Modifier.weight(1f).fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFF0F172A))
                                            .padding(14.dp)
                                    ) {
                                        val lines = activeFileCode.lines()
                                        Row(
                                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                                        ) {
                                            Column(Modifier.padding(end = 16.dp)) {
                                                lines.indices.forEach { idx ->
                                                    Text(
                                                        "${idx + 1}",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF475569),
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                            Column {
                                                lines.forEach { line ->
                                                    Text(
                                                        if (line.isEmpty()) " " else line,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFFF1F5F9),
                                                        lineHeight = 18.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                // Action Row dengan 2 Icon Button (Copy Icon & Download All ZIP Icon)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Icon Salin Kode (Copy Icon)
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText(selectedFile, activeFileCode)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "$selectedFile disalin!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(BlueLight)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = "Salin Kode Berkas Aktif",
                                            tint = BlueNormal,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Spacer(Modifier.width(24.dp))

                                    // Icon Unduh Semua File (Download ZIP Icon)
                                    IconButton(
                                        onClick = {
                                            Toast.makeText(context, "Mengunduh semua file (ZIP)...", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(BlueLight)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.FileDownload,
                                            contentDescription = "Unduh Semua File (ZIP)",
                                            tint = BlueNormal,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }

                // Bottom Control Area (UX Enhanced Segmented Button & Input)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Kustom Segmented Button UX (Chat | Preview | Code) dengan Animasi Transisi Warni
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFFEBF5FF))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        UxSegmentedTabItem(
                            label = "Chat",
                            iconRes = R.drawable.ic_square_pen,
                            isSelected = tab == 0,
                            onClick = { tab = 0; focus.clearFocus() },
                            modifier = Modifier.weight(1f)
                        )
                        UxSegmentedTabItem(
                            label = "Preview",
                            iconRes = R.drawable.ic_eye,
                            isSelected = tab == 1,
                            onClick = { tab = 1; focus.clearFocus() },
                            modifier = Modifier.weight(1f)
                        )
                        UxSegmentedTabItem(
                            label = "Code",
                            iconRes = R.drawable.ic_pencil,
                            isSelected = tab == 2,
                            onClick = { tab = 2; focus.clearFocus() },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (tab == 0) {
                        Spacer(Modifier.height(12.dp))

                        // Input Bar (Placeholder: "Tulis ide kamu disini!", Multiline Membesar ke Bawah)
                        AppTextField(
                            value = input,
                            onValueChange = { if (it.length <= inputLimit) input = it },
                            placeholder = if (awaitingAnswer) "Jawab pertanyaan AI..." else "Tulis ide kamu disini!",
                            singleLine = false,
                            maxLines = 5,
                            focusRequester = requester,
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_send_horizontal),
                                    contentDescription = "Send",
                                    tint = if (input.isNotBlank() && !generation.loading) BlueNormal else Neutral400,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable(enabled = input.isNotBlank() && !generation.loading) {
                                            if (awaitingAnswer) {
                                                studioViewModel.answerBuild(input.trim())
                                            } else {
                                                studioViewModel.buildApk(input.trim())
                                                input = ""
                                            }
                                            focus.clearFocus()
                                        }
                                )
                            }
                        )

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "YAP is AI and can make mistakes.",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 12.sp,
                            color = Neutral500
                        )
                    }
                }
            }
        }
    }

    // Dialog Konfirmasi New Chat (Tombol Bersampingan Horizontal)
    if (confirmNew) {
        AlertDialog(
            onDismissRequest = { confirmNew = false },
            containerColor = NeutralWhite,
            title = { Text("Mulai Obrolan Baru?", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.Bold, color = BlueDarker) },
            text = { Text("Draf yang belum dikirim akan dibersihkan. Percakapan yang sudah dikirim tetap tersimpan.", fontFamily = PlusJakartaSansFamily, color = Neutral500) },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppButton(
                            text = "Tetap Lanjut",
                            onClick = { confirmNew = false },
                            variant = AppButtonVariant.Secondary,
                            colorStyle = AppButtonColorStyle.Blue,
                            fullWidth = true
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppButton(
                            text = "Mulai Baru",
                            onClick = startNew,
                            variant = AppButtonVariant.Primary,
                            colorStyle = AppButtonColorStyle.Blue,
                            fullWidth = true
                        )
                    }
                }
            },
            dismissButton = null
        )
    }

    // Popup Dialog Rename Chat (Tombol Bersampingan Horizontal)
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = NeutralWhite,
            title = {
                Text(
                    text = "Rename Chat",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueDarker
                )
            },
            text = {
                Column {
                    Text(
                        text = "Ubah nama obrolan ini sesuai keinginan Anda:",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 14.sp,
                        color = Neutral500
                    )
                    Spacer(Modifier.height(12.dp))
                    AppTextField(
                        label = "Nama Obrolan",
                        value = renameInputText,
                        onValueChange = { renameInputText = it },
                        placeholder = "Masukkan nama obrolan..."
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppButton(
                            text = "Cancel",
                            onClick = { showRenameDialog = false },
                            variant = AppButtonVariant.Secondary,
                            colorStyle = AppButtonColorStyle.Blue,
                            fullWidth = true
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppButton(
                            text = "Simpan",
                            onClick = {
                                if (renameInputText.isNotBlank()) {
                                    currentChatTitle = renameInputText.trim()
                                }
                                showRenameDialog = false
                                Toast.makeText(context, "Nama obrolan diperbarui!", Toast.LENGTH_SHORT).show()
                            },
                            variant = AppButtonVariant.Primary,
                            colorStyle = AppButtonColorStyle.Blue,
                            fullWidth = true
                        )
                    }
                }
            },
            dismissButton = null
        )
    }

    // Popup Dialog Delete Chat (Tombol Bersampingan Horizontal dengan Warna Merah)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = NeutralWhite,
            title = {
                Text(
                    text = "Hapus Obrolan Ini?",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedNormal
                )
            },
            text = {
                Text(
                    text = "Apakah Anda yakin ingin menghapus obrolan ini? Seluruh riwayat obrolan ini akan dihapus secara permanen.",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    color = BlueDarker
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AppButton(
                            text = "Cancel",
                            onClick = { showDeleteDialog = false },
                            variant = AppButtonVariant.Secondary,
                            colorStyle = AppButtonColorStyle.Red,
                            fullWidth = true
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        AppButton(
                            text = "Delete",
                            onClick = {
                                showDeleteDialog = false
                                startNew()
                                Toast.makeText(context, "Obrolan berhasil dihapus", Toast.LENGTH_SHORT).show()
                            },
                            variant = AppButtonVariant.Primary,
                            colorStyle = AppButtonColorStyle.Red,
                            fullWidth = true
                        )
                    }
                }
            },
            dismissButton = null
        )
    }
}

@Composable
private fun GenerationStatus(
    state: StudioUiState,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        color = NeutralWhite,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BlueLightActive),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                YapMascot(
                    mood = if (state.loading) MascotMood.Thinking else MascotMood.Happy,
                    size = 42.dp,
                    showSpeechBubble = false,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    when {
                        state.reply != null -> "YAP AI"
                        state.error != null -> "Tugas belum selesai"
                        state.apk?.status == "responded" -> "YAP AI"
                        state.apk?.status == "ready" -> "Aplikasi Web Siap"
                        state.apk?.status == "awaiting_answer" -> "YAP AI Menunggu Jawabanmu"
                        state.apk?.status == "building" -> "YAP AI sedang mengerjakan permintaanmu…"
                        else -> "Menunggu YAP AI…"
                    },
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueDarker
                )
            }
            state.reply?.let { Text(it, fontFamily = PlusJakartaSansFamily, fontSize = 15.sp, color = BlueDarker) }
            state.apk?.message?.let { Text(it, fontFamily = PlusJakartaSansFamily, fontSize = 15.sp, color = BlueDarker) }
            val questions = state.apk?.clarifications.orEmpty().ifEmpty { listOfNotNull(state.apk?.clarification) }
            questions.forEach { clarification ->
                Text("Pertanyaan AI", fontFamily = PlusJakartaSansFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BlueNormal)
                Text(clarification.question, fontFamily = PlusJakartaSansFamily, fontSize = 15.sp, color = BlueDarker)
                clarification.answer?.let {
                    Text("Jawabanmu", fontFamily = PlusJakartaSansFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = BlueNormal)
                    Text(it, fontFamily = PlusJakartaSansFamily, fontSize = 15.sp, color = BlueDarker)
                }
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = BlueNormal)
                Text(
                    if (state.apk?.status == "awaiting_answer") "Mengirim jawaban…" else "YAP AI sedang membuatkan aplikasi web...",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 12.sp,
                    color = Neutral500
                )
            }
            state.error?.let {
                Text(it, color = RedNormal, fontFamily = PlusJakartaSansFamily, fontSize = 13.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("Coba lagi", color = BlueNormal, fontFamily = PlusJakartaSansFamily) }
            }
            if (state.apk?.status == "ready") {
                state.apk.appName?.let { Text(it, fontFamily = PlusJakartaSansFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BlueDarker) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onPreview) { Text("Coba di Preview", fontFamily = PlusJakartaSansFamily, color = BlueNormal) }
                    Button(onClick = onDownload, enabled = !state.downloading, colors = ButtonDefaults.buttonColors(containerColor = BlueNormal)) {
                        Text(if (state.downloading) "Mengunduh…" else "Unduh Web App", fontFamily = PlusJakartaSansFamily)
                    }
                }
                state.downloadMessage?.let { Text(it, fontFamily = PlusJakartaSansFamily, fontSize = 12.sp, color = Neutral500, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        }
    }
}

@Composable
fun PromptChipItem(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFFEBF5FF))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontFamily = PlusJakartaSansFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = BlueDarker
        )
    }
}

@Composable
fun UxSegmentedTabItem(
    label: String,
    iconRes: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) BlueNormal else Color.Transparent,
        label = "tabBgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) NeutralWhite else BlueDarker,
        label = "tabContentColor"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatStudioScreenPreview() {
    YAPFinalProjectPAPBTheme {
        ChatStudioScreen()
    }
}
