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
            .put("response_format", JSONObject().put("type", "json_object"))
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
            connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) error("Provider request failed (HTTP $status).")
            JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
        } finally {
            connection.disconnect()
        }
    }
}
