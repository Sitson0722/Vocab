package com.sitson.vocab.provider

data class ProviderConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4.1-mini",
    val apiKey: String = "",
)

object ProviderConfigValidator {
    fun validate(config: ProviderConfig): String? {
        val url = runCatching { java.net.URI(config.baseUrl.trim()) }.getOrNull()
            ?: return "Enter a valid provider URL."
        if (url.scheme != "https") return "The provider URL must use HTTPS."
        if (url.host.isNullOrBlank()) return "The provider URL needs a host."
        if (config.model.isBlank()) return "Enter a model name."
        if (config.apiKey.isBlank()) return "Enter an API key."
        return null
    }

    fun chatCompletionsUrl(baseUrl: String): String =
        "${baseUrl.trim().trimEnd('/')}/chat/completions"
}
