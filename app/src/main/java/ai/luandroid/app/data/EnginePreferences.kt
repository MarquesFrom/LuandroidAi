package ai.luandroid.app.data

import android.content.Context
import org.json.JSONObject

@Suppress("UseKtx")
class EnginePreferences(context: Context) {
    private val preferences = context.getSharedPreferences("engine_config", Context.MODE_PRIVATE)

    fun load(): ProviderConfig {
        val json = preferences.getString(CONFIG, null) ?: return ProviderConfig()
        return try {
            val saved = JSONObject(json)
            val previousChatUrl = saved.optString("chatBaseUrl")
            val previousChatModel = saved.optString("chatModel")
            ProviderConfig(
                baseUrl = saved.optString("baseUrl").takeUnless {
                    it.isBlank() || it.contains("api.openai.com") || it.endsWith("/v1")
                }?.let { if (it == "http://192.168.1.10:8188") DEFAULT_COMFY_URL else it }
                    ?: DEFAULT_COMFY_URL,
                chatBaseUrl = previousChatUrl.takeUnless {
                    it.isBlank() || it.contains("api.openai.com") || it.endsWith("/v1")
                } ?: DEFAULT_OLLAMA_URL,
                chatModel = previousChatModel.takeUnless {
                    it !in SUPPORTED_CHAT_MODELS
                } ?: DEFAULT_OLLAMA_MODEL,
                workflowJson = saved.optString("workflowJson"),
                generationParametersJson = saved.optString("generationParametersJson"),
            )
        } catch (error: Exception) {
            throw IllegalStateException("Não foi possível ler as configurações salvas do motor.", error)
        }
    }

    fun save(config: ProviderConfig) {
        val json = JSONObject()
            .put("baseUrl", config.baseUrl)
            .put("chatBaseUrl", config.chatBaseUrl)
            .put("chatModel", config.chatModel)
            .put("workflowJson", config.workflowJson)
            .put("generationParametersJson", config.generationParametersJson)
        check(preferences.edit().putString(CONFIG, json.toString()).commit()) {
            "Não foi possível salvar as configurações do motor."
        }
    }

    private companion object {
        const val CONFIG = "provider_config"
    }
}
