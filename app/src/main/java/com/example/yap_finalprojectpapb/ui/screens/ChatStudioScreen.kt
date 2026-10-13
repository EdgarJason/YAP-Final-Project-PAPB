package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yap_finalprojectpapb.R
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppButtonColorStyle
import com.example.yap_finalprojectpapb.ui.components.AppButtonVariant
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.theme.BlueDarker
import com.example.yap_finalprojectpapb.ui.theme.BlueLight
import com.example.yap_finalprojectpapb.ui.theme.BlueNormal
import com.example.yap_finalprojectpapb.ui.theme.Neutral200
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.Neutral900
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.RedNormal
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme
import kotlinx.coroutines.launch

enum class StudioTab {
    Chat,
    Preview,
    Code
}

enum class ChatState {
    Start,
    Processing,
    Response,
    NoInternet
}

@Composable
fun ChatStudioScreen(
    onMenuClick: () -> Unit = {},
    onApiSettingsClick: () -> Unit = {},
    onNewChatClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onRecentSelect: (String) -> Unit = {}
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(StudioTab.Chat) }
    var chatState by remember { mutableStateOf(ChatState.Start) }
    var inputMessage by remember { mutableStateOf("") }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFFEFF6FF), // Light blue drawer background #EFF6FF
                modifier = Modifier.width(300.dp)
            ) {
                DrawerSheetContent(
                    onCloseClick = {
                        coroutineScope.launch { drawerState.close() }
                    },
                    onProfileClick = {
                        coroutineScope.launch { drawerState.close() }
                        onProfileClick()
                    },
                    onRecentClick = { title ->
                        coroutineScope.launch { drawerState.close() }
                        onRecentSelect(title)
                    },
                    onNewChatClick = {
                        coroutineScope.launch { drawerState.close() }
                        onNewChatClick()
                    }
                )
            }
        }
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = NeutralWhite
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sandwich Menu Button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BlueNormal)
                            .clickable {
                                onMenuClick()
                                coroutineScope.launch { drawerState.open() }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_menu),
                            contentDescription = "Menu",
                            tint = NeutralWhite,
                            modifier = Modifier.size(20.dp)
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
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_down),
                            contentDescription = "Dropdown",
                            tint = BlueDarker,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Actions (New Chat & More)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNewChatClick) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_square_pen),
                                contentDescription = "New Chat",
                                tint = BlueDarker,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(onClick = { }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_ellipsis_vertical),
                                contentDescription = "More",
                                tint = BlueDarker,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Main Tab Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeTab) {
                        StudioTab.Chat -> StudioChatContent(
                            chatState = chatState,
                            onStateChange = { chatState = it }
                        )
                        StudioTab.Preview -> StudioPreviewContent()
                        StudioTab.Code -> StudioCodeContent()
                    }
                }

                // Bottom Control Area
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Segmented Tab Selector (Chat | Preview | Code)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(BlueLight)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StudioTabItem(
                            label = "Chat",
                            isSelected = activeTab == StudioTab.Chat,
                            onClick = { activeTab = StudioTab.Chat },
                            modifier = Modifier.weight(1f)
                        )
                        StudioTabItem(
                            label = "Preview",
                            isSelected = activeTab == StudioTab.Preview,
                            onClick = { activeTab = StudioTab.Preview },
                            modifier = Modifier.weight(1f)
                        )
                        StudioTabItem(
                            label = "Code",
                            isSelected = activeTab == StudioTab.Code,
                            onClick = { activeTab = StudioTab.Code },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (activeTab == StudioTab.Chat) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Message Input Field
                        AppTextField(
                            value = inputMessage,
                            onValueChange = { inputMessage = it },
                            placeholder = "Placeholder",
                            trailingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_send_horizontal),
                                    contentDescription = "Send",
                                    tint = BlueNormal,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            if (inputMessage.isNotBlank()) {
                                                chatState = ChatState.Processing
                                                inputMessage = ""
                                            }
                                        }
                                )
                            }
                        )

                        Spacer(modifier = Modifier.height(6.dp))

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
}

