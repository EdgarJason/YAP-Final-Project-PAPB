package com.example.yap_finalprojectpapb.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.yap_finalprojectpapb.data.StudioConversation
import com.example.yap_finalprojectpapb.ui.components.AppButton
import com.example.yap_finalprojectpapb.ui.components.AppTextField
import com.example.yap_finalprojectpapb.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryItem(val id: String, val title: String, val date: String)

@Composable
fun ChatHistoryScreen(
    conversations: List<StudioConversation> = emptyList(),
    onBackClick: () -> Unit = {},
    onChatSelect: (HistoryItem) -> Unit = {},
    onNewChatClick: () -> Unit = {}
) {
    var search by remember { mutableStateOf("") }
    val format = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.forLanguageTag("id")) }
    val entries = conversations.filter { it.turns.isNotEmpty() }.sortedByDescending { it.updated }.map {
        val title = (it.turns.firstOrNull { turn -> turn.reply == null } ?: it.turns.first()).prompt
        HistoryItem(it.id, title.take(100), format.format(Date(it.updated)))
    }
    val results = entries.filter { it.title.contains(search, ignoreCase = true) }
    Surface(Modifier.fillMaxSize(), color = NeutralWhite) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(4.dp))
            HeaderWithTitle(title = "Riwayat percakapan", onBackClick = onBackClick)
            AppTextField(value = search, onValueChange = { search = it }, placeholder = "Cari percakapan")
            if (results.isEmpty()) {
                Text(if (entries.isEmpty()) "Percakapanmu akan tersimpan di sini." else "Tidak ada percakapan yang cocok.",
                    color = Neutral500, style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(results, key = { it.id }) { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onChatSelect(item) },
                        shape = RoundedCornerShape(16.dp), color = BlueLight
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall, color = BlueDarker)
                            Text(item.date, style = MaterialTheme.typography.bodySmall, color = Neutral500)
                        }
                    }
                }
            }
            AppButton(text = "Obrolan baru", onClick = onNewChatClick)
            Spacer(Modifier.height(8.dp))
        }
    }
}
