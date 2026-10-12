package com.example.yap_finalprojectpapb.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.yap_finalprojectpapb.ui.theme.Neutral500
import com.example.yap_finalprojectpapb.ui.theme.Neutral900
import com.example.yap_finalprojectpapb.ui.theme.NeutralWhite
import com.example.yap_finalprojectpapb.ui.theme.PlusJakartaSansFamily
import com.example.yap_finalprojectpapb.ui.theme.YAPFinalProjectPAPBTheme

data class HistoryItem(
    val id: String,
    val title: String,
    val date: String
)

@Composable
fun ChatHistoryScreen(
    onBackClick: () -> Unit = {},
    onChatSelect: (HistoryItem) -> Unit = {},
    onNewChatClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isEmptyState by remember { mutableStateOf(false) }

    val todayList = remember {
        listOf(
            HistoryItem("1", "Kalkulator Sederhana", "10/10/2026 - 00:00"),
            HistoryItem("2", "Kalkulator Sederhana", "10/10/2026 - 00:00"),
            HistoryItem("3", "Kalkulator Sederhana", "10/10/2026 - 00:00")
        )
    }

    val yesterdayList = remember {
        listOf(
            HistoryItem("4", "Kalkulator Sederhana", "10/10/2026 - 00:00"),
            HistoryItem("5", "Kalkulator Sederhana", "10/10/2026 - 00:00"),
            HistoryItem("6", "Kalkulator Sederhana", "10/10/2026 - 00:00")
        )
    }

    val olderList = remember {
        listOf(
            HistoryItem("7", "Kalkulator Sederhana", "10/10/2026 - 00:00"),
            HistoryItem("8", "Kalkulator Sederhana", "10/10/2026 - 00:00"),
            HistoryItem("9", "Kalkulator Sederhana", "10/10/2026 - 00:00")
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = NeutralWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            HeaderWithTitle(
                title = "Chat History",
                onBackClick = onBackClick
            )

            if (!isEmptyState) {
                Spacer(modifier = Modifier.height(16.dp))

                // Search Field dengan Icon Search di Kanan
                AppTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Placeholder",
                    trailingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = "Search",
                            tint = Neutral900,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        HistoryGroupSection(
                            title = "Today",
                            items = todayList,
                            onItemClick = onChatSelect
                        )
                    }

                    item {
                        HistoryGroupSection(
                            title = "Yesterday",
                            items = yesterdayList,
                            onItemClick = onChatSelect
                        )
                    }

                    item {
                        HistoryGroupSection(
                            title = "Older Chats",
                            items = olderList,
                            onItemClick = onChatSelect
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            } else {
                // Empty Chat History State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Search Document Placeholder
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(BlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = "Not Found",
                            tint = BlueNormal,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Chat not found. Start chatting\nto see chat history..",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 16.sp,
                        color = Neutral900,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    AppButton(
                        text = "New Chat",
                        onClick = onNewChatClick,
                        variant = AppButtonVariant.Primary,
                        colorStyle = AppButtonColorStyle.Blue
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryGroupSection(
    title: String,
    items: List<HistoryItem>,
    onItemClick: (HistoryItem) -> Unit
) {
    Column {
        Text(
            text = title,
            fontFamily = PlusJakartaSansFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Neutral900,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                HistoryCardRow(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
fun HistoryCardRow(
    item: HistoryItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = BlueLight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = item.title,
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Neutral900
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.date,
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 12.sp,
                    color = Neutral500
                )
            }

            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = "Open",
                tint = Neutral900,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChatHistoryScreenPreview() {
    YAPFinalProjectPAPBTheme {
        ChatHistoryScreen()
    }
}
