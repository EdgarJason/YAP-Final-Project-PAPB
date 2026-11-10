package com.example.yap_finalprojectpapb.data.repository

import android.content.Context
import com.example.yap_finalprojectpapb.domain.model.ChatSession
import com.example.yap_finalprojectpapb.domain.repository.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

class ChatRepositoryImpl(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChatRepository {

    private val prefs = context.applicationContext.getSharedPreferences("yap_chat_history", Context.MODE_PRIVATE)

    companion object {
        private const val USERS_COLLECTION = "users"
        private const val CHATS_COLLECTION = "chats"
    }

    override fun observeChats(): Flow<List<ChatSession>> = callbackFlow {
        var firestoreListener: ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            firestoreListener?.remove()
            val uid = firebaseAuth.currentUser?.uid ?: "guest"
            // Emit cached local history immediately
            trySend(loadLocalChats(uid))

            if (firebaseAuth.currentUser != null) {
                firestoreListener = firestore.collection(USERS_COLLECTION)
                    .document(uid)
                    .collection(CHATS_COLLECTION)
                    .orderBy("updatedAt", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) {
                            trySend(loadLocalChats(uid))
                            return@addSnapshotListener
                        }
                        val remoteChats = snapshot.documents.mapNotNull { doc ->
                            val id = doc.getString("id") ?: doc.id
                            val prompt = doc.getString("prompt") ?: return@mapNotNull null
                            val title = doc.getString("title")?.takeIf { it.isNotBlank() } ?: deriveTitle(prompt)
                            val status = doc.getString("status") ?: "idle"
                            val apkBuildId = doc.getString("apkBuildId")
                            val gatewayUrl = doc.getString("gatewayUrl")
                            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                            val updatedAt = doc.getLong("updatedAt") ?: createdAt
                            ChatSession(
                                id = id,
                                uid = uid,
                                title = title,
                                prompt = prompt,
                                status = status,
                                apkBuildId = apkBuildId,
                                gatewayUrl = gatewayUrl,
                                createdAt = createdAt,
                                updatedAt = updatedAt
                            )
                        }
                        val merged = mergeChats(loadLocalChats(uid), remoteChats)
                        saveLocalChats(uid, merged)
                        trySend(merged)
                    }
            }
        }

        auth.addAuthStateListener(authListener)
        awaitClose {
            firestoreListener?.remove()
            auth.removeAuthStateListener(authListener)
        }
    }

    override suspend fun saveOrUpdateChat(chat: ChatSession) {
        val uid = auth.currentUser?.uid ?: chat.uid.ifEmpty { "guest" }
        val normalized = chat.copy(
            uid = uid,
            title = chat.title.ifBlank { deriveTitle(chat.prompt) },
            updatedAt = System.currentTimeMillis()
        )

        // Always persist locally immediately so UI is responsive even offline
        val currentLocal = loadLocalChats(uid).toMutableList()
        val existingIdx = currentLocal.indexOfFirst { it.id == normalized.id }
        if (existingIdx >= 0) {
            currentLocal[existingIdx] = normalized.copy(createdAt = currentLocal[existingIdx].createdAt)
        } else {
            currentLocal.add(0, normalized)
        }
        saveLocalChats(uid, currentLocal.sortedByDescending { it.updatedAt })

        // Sync to Cloud Firestore if authenticated
        if (auth.currentUser != null) {
            withTimeoutOrNull(4_000L) {
                runCatching {
                    firestore.collection(USERS_COLLECTION)
                        .document(uid)
                        .collection(CHATS_COLLECTION)
                        .document(normalized.id)
                        .set(
                            mapOf(
                                "id" to normalized.id,
                                "uid" to normalized.uid,
                                "title" to normalized.title,
                                "prompt" to normalized.prompt,
                                "status" to normalized.status,
                                "apkBuildId" to normalized.apkBuildId,
                                "gatewayUrl" to normalized.gatewayUrl,
                                "createdAt" to normalized.createdAt,
                                "updatedAt" to normalized.updatedAt
                            ),
                            SetOptions.merge()
                        )
                        .await()
                }
            }
        }
    }

    override suspend fun getChatById(chatId: String): ChatSession? {
        val uid = auth.currentUser?.uid ?: "guest"
        loadLocalChats(uid).firstOrNull { it.id == chatId }?.let { return it }
        if (auth.currentUser != null) {
            val doc = withTimeoutOrNull(4_000L) {
                runCatching {
                    firestore.collection(USERS_COLLECTION)
                        .document(uid)
                        .collection(CHATS_COLLECTION)
                        .document(chatId)
                        .get()
                        .await()
                }.getOrNull()
            }
            if (doc != null && doc.exists()) {
                val prompt = doc.getString("prompt") ?: return null
                return ChatSession(
                    id = doc.getString("id") ?: doc.id,
                    uid = uid,
                    title = doc.getString("title") ?: deriveTitle(prompt),
                    prompt = prompt,
                    status = doc.getString("status") ?: "idle",
                    apkBuildId = doc.getString("apkBuildId"),
                    gatewayUrl = doc.getString("gatewayUrl"),
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                )
            }
        }
        return null
    }

    override suspend fun deleteChat(chatId: String) {
        val uid = auth.currentUser?.uid ?: "guest"
        val updated = loadLocalChats(uid).filterNot { it.id == chatId }
        saveLocalChats(uid, updated)
        if (auth.currentUser != null) {
            withTimeoutOrNull(4_000L) {
                runCatching {
                    firestore.collection(USERS_COLLECTION)
                        .document(uid)
                        .collection(CHATS_COLLECTION)
                        .document(chatId)
                        .delete()
                        .await()
                }
            }
        }
    }

    private fun deriveTitle(prompt: String): String {
        val cleaned = prompt.trim().replace(Regex("\\s+"), " ")
        return if (cleaned.length <= 36) cleaned else cleaned.take(36).trimEnd() + "…"
    }

    private fun mergeChats(local: List<ChatSession>, remote: List<ChatSession>): List<ChatSession> {
        val map = LinkedHashMap<String, ChatSession>()
        remote.forEach { map[it.id] = it }
        local.forEach { localItem ->
            val existing = map[localItem.id]
            if (existing == null || localItem.updatedAt > existing.updatedAt) {
                map[localItem.id] = localItem
            }
        }
        return map.values.sortedByDescending { it.updatedAt }
    }

    private fun loadLocalChats(uid: String): List<ChatSession> {
        val raw = prefs.getString("chats_$uid", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        ChatSession(
                            id = obj.optString("id"),
                            uid = obj.optString("uid", uid),
                            title = obj.optString("title"),
                            prompt = obj.optString("prompt"),
                            status = obj.optString("status", "idle"),
                            apkBuildId = obj.optString("apkBuildId").takeIf { it.isNotBlank() },
                            gatewayUrl = obj.optString("gatewayUrl").takeIf { it.isNotBlank() },
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
            }.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    private fun saveLocalChats(uid: String, chats: List<ChatSession>) {
        val array = JSONArray()
        chats.forEach { chat ->
            array.put(
                JSONObject()
                    .put("id", chat.id)
                    .put("uid", chat.uid)
                    .put("title", chat.title)
                    .put("prompt", chat.prompt)
                    .put("status", chat.status)
                    .put("apkBuildId", chat.apkBuildId ?: "")
                    .put("gatewayUrl", chat.gatewayUrl ?: "")
                    .put("createdAt", chat.createdAt)
                    .put("updatedAt", chat.updatedAt)
            )
        }
        prefs.edit().putString("chats_$uid", array.toString()).apply()
    }
}
