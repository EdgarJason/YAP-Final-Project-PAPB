package com.example.yap_finalprojectpapb.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class StudioTurn(
    val id: String,
    val prompt: String,
    val created: Long = System.currentTimeMillis(),
    val apk: ApkBuild? = null,
    val reply: String? = null,
    val error: String? = null,
    val gatewayUrl: String? = null
)

@Serializable
data class StudioConversation(
    val id: String,
    val turns: List<StudioTurn> = emptyList(),
    val updated: Long = System.currentTimeMillis()
)

@Serializable
data class SavedConversations(val activeId: String, val conversations: List<StudioConversation>, val activeTurnId: String? = null)

class StudioConversationStore(context: Context) {
    private val preferences = context.getSharedPreferences("yap_conversations", Context.MODE_PRIVATE)
    private val legacy = GatewaySettingsStore(context)
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): SavedConversations {
        preferences.getString("conversations", null)?.let { return json.decodeFromString(it) }
        val saved = legacy.loadApkBuild()
        val turns = saved?.let {
            listOf(StudioTurn(id = it.id, prompt = it.prompt, gatewayUrl = it.gatewayUrl))
        }.orEmpty()
        val conversation = StudioConversation(UUID.randomUUID().toString(), turns)
        return SavedConversations(conversation.id, listOf(conversation))
    }

    fun save(saved: SavedConversations) {
        check(preferences.edit().putString("conversations", json.encodeToString(saved)).commit()) {
            "Percakapan belum dapat disimpan di perangkat."
        }
    }
}
