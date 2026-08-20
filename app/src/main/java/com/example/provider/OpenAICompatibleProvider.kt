package com.example.provider

import com.example.model.ApiConfig
import com.example.model.ModelCapabilities
import com.example.model.ModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenAICompatibleProvider(
    private val config: ApiConfig,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : AIProvider {

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return if (trimmed.isEmpty()) "https://api.openai.com/v1" else trimmed
    }

    private fun createBaseRequestBuilder(url: String): Request.Builder {
        val builder = Request.Builder().url(url)
        val apiKey = config.apiKey.trim()
        if (apiKey.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }
        if (config.organization.trim().isNotEmpty()) {
            builder.addHeader("OpenAI-Organization", config.organization.trim())
        }
        builder.addHeader("User-Agent", "OmniChat-Android/1.0")
        return builder
    }

    override suspend fun testConnection(): ConnectionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            val modelsResult = getModels()
            val latency = System.currentTimeMillis() - startTime
            if (modelsResult.isSuccess) {
                val models = modelsResult.getOrThrow()
                ConnectionResult(
                    success = true,
                    message = "Connection successful. Discovered ${models.size} models.",
                    modelCount = models.size,
                    latencyMs = latency,
                    models = models
                )
            } else {
                val errorMsg = modelsResult.exceptionOrNull()?.message ?: "Unknown error"
                ConnectionResult(
                    success = false,
                    message = errorMsg,
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ConnectionResult(
                success = false,
                message = "Unable to connect: ${e.localizedMessage ?: e.message}",
                latencyMs = latency
            )
        }
    }

    override suspend fun getModels(): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = normalizeBaseUrl(config.baseUrl)
            val url = if (baseUrl.endsWith("/models")) baseUrl else "$baseUrl/models"
            val request = createBaseRequestBuilder(url).get().build()

            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = parseErrorMessage(response.code, bodyString)
                return@withContext Result.failure(IOException(errorMsg))
            }

            val models = parseModelsJson(bodyString)
            if (models.isEmpty()) {
                // Return fallback if list empty
                return@withContext Result.success(listOf(
                    ModelInfo(id = config.defaultModel.ifEmpty { "default-model" })
                ))
            }
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseModelsJson(jsonString: String): List<ModelInfo> {
        val list = mutableListOf<ModelInfo>()
        try {
            val root = JSONObject(jsonString)
            val dataArray = when {
                root.has("data") -> root.optJSONArray("data")
                root.has("models") -> root.optJSONArray("models")
                else -> null
            }

            if (dataArray != null) {
                for (i in 0 until dataArray.length()) {
                    val item = dataArray.optJSONObject(i)
                    if (item != null) {
                        val id = item.optString("id", "")
                        if (id.isNotEmpty()) {
                            val ownedBy = item.optString("owned_by", null)
                            list.add(ModelInfo(
                                id = id,
                                name = id,
                                ownedBy = ownedBy,
                                capabilities = inferCapabilities(id)
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Try array at root
            try {
                val array = JSONArray(jsonString)
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i)
                    val id = item?.optString("id", "") ?: array.optString(i, "")
                    if (id.isNotEmpty()) {
                        list.add(ModelInfo(
                            id = id,
                            name = id,
                            capabilities = inferCapabilities(id)
                        ))
                    }
                }
            } catch (_: Exception) {}
        }
        return list.sortedBy { it.id }
    }

    private fun inferCapabilities(modelId: String): ModelCapabilities {
        val lower = modelId.lowercase()
        val hasVision = lower.contains("vision") || lower.contains("4o") ||
                lower.contains("gemini") || lower.contains("claude") ||
                lower.contains("vl") || lower.contains("pixtral")
        val hasReasoning = lower.contains("r1") || lower.contains("o1") ||
                lower.contains("o3") || lower.contains("reason") ||
                lower.contains("deepseek-r") || lower.contains("qwq")
        val hasTools = !lower.contains("instruct") || lower.contains("turbo") || lower.contains("4") || lower.contains("gpt")

        return ModelCapabilities(
            vision = hasVision,
            tools = hasTools,
            reasoning = hasReasoning,
            streaming = true
        )
    }

    override fun streamChat(request: ChatRequest): Flow<ChatStreamChunk> = callbackFlow {
        val baseUrl = normalizeBaseUrl(config.baseUrl)
        val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

        val jsonPayload = buildChatPayload(request, stream = true)
        val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val httpRequest = createBaseRequestBuilder(url)
            .post(body)
            .addHeader("Accept", "text/event-stream")
            .build()

        val call = client.newCall(httpRequest)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(ChatStreamChunk(error = "Network error: ${e.localizedMessage ?: e.message}", isDone = true))
                close()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    val parsedErr = parseErrorMessage(response.code, errBody)
                    trySend(ChatStreamChunk(error = parsedErr, isDone = true))
                    close()
                    return
                }

                val responseBody = response.body
                if (responseBody == null) {
                    trySend(ChatStreamChunk(error = "Empty response body from server", isDone = true))
                    close()
                    return
                }

                val reader = BufferedReader(InputStreamReader(responseBody.byteStream(), Charsets.UTF_8))
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line?.trim() ?: continue
                        if (currentLine.isEmpty() || currentLine.startsWith(":")) {
                            continue // SSE comment / ping
                        }

                        if (currentLine.startsWith("data:")) {
                            val data = currentLine.removePrefix("data:").trim()
                            if (data == "[DONE]") {
                                trySend(ChatStreamChunk(isDone = true))
                                break
                            }

                            val chunk = parseStreamChunk(data)
                            if (chunk != null) {
                                trySend(chunk)
                            }
                        }
                    }
                    trySend(ChatStreamChunk(isDone = true))
                } catch (e: Exception) {
                    if (!call.isCanceled()) {
                        trySend(ChatStreamChunk(error = "Stream interrupted: ${e.localizedMessage ?: e.message}", isDone = true))
                    }
                } finally {
                    try {
                        reader.close()
                        responseBody.close()
                    } catch (_: Exception) {}
                    close()
                }
            }
        })

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun sendChat(request: ChatRequest): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = normalizeBaseUrl(config.baseUrl)
            val url = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"

            val jsonPayload = buildChatPayload(request, stream = false)
            val body = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

            val httpRequest = createBaseRequestBuilder(url).post(body).build()
            val response = client.newCall(httpRequest).execute()
            val responseText = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException(parseErrorMessage(response.code, responseText)))
            }

            val json = JSONObject(responseText)
            val choices = json.optJSONArray("choices")
            val firstChoice = choices?.optJSONObject(0)
            val messageObj = firstChoice?.optJSONObject("message")
            val content = messageObj?.optString("content", "") ?: ""

            val usageObj = json.optJSONObject("usage")
            val promptTokens = usageObj?.optInt("prompt_tokens")
            val completionTokens = usageObj?.optInt("completion_tokens")
            val totalTokens = usageObj?.optInt("total_tokens")

            Result.success(ChatResponse(
                content = content,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildChatPayload(request: ChatRequest, stream: Boolean): JSONObject {
        val payload = JSONObject()
        payload.put("model", request.model)
        payload.put("stream", stream)

        if (stream) {
            val streamOptions = JSONObject()
            streamOptions.put("include_usage", true)
            payload.put("stream_options", streamOptions)
        }

        request.temperature?.let { payload.put("temperature", it) }
        request.maxTokens?.let { payload.put("max_tokens", it) }

        val messagesArray = JSONArray()

        // System prompt
        val sysPrompt = request.systemPrompt ?: config.systemPrompt
        if (sysPrompt.isNotBlank()) {
            val sysObj = JSONObject()
            sysObj.put("role", "system")
            sysObj.put("content", sysPrompt)
            messagesArray.put(sysObj)
        }

        // Messages
        for (msg in request.messages) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)

            val imageAttachments = msg.attachments.filter { it.isImage && it.content.isNotEmpty() }
            val textAttachments = msg.attachments.filter { !it.isImage && it.content.isNotEmpty() }

            var fullText = msg.text
            if (textAttachments.isNotEmpty()) {
                val attachedDocs = textAttachments.joinToString("\n\n") {
                    "--- Attached Document: ${it.name} ---\n${it.content}\n--- End of Document ---"
                }
                fullText = if (fullText.isNotBlank()) "$attachedDocs\n\n$fullText" else attachedDocs
            }

            if (imageAttachments.isNotEmpty()) {
                val contentArray = JSONArray()
                if (fullText.isNotBlank()) {
                    val textObj = JSONObject()
                    textObj.put("type", "text")
                    textObj.put("text", fullText)
                    contentArray.put(textObj)
                }

                for (img in imageAttachments) {
                    val imgObj = JSONObject()
                    imgObj.put("type", "image_url")
                    val urlObj = JSONObject()
                    val dataUrl = if (img.content.startsWith("data:")) img.content else "data:${img.mimeType};base64,${img.content}"
                    urlObj.put("url", dataUrl)
                    imgObj.put("image_url", urlObj)
                    contentArray.put(imgObj)
                }
                msgObj.put("content", contentArray)
            } else {
                msgObj.put("content", fullText)
            }

            messagesArray.put(msgObj)
        }

        payload.put("messages", messagesArray)
        return payload
    }

    private fun parseStreamChunk(jsonString: String): ChatStreamChunk? {
        return try {
            val json = JSONObject(jsonString)
            val choices = json.optJSONArray("choices")
            var deltaContent = ""
            var reasoningContent: String? = null

            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.optJSONObject(0)
                val delta = firstChoice?.optJSONObject("delta")
                if (delta != null) {
                    if (delta.has("content") && !delta.isNull("content")) {
                        deltaContent = delta.optString("content", "")
                    }
                    if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                        reasoningContent = delta.optString("reasoning_content", null)
                    } else if (delta.has("thought") && !delta.isNull("thought")) {
                        reasoningContent = delta.optString("thought", null)
                    }
                } else if (firstChoice?.has("text") == true) {
                    deltaContent = firstChoice.optString("text", "")
                }
            }

            val usage = json.optJSONObject("usage")
            val promptTokens = usage?.optInt("prompt_tokens")
            val completionTokens = usage?.optInt("completion_tokens")
            val totalTokens = usage?.optInt("total_tokens")

            ChatStreamChunk(
                deltaText = deltaContent,
                reasoningText = reasoningContent,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
                totalTokens = totalTokens
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseErrorMessage(statusCode: Int, responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            if (json.has("error")) {
                val err = json.optJSONObject("error")
                if (err != null && err.has("message")) {
                    return err.getString("message")
                }
                if (json.optString("error").isNotEmpty()) {
                    return json.optString("error")
                }
            }
            if (json.has("message")) {
                return json.getString("message")
            }
            when (statusCode) {
                401 -> "Authentication failed (401). Please verify your API Key."
                403 -> "Access forbidden (403). Check your account permissions or organization."
                404 -> "Endpoint not found (404). Verify your Base URL."
                429 -> "Rate limit exceeded (429). Please wait a moment or check your quota."
                500, 502, 503 -> "Server error ($statusCode). The provider API is temporarily unavailable."
                else -> "HTTP Error $statusCode: ${responseBody.take(120)}"
            }
        } catch (_: Exception) {
            when (statusCode) {
                401 -> "Authentication failed (401). Please verify your API Key."
                403 -> "Access forbidden (403). Check your account permissions."
                404 -> "Endpoint not found (404). Verify your Base URL."
                429 -> "Rate limit exceeded (429). Please wait a moment or check your quota."
                500, 502, 503 -> "Provider server error ($statusCode)."
                else -> "HTTP $statusCode error occurred."
            }
        }
    }
}