@Composable
fun DrawerSheetContent(
    onCloseClick: () -> Unit,
    onProfileClick: () -> Unit,
    onRecentClick: (String) -> Unit,
    onNewChatClick: () -> Unit
) {
    val recentItems = remember {
        listOf(
            "Kalkulator Sederhana",
            "Aplikasi Deteksi Katarak",
            "App UI/UX ala Prabowo"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Spacer(modifier = Modifier.height(24.dp))

            // Header Drawer: Logo Mascot + YAP + Close Icon (ic_x)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_mascot),
                        contentDescription = "YAP Mascot Logo",
                        modifier = Modifier.size(40.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "YAP",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueDarker
                    )
                }

                IconButton(onClick = onCloseClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_x),
                        contentDescription = "Close",
                        tint = BlueDarker,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Profile Card White Rounded Box
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onProfileClick() },
                color = NeutralWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Neutral200)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prabowo Subianto",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Neutral900
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "prabowo@gmail.com",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 12.sp,
                            color = Neutral500
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

            Spacer(modifier = Modifier.height(32.dp))

            // RECENTS Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { }
            ) {
                Text(
                    text = "RECENTS",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Neutral900
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = "Recents",
                    tint = Neutral900,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recent List Items
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                recentItems.forEach { title ->
                    Text(
                        text = title,
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = Neutral900,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRecentClick(title) }
                    )
                }
            }
        }

        // Tombol New Chat di bagian paling bawah dengan jarak 48px dari bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp)
        ) {
            AppButton(
                text = "New Chat",
                onClick = onNewChatClick,
                variant = AppButtonVariant.Primary,
                colorStyle = AppButtonColorStyle.Blue
            )
        }
    }
}

@Composable
fun StudioChatContent(
    chatState: ChatState,
    onStateChange: (ChatState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (chatState) {
            ChatState.Start -> {
                Spacer(modifier = Modifier.height(40.dp))

                Image(
                    painter = painterResource(id = R.drawable.ic_mascot),
                    contentDescription = "YAP Mascot",
                    modifier = Modifier.size(120.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "YAP Mobile AI Studio",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueDarker
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Plan & Build interactive web\ndirectly using free AI API!",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 14.sp,
                    color = BlueDarker,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Prompt Chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PromptChip(text = "Simple Calculator", onClick = { onStateChange(ChatState.Processing) })
                    PromptChip(text = "Notes App", onClick = { onStateChange(ChatState.Processing) })
                }
                Spacer(modifier = Modifier.height(8.dp))
                PromptChip(text = "Simple Stopwatch", onClick = { onStateChange(ChatState.Processing) })

                Spacer(modifier = Modifier.weight(1f))

                // Floating Banner Notice
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BlueLight)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Before chatting, setup your free AI API\nat \"API Settings\" above",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = BlueDarker,
                        textAlign = TextAlign.Center
                    )
                }
            }

            ChatState.Processing -> {
                Spacer(modifier = Modifier.height(20.dp))

                // Sent Message Bubble
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                        .background(BlueNormal)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Saya ingin membuat aplikasi\nberbasis web yang berisikan\nkalkulator sederhana",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 15.sp,
                        color = NeutralWhite
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Processing Indicator
                Row(
                    modifier = Modifier.align(Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_mascot),
                        contentDescription = "Mascot",
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Processing....",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Neutral900
                    )
                }
            }

            ChatState.Response -> {
                Spacer(modifier = Modifier.height(20.dp))

                // User Sent Bubble
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                        .background(BlueNormal)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Saya ingin membuat aplikasi\nberbasis web yang berisikan\nkalkulator sederhana",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 15.sp,
                        color = NeutralWhite
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // AI Response Bubble
                Row(
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_mascot),
                        contentDescription = "Mascot",
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                            .background(BlueLight)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Saya telah membuatkan aplikasi berbasis web yang berisikan kalkulator sederhana. Silakan buka tab Preview atau Code.",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 15.sp,
                            color = Neutral900
                        )
                    }
                }
            }

            ChatState.NoInternet -> {
                Spacer(modifier = Modifier.height(20.dp))

                // User Message
                Box(
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                        .background(BlueNormal)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Saya ingin membuat aplikasi\nberbasis web yang berisikan\nkalkulator sederhana",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 15.sp,
                        color = NeutralWhite
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // No Internet Notice
                Row(
                    modifier = Modifier.align(Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_triangle_alert),
                        contentDescription = "Alert",
                        tint = RedNormal,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "No Internet Connection!",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedNormal
                        )
                        Text(
                            text = "Drag down to refresh",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 13.sp,
                            color = Neutral500
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudioPreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Preview",
            fontFamily = PlusJakartaSansFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BlueDarker
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Black Preview Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        )
    }
}

@Composable
fun StudioCodeContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Source Code Single File",
            fontFamily = PlusJakartaSansFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = BlueDarker
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Code Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons Row (Copy Code & Download .html)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AppButton(
                    text = "Copy Code",
                    onClick = { },
                    variant = AppButtonVariant.Secondary,
                    colorStyle = AppButtonColorStyle.Blue,
                    fullWidth = true
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                AppButton(
                    text = "Download .html",
                    onClick = { },
                    variant = AppButtonVariant.Secondary,
                    colorStyle = AppButtonColorStyle.Blue,
                    fullWidth = true
                )
            }
        }
    }
}

@Composable
fun PromptChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BlueLight)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            fontFamily = PlusJakartaSansFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Neutral900
        )
    }
}

@Composable
fun StudioTabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) BlueNormal else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFamily,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) NeutralWhite else Neutral900
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatStudioScreenPreview() {
    YAPFinalProjectPAPBTheme {
        ChatStudioScreen()
    }
}
