package com.example.model

import java.util.UUID

enum class MessageRole(val value: String) {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    TOOL("tool");

    companion object {
        fun fromString(value: String): MessageRole =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: USER
    }
}

enum class MessageStatus(val value: String) {
    PENDING("pending"),
    STREAMING("streaming"),
    COMPLETED("completed"),
    ERROR("error"),
    CANCELLED("cancelled");

    companion object {
        fun fromString(value: String): MessageStatus =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: COMPLETED
    }
}

data class Attachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val content: String = "", // Base64 data or plain text
    val isImage: Boolean = false
) {
    val readableSize: String
        get() {
            return when {
                sizeBytes < 1024 -> "$sizeBytes B"
                sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
                else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
            }
        }
}

data class ModelCapabilities(
    val vision: Boolean = false,
    val tools: Boolean = false,
    val reasoning: Boolean = false,
    val streaming: Boolean = true
)

data class ModelInfo(
    val id: String,
    val name: String = id,
    val ownedBy: String? = null,
    val contextLength: Int? = null,
    val capabilities: ModelCapabilities = ModelCapabilities()
)

data class ApiConfig(
    val provider: String = "openai_compatible",
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val organization: String = "",
    val defaultModel: String = "",
    val systemPrompt: String = "You are a helpful, brilliant, and concise AI assistant.",
    val temperature: Float = 0.7f,
    val maxTokens: Int? = null,
    val enterToSend: Boolean = true,
    val showTimestamps: Boolean = true,
    val themeMode: String = "system", // "system", "dark", "light"
    val debugMode: Boolean = false,
    val isConfigured: Boolean = false
)

data class DebugLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val method: String,
    val endpoint: String,
    val model: String,
    val statusCode: Int,
    val durationMs: Long,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val error: String? = null
)
