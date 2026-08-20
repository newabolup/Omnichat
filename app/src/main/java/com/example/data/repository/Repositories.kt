package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.local.AppDatabase
import com.example.data.local.ConversationDao
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageDao
import com.example.data.local.MessageEntity
import com.example.model.ApiConfig
import com.example.model.DebugLogEntry
import com.example.model.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    val activeConversations: Flow<List<ConversationEntity>> = conversationDao.getActiveConversations()
    val archivedConversations: Flow<List<ConversationEntity>> = conversationDao.getArchivedConversations()

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun getMessagesSnapshot(conversationId: String): List<MessageEntity> =
        messageDao.getMessagesSnapshot(conversationId)

    suspend fun getConversation(conversationId: String): ConversationEntity? =
        conversationDao.getConversationById(conversationId)

    fun observeConversation(conversationId: String): Flow<ConversationEntity?> =
        conversationDao.observeConversationById(conversationId)

    suspend fun createConversation(
        title: String = "New Chat",
        model: String = "",
        systemPrompt: String? = null
    ): ConversationEntity {
        val conv = ConversationEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isPinned = false,
            isArchived = false,
            model = model,
            systemPrompt = systemPrompt
        )
        conversationDao.insertConversation(conv)
        return conv
    }

    suspend fun insertMessage(message: MessageEntity) {
        messageDao.insertMessage(message)
        // Update conversation's updatedAt
        conversationDao.getConversationById(message.conversationId)?.let { conv ->
            conversationDao.updateConversation(conv.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateMessage(message: MessageEntity) {
        messageDao.updateMessage(message)
    }

    suspend fun deleteMessage(id: String) {
        messageDao.deleteMessageById(id)
    }

    suspend fun deleteMessagesAfter(conversationId: String, timestamp: Long) {
        messageDao.deleteMessagesAfter(conversationId, timestamp)
    }

    suspend fun renameConversation(id: String, newTitle: String) {
        conversationDao.updateTitle(id, newTitle)
    }

    suspend fun setPinned(id: String, isPinned: Boolean) {
        conversationDao.setPinned(id, isPinned)
    }

    suspend fun setArchived(id: String, isArchived: Boolean) {
        conversationDao.setArchived(id, isArchived)
    }

    suspend fun updateModel(id: String, model: String) {
        conversationDao.updateModel(id, model)
    }

    suspend fun deleteConversation(id: String) {
        messageDao.deleteMessagesForConversation(id)
        conversationDao.deleteConversationById(id)
    }

    suspend fun deleteAllConversations() {
        messageDao.deleteAllMessages()
        conversationDao.deleteAllConversations()
    }

    suspend fun searchConversations(query: String): List<ConversationEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return conversationDao.searchConversations(trimmed)
    }

    fun generateSmartTitle(prompt: String): String {
        val cleaned = prompt.trim()
            .replace(Regex("""^(can you|could you|please|tell me about|how to|what is|how do i|explain)\s+""", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleaned.isEmpty()) return "New Chat"

        // Capitalize words and take first 5-6 words
        val words = cleaned.split(Regex("""\s+""")).take(6)
        val titleCandidate = words.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        return if (titleCandidate.length > 40) {
            titleCandidate.take(37) + "..."
        } else {
            titleCandidate
        }
    }
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("omnichat_settings", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config = _config.asStateFlow()

    private val _cachedModels = MutableStateFlow(loadCachedModels())
    val cachedModels = _cachedModels.asStateFlow()

    private val _debugLogs = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val debugLogs = _debugLogs.asStateFlow()

    private fun loadConfig(): ApiConfig {
        val isConfigured = prefs.getBoolean("is_configured", false)
        return ApiConfig(
            provider = prefs.getString("provider", "openai_compatible") ?: "openai_compatible",
            baseUrl = prefs.getString("base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1",
            apiKey = prefs.getString("api_key", "") ?: "",
            organization = prefs.getString("organization", "") ?: "",
            defaultModel = prefs.getString("default_model", "") ?: "",
            systemPrompt = prefs.getString("system_prompt", "You are a helpful, brilliant, and concise AI assistant.")
                ?: "You are a helpful, brilliant, and concise AI assistant.",
            temperature = prefs.getFloat("temperature", 0.7f),
            maxTokens = if (prefs.contains("max_tokens")) prefs.getInt("max_tokens", 4096) else null,
            enterToSend = prefs.getBoolean("enter_to_send", true),
            showTimestamps = prefs.getBoolean("show_timestamps", true),
            themeMode = prefs.getString("theme_mode", "system") ?: "system",
            debugMode = prefs.getBoolean("debug_mode", false),
            isConfigured = isConfigured
        )
    }

    fun saveConfig(newConfig: ApiConfig) {
        prefs.edit()
            .putString("provider", newConfig.provider)
            .putString("base_url", newConfig.baseUrl)
            .putString("api_key", newConfig.apiKey)
            .putString("organization", newConfig.organization)
            .putString("default_model", newConfig.defaultModel)
            .putString("system_prompt", newConfig.systemPrompt)
            .putFloat("temperature", newConfig.temperature)
            .putBoolean("enter_to_send", newConfig.enterToSend)
            .putBoolean("show_timestamps", newConfig.showTimestamps)
            .putString("theme_mode", newConfig.themeMode)
            .putBoolean("debug_mode", newConfig.debugMode)
            .putBoolean("is_configured", newConfig.isConfigured)
            .apply()

        if (newConfig.maxTokens != null) {
            prefs.edit().putInt("max_tokens", newConfig.maxTokens).apply()
        } else {
            prefs.edit().remove("max_tokens").apply()
        }

        _config.value = newConfig
    }

    fun saveCachedModels(models: List<ModelInfo>) {
        val array = JSONArray()
        for (m in models) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("name", m.name)
            m.ownedBy?.let { obj.put("ownedBy", it) }
            val caps = JSONObject()
            caps.put("vision", m.capabilities.vision)
            caps.put("tools", m.capabilities.tools)
            caps.put("reasoning", m.capabilities.reasoning)
            caps.put("streaming", m.capabilities.streaming)
            obj.put("caps", caps)
            array.put(obj)
        }
        prefs.edit().putString("cached_models_json", array.toString()).apply()
        _cachedModels.value = models
    }

    private fun loadCachedModels(): List<ModelInfo> {
        val jsonStr = prefs.getString("cached_models_json", null) ?: return emptyList()
        val list = mutableListOf<ModelInfo>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.optString("name", id)
                val ownedBy = obj.optString("ownedBy", null)
                val capsObj = obj.optJSONObject("caps")
                val caps = if (capsObj != null) {
                    com.example.model.ModelCapabilities(
                        vision = capsObj.optBoolean("vision", false),
                        tools = capsObj.optBoolean("tools", false),
                        reasoning = capsObj.optBoolean("reasoning", false),
                        streaming = capsObj.optBoolean("streaming", true)
                    )
                } else com.example.model.ModelCapabilities()
                list.add(ModelInfo(id = id, name = name, ownedBy = ownedBy, capabilities = caps))
            }
        } catch (_: Exception) {}
        return list
    }

    fun addDebugLog(entry: DebugLogEntry) {
        val current = _debugLogs.value.toMutableList()
        current.add(0, entry)
        if (current.size > 50) {
            _debugLogs.value = current.take(50)
        } else {
            _debugLogs.value = current
        }
    }

    fun clearDebugLogs() {
        _debugLogs.value = emptyList()
    }
}
