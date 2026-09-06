package com.sitson.vocab.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface MaterialGenerator {
    suspend fun generate(systemPrompt: String, userPrompt: String): String
}

/** Minimal provider-neutral Chat Completions adapter; no provider or model is hard-coded here. */
class OpenAiCompatibleClient(private val config: ProviderConfig) : MaterialGenerator {
    override suspend fun generate(systemPrompt: String, userPrompt: String): String = withContext(Dispatchers.IO) {
        ProviderConfigValidator.validate(config)?.let { error(it) }
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.7)
            .put("max_tokens", 2500)
            .put(
                "messages",
                org.json.JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)),
            ).toString()
        val connection = URL(ProviderConfigValidator.chatCompletionsUrl(config.baseUrl))
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 45_000
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { reader ->
                val chars = CharArray(1_000_001)
                var size = 0
                while (size < chars.size) { val count = reader.read(chars, size, chars.size - size); if (count < 0) break; size += count }
                require(size <= 1_000_000) { "服务返回的内容过大。" }; String(chars, 0, size)
            }.orEmpty()
            if (status !in 200..299) error("Provider request failed (HTTP $status).")
            JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        } finally {
            connection.disconnect()
        }
    }
}
