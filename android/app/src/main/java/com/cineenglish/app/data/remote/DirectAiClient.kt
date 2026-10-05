package com.cineenglish.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object DirectAiClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getPresetModelsForUrl(url: String): List<String> {
        val lower = url.lowercase()
        return when {
            lower.contains("deepseek") -> listOf("deepseek-chat", "deepseek-reasoner")
            lower.contains("dashscope") || lower.contains("aliyun") || lower.contains("qwen") ->
                listOf("qwen-plus", "qwen-turbo", "qwen-max", "qwen-long")
            lower.contains("siliconflow") ->
                listOf("deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "Qwen/Qwen2.5-7B-Instruct")
            lower.contains("moonshot") -> listOf("moonshot-v1-8k", "moonshot-v1-32k")
            lower.contains("openai") -> listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
            else -> listOf("deepseek-chat", "gpt-4o-mini", "qwen-plus", "claude-3-5-sonnet")
        }
    }

    suspend fun fetchModelsDirectly(baseUrl: String, apiKey: String?): List<String> = withContext(Dispatchers.IO) {
        val cleanBase = baseUrl.trimEnd('/')
        val candidateUrls = if (cleanBase.endsWith("/v1")) {
            listOf("$cleanBase/models", "${cleanBase.removeSuffix("/v1")}/models")
        } else {
            listOf("$cleanBase/v1/models", "$cleanBase/models")
        }

        for (url in candidateUrls) {
            try {
                val reqBuilder = Request.Builder().url(url).get()
                if (!apiKey.isNullOrBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
                }
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: return@use
                        val json = JSONObject(bodyStr)
                        val modelList = mutableListOf<String>()
                        if (json.has("data")) {
                            val dataArr = json.getJSONArray("data")
                            for (i in 0 until dataArr.length()) {
                                val item = dataArr.optJSONObject(i)
                                val id = item?.optString("id")
                                if (!id.isNullOrBlank()) {
                                    modelList.add(id)
                                }
                            }
                        }
                        if (modelList.isNotEmpty()) {
                            modelList.sort()
                            return@withContext modelList
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext getPresetModelsForUrl(cleanBase)
    }

    suspend fun testChatDirectly(
        baseUrl: String,
        apiKey: String,
        modelName: String
    ): Result<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val startTs = System.currentTimeMillis()
        val cleanBase = baseUrl.trimEnd('/')
        val candidateUrls = if (cleanBase.endsWith("/v1")) {
            listOf("$cleanBase/chat/completions", "${cleanBase.removeSuffix("/v1")}/chat/completions")
        } else {
            listOf("$cleanBase/v1/chat/completions", "$cleanBase/chat/completions")
        }

        // Auto-fix model name if user selected DeepSeek but model remained gpt-4o-mini
        val effectiveModel = if (cleanBase.contains("deepseek") && modelName == "gpt-4o-mini") {
            "deepseek-chat"
        } else modelName

        val jsonPayload = JSONObject().apply {
            put("model", effectiveModel)
            val messages = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are a concise English dialogue coach.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Reply with one word: 'Connected'.")
                })
            }
            put("messages", messages)
            put("max_tokens", 15)
        }

        val requestBody = jsonPayload.toString().toRequestBody("application/json".toMediaType())

        var lastError = "Could not reach chat/completions endpoint"

        for (url in candidateUrls) {
            try {
                val reqBuilder = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                if (apiKey.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
                }

                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val latency = System.currentTimeMillis() - startTs
                    val bodyStr = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val json = JSONObject(bodyStr)
                        val choices = json.optJSONArray("choices")
                        val reply = choices?.optJSONObject(0)
                            ?.optJSONObject("message")
                            ?.optString("content") ?: "Connected"
                        return@withContext Result.success(Pair(reply.trim(), latency))
                    } else {
                        lastError = "HTTP ${response.code}: $bodyStr"
                    }
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
            }
        }

        return@withContext Result.failure(Exception(lastError))
    }
}
