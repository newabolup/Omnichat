package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.SettingsRepository
import com.example.model.ApiConfig
import com.example.model.Attachment
import com.example.model.DebugLogEntry
import com.example.model.ModelInfo
import com.example.provider.AIProvider
import com.example.provider.ChatMessagePayload
import com.example.provider.ChatRequest
import com.example.provider.ConnectionResult
import com.example.provider.OpenAICompatibleProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ChatUiState(
    val activeConversationId: String? = null,
    val selectedModel: String = "",
    val inputText: String = "",
    val attachments: List<Attachment> = emptyList(),
    val isGenerating: Boolean = false,
    val models: List<ModelInfo> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsError: String? = null,
    val isTestingConnection: Boolean = false,
    val testConnectionResult: ConnectionResult? = null,
    val editingMessage: MessageEntity? = null
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    val chatRepository = ChatRepository(database.conversationDao(), database.messageDao())
    val settingsRepository = SettingsRepository(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    val config = settingsRepository.config
    val cachedModels = settingsRepository.cachedModels
    val debugLogs = settingsRepository.debugLogs

    val activeConversations = chatRepository.activeConversations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val archivedConversations = chatRepository.archivedConversations.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private var currentStreamJob: Job? = null
    private var activeProvider: AIProvider = OpenAICompatibleProvider(config.value)

    init {
        // Observe config changes to rebuild provider
        viewModelScope.launch {
            config.collectLatest { newConfig ->
                activeProvider = OpenAICompatibleProvider(newConfig)
                if (newConfig.defaultModel.isNotEmpty() && _uiState.value.selectedModel.isEmpty()) {
                    _uiState.value = _uiState.value.copy(selectedModel = newConfig.defaultModel)
                }
            }
        }

        // Initialize models from cache or network
        if (cachedModels.value.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                models = cachedModels.value,
                selectedModel = config.value.defaultModel.ifEmpty { cachedModels.value.firstOrNull()?.id ?: "" }
            )
        }

        if (config.value.isConfigured || config.value.apiKey.isNotEmpty()) {
            refreshModels()
        }
    }

    fun onInputTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun addAttachment(attachment: Attachment) {
        val current = _uiState.value.attachments.toMutableList()
        current.add(attachment)
        _uiState.value = _uiState.value.copy(attachments = current)
    }

    fun removeAttachment(id: String) {
        val current = _uiState.value.attachments.filter { it.id != id }
        _uiState.value = _uiState.value.copy(attachments = current)
    }

    fun clearAttachments() {
        _uiState.value = _uiState.value.copy(attachments = emptyList())
    }

    fun selectModel(modelId: String) {
        _uiState.value = _uiState.value.copy(selectedModel = modelId)
        val activeId = _uiState.value.activeConversationId
        if (activeId != null) {
            viewModelScope.launch {
                chatRepository.updateModel(activeId, modelId)
            }
        }
    }

    fun selectConversation(conversationId: String) {
        currentStreamJob?.cancel()
        _uiState.value = _uiState.value.copy(
            activeConversationId = conversationId,
            isGenerating = false,
            editingMessage = null
        )

        viewModelScope.launch {
            val conv = chatRepository.getConversation(conversationId)
            if (conv != null && conv.model.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(selectedModel = conv.model)
            }
            chatRepository.getMessages(conversationId).collectLatest { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun startNewChat() {
        currentStreamJob?.cancel()
        _uiState.value = _uiState.value.copy(
            activeConversationId = null,
            isGenerating = false,
            inputText = "",
            attachments = emptyList(),
            editingMessage = null
        )
        _messages.value = emptyList()
    }

    fun refreshModels() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingModels = true, modelsError = null)
            val startTime = System.currentTimeMillis()
            val result = activeProvider.getModels()
            val duration = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                val models = result.getOrThrow()
                settingsRepository.saveCachedModels(models)
                val currentSelected = _uiState.value.selectedModel
                val updatedSelected = if (currentSelected.isNotEmpty() && models.any { it.id == currentSelected }) {
                    currentSelected
                } else if (config.value.defaultModel.isNotEmpty() && models.any { it.id == config.value.defaultModel }) {
                    config.value.defaultModel
                } else {
                    models.firstOrNull()?.id ?: ""
                }

                _uiState.value = _uiState.value.copy(
                    models = models,
                    selectedModel = updatedSelected,
                    isLoadingModels = false,
                    modelsError = null
                )

                settingsRepository.addDebugLog(
                    DebugLogEntry(
                        method = "GET",
                        endpoint = "/models",
                        model = "",
                        statusCode = 200,
                        durationMs = duration
                    )
                )
            } else {
                val error = result.exceptionOrNull()?.localizedMessage ?: "Failed to load models"
                _uiState.value = _uiState.value.copy(
                    isLoadingModels = false,
                    modelsError = error
                )
                settingsRepository.addDebugLog(
                    DebugLogEntry(
                        method = "GET",
                        endpoint = "/models",
                        model = "",
                        statusCode = 500,
                        durationMs = duration,
                        error = error
                    )
                )
            }
        }
    }

    fun testConnection(tempConfig: ApiConfig, onResult: (ConnectionResult) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTestingConnection = true, testConnectionResult = null)
            val testProvider = OpenAICompatibleProvider(tempConfig)
            val result = testProvider.testConnection()
            _uiState.value = _uiState.value.copy(
                isTestingConnection = false,
                testConnectionResult = result
            )
            if (result.success && result.models.isNotEmpty()) {
                settingsRepository.saveCachedModels(result.models)
                _uiState.value = _uiState.value.copy(models = result.models)
            }
            onResult(result)
        }
    }

    fun saveConfig(newConfig: ApiConfig) {
        settingsRepository.saveConfig(newConfig)
        activeProvider = OpenAICompatibleProvider(newConfig)
        if (newConfig.defaultModel.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(selectedModel = newConfig.defaultModel)
        }
        refreshModels()
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val attachments = _uiState.value.attachments
        if (text.isEmpty() && attachments.isEmpty()) return
        if (_uiState.value.isGenerating) return

        val currentModel = _uiState.value.selectedModel.ifEmpty {
            config.value.defaultModel.ifEmpty { "default-model" }
        }

        viewModelScope.launch {
            // 1. Ensure conversation exists
            var convId = _uiState.value.activeConversationId
            if (convId == null) {
                val smartTitle = chatRepository.generateSmartTitle(if (text.isNotEmpty()) text else attachments.firstOrNull()?.name ?: "New Chat")
                val conv = chatRepository.createConversation(
                    title = smartTitle,
                    model = currentModel,
                    systemPrompt = config.value.systemPrompt
                )
                convId = conv.id
                _uiState.value = _uiState.value.copy(activeConversationId = convId)
                selectConversation(convId)
            }

            // 2. Serialize attachments to JSON
            val attachmentsJson = if (attachments.isNotEmpty()) {
                val array = JSONArray()
                for (att in attachments) {
                    val obj = JSONObject()
                    obj.put("id", att.id)
                    obj.put("name", att.name)
                    obj.put("sizeBytes", att.sizeBytes)
                    obj.put("mimeType", att.mimeType)
                    obj.put("content", att.content)
                    obj.put("isImage", att.isImage)
                    array.put(obj)
                }
                array.toString()
            } else null

            // 3. Insert User Message
            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = "user",
                content = text,
                createdAt = System.currentTimeMillis(),
                status = "completed",
                attachmentsJson = attachmentsJson
            )
            chatRepository.insertMessage(userMsg)

            // Reset input bar
            _uiState.value = _uiState.value.copy(inputText = "", attachments = emptyList())

            // 4. Create Assistant Placeholder
            val assistantMsgId = UUID.randomUUID().toString()
            val assistantMsg = MessageEntity(
                id = assistantMsgId,
                conversationId = convId,
                role = "assistant",
                content = "",
                createdAt = System.currentTimeMillis(),
                status = "streaming",
                model = currentModel
            )
            chatRepository.insertMessage(assistantMsg)

            // 5. Start streaming response
            executeChatStream(convId, assistantMsgId, currentModel)
        }
    }

    fun regenerateResponse(assistantMessageId: String) {
        if (_uiState.value.isGenerating) return
        val convId = _uiState.value.activeConversationId ?: return

        viewModelScope.launch {
            val msgs = chatRepository.getMessagesSnapshot(convId)
            val targetIdx = msgs.indexOfFirst { it.id == assistantMessageId }
            if (targetIdx == -1) return@launch

            // Delete existing assistant response
            chatRepository.deleteMessage(assistantMessageId)

            // Create new assistant placeholder
            val newAssistantId = UUID.randomUUID().toString()
            val currentModel = _uiState.value.selectedModel.ifEmpty { config.value.defaultModel }
            val newAssistantMsg = MessageEntity(
                id = newAssistantId,
                conversationId = convId,
                role = "assistant",
                content = "",
                createdAt = System.currentTimeMillis(),
                status = "streaming",
                model = currentModel
            )
            chatRepository.insertMessage(newAssistantMsg)

            executeChatStream(convId, newAssistantId, currentModel)
        }
    }

    fun startEditUserMessage(message: MessageEntity) {
        _uiState.value = _uiState.value.copy(
            editingMessage = message,
            inputText = message.content
        )
    }

    fun cancelEditUserMessage() {
        _uiState.value = _uiState.value.copy(
            editingMessage = null,
            inputText = ""
        )
    }

    fun submitEditedMessage() {
        val editing = _uiState.value.editingMessage ?: return
        val newText = _uiState.value.inputText.trim()
        val convId = _uiState.value.activeConversationId ?: return
        if (newText.isEmpty()) return

        viewModelScope.launch {
            // Delete messages after this timestamp to branch
            chatRepository.deleteMessagesAfter(convId, editing.createdAt + 1)

            // Update user message content
            chatRepository.updateMessage(editing.copy(content = newText, updatedAt = System.currentTimeMillis()))

            _uiState.value = _uiState.value.copy(editingMessage = null, inputText = "")

            // Create new assistant placeholder
            val currentModel = _uiState.value.selectedModel.ifEmpty { config.value.defaultModel }
            val newAssistantId = UUID.randomUUID().toString()
            val newAssistantMsg = MessageEntity(
                id = newAssistantId,
                conversationId = convId,
                role = "assistant",
                content = "",
                createdAt = System.currentTimeMillis(),
                status = "streaming",
                model = currentModel
            )
            chatRepository.insertMessage(newAssistantMsg)

            executeChatStream(convId, newAssistantId, currentModel)
        }
    }

    fun stopGeneration() {
        currentStreamJob?.cancel()
        val convId = _uiState.value.activeConversationId ?: return
        viewModelScope.launch {
            val msgs = chatRepository.getMessagesSnapshot(convId)
            val streamingMsg = msgs.find { it.status == "streaming" }
            if (streamingMsg != null) {
                chatRepository.updateMessage(streamingMsg.copy(status = "cancelled", updatedAt = System.currentTimeMillis()))
            }
            _uiState.value = _uiState.value.copy(isGenerating = false)
        }
    }

    private fun executeChatStream(conversationId: String, assistantMessageId: String, model: String) {
        _uiState.value = _uiState.value.copy(isGenerating = true)
        currentStreamJob?.cancel()

        currentStreamJob = viewModelScope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            var accumulatedText = ""
            var promptTokens: Int? = null
            var completionTokens: Int? = null
            var totalTokens: Int? = null
            var errorMessage: String? = null
            var isCancelled = false

            try {
                // Fetch previous messages for context
                val allMsgs = chatRepository.getMessagesSnapshot(conversationId)
                    .filter { it.id != assistantMessageId && it.status != "error" }

                val payloadList = allMsgs.map { msg ->
                    val attachments = parseAttachmentsJson(msg.attachmentsJson)
                    ChatMessagePayload(
                        role = msg.role,
                        text = msg.content,
                        attachments = attachments
                    )
                }

                val request = ChatRequest(
                    messages = payloadList,
                    model = model,
                    systemPrompt = config.value.systemPrompt,
                    temperature = config.value.temperature,
                    maxTokens = config.value.maxTokens,
                    stream = true
                )

                activeProvider.streamChat(request).collect { chunk ->
                    if (chunk.error != null) {
                        errorMessage = chunk.error
                        return@collect
                    }

                    if (chunk.deltaText.isNotEmpty()) {
                        accumulatedText += chunk.deltaText
                        // Periodically / on each chunk update DB entity
                        chatRepository.updateMessage(
                            MessageEntity(
                                id = assistantMessageId,
                                conversationId = conversationId,
                                role = "assistant",
                                content = accumulatedText,
                                createdAt = startTime,
                                updatedAt = System.currentTimeMillis(),
                                status = "streaming",
                                model = model
                            )
                        )
                    }

                    chunk.promptTokens?.let { promptTokens = it }
                    chunk.completionTokens?.let { completionTokens = it }
                    chunk.totalTokens?.let { totalTokens = it }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    isCancelled = true
                } else {
                    errorMessage = e.localizedMessage ?: e.message ?: "Unknown error"
                }
            } finally {
                val duration = System.currentTimeMillis() - startTime
                val finalStatus = when {
                    isCancelled -> "cancelled"
                    errorMessage != null -> "error"
                    else -> "completed"
                }

                val finalMessage = MessageEntity(
                    id = assistantMessageId,
                    conversationId = conversationId,
                    role = "assistant",
                    content = if (accumulatedText.isNotEmpty()) accumulatedText else (errorMessage ?: "Generation stopped"),
                    createdAt = startTime,
                    updatedAt = System.currentTimeMillis(),
                    status = finalStatus,
                    model = model,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    totalTokens = totalTokens,
                    latencyMs = duration,
                    errorMessage = errorMessage
                )
                chatRepository.updateMessage(finalMessage)

                settingsRepository.addDebugLog(
                    DebugLogEntry(
                        method = "POST",
                        endpoint = "/chat/completions",
                        model = model,
                        statusCode = if (errorMessage == null) 200 else 500,
                        durationMs = duration,
                        promptTokens = promptTokens,
                        completionTokens = completionTokens,
                        totalTokens = totalTokens,
                        error = errorMessage
                    )
                )

                _uiState.value = _uiState.value.copy(isGenerating = false)
            }
        }
    }

    private fun parseAttachmentsJson(jsonStr: String?): List<Attachment> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<Attachment>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Attachment(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        name = obj.optString("name", "file"),
                        sizeBytes = obj.optLong("sizeBytes", 0L),
                        mimeType = obj.optString("mimeType", "text/plain"),
                        content = obj.optString("content", ""),
                        isImage = obj.optBoolean("isImage", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch { chatRepository.renameConversation(id, title) }
    }

    fun pinConversation(id: String, isPinned: Boolean) {
        viewModelScope.launch { chatRepository.setPinned(id, isPinned) }
    }

    fun archiveConversation(id: String, isArchived: Boolean) {
        viewModelScope.launch {
            chatRepository.setArchived(id, isArchived)
            if (isArchived && _uiState.value.activeConversationId == id) {
                startNewChat()
            }
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            chatRepository.deleteConversation(id)
            if (_uiState.value.activeConversationId == id) {
                startNewChat()
            }
        }
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch { chatRepository.deleteMessage(id) }
    }

    fun deleteAllConversations() {
        viewModelScope.launch {
            chatRepository.deleteAllConversations()
            startNewChat()
        }
    }

    suspend fun searchConversations(query: String): List<ConversationEntity> {
        return chatRepository.searchConversations(query)
    }

    fun clearDebugLogs() {
        settingsRepository.clearDebugLogs()
    }
}
