package com.example.yap_finalprojectpapb.domain.model

data class ChatSession(
    val id: String = "",
    val uid: String = "",
    val title: String = "",
    val prompt: String = "",
    val status: String = "idle",
    val apkBuildId: String? = null,
    val gatewayUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
