package com.example.yap_finalprojectpapb.domain.repository

import com.example.yap_finalprojectpapb.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChats(): Flow<List<ChatSession>>
    suspend fun saveOrUpdateChat(chat: ChatSession)
    suspend fun getChatById(chatId: String): ChatSession?
    suspend fun deleteChat(chatId: String)
}
