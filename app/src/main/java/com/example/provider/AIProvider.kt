package com.example.provider

import com.example.model.Attachment
import com.example.model.ModelInfo
import kotlinx.coroutines.flow.Flow

data class ChatMessagePayload(
    val role: String,
    val text: String,
    val attachments: List<Attachment> = emptyList()
)

data class ChatRequest(
    val messages: List<ChatMessagePayload>,
    val model: String,
    val systemPrompt: String? = null,
    val temperature: Float? = 0.7f,
    val maxTokens: Int? = null,
    val stream: Boolean = true
)

data class ChatStreamChunk(
    val deltaText: String = "",
    val reasoningText: String? = null,
    val isDone: Boolean = false,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val error: String? = null
)

data class ConnectionResult(
    val success: Boolean,
    val message: String,
    val modelCount: Int = 0,
    val latencyMs: Long = 0,
    val models: List<ModelInfo> = emptyList()
)

data class ChatResponse(
    val content: String,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

interface AIProvider {
    suspend fun testConnection(): ConnectionResult
    suspend fun getModels(): Result<List<ModelInfo>>
    fun streamChat(request: ChatRequest): Flow<ChatStreamChunk>
    suspend fun sendChat(request: ChatRequest): Result<ChatResponse>
}
