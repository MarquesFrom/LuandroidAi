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
            ProviderConfig(
                engine = EngineKind.valueOf(saved.optString("engine", EngineKind.OPENAI_COMPATIBLE.name)),
                name = saved.optString("name"),
                baseUrl = saved.optString("baseUrl"),
                model = saved.optString("model"),
                chatBaseUrl = saved.optString("chatBaseUrl"),
                chatModel = saved.optString("chatModel"),
                searchUrl = saved.optString("searchUrl"),
                workflowJson = saved.optString("workflowJson"),
            )
        } catch (error: Exception) {
            throw IllegalStateException("Não foi possível ler as configurações salvas do motor.", error)
        }
    }

    fun save(config: ProviderConfig) {
        val json = JSONObject()
            .put("engine", config.engine.name)
            .put("name", config.name)
            .put("baseUrl", config.baseUrl)
            .put("model", config.model)
            .put("chatBaseUrl", config.chatBaseUrl)
            .put("chatModel", config.chatModel)
            .put("searchUrl", config.searchUrl)
            .put("workflowJson", config.workflowJson)
        check(preferences.edit().putString(CONFIG, json.toString()).commit()) {
            "Não foi possível salvar as configurações do motor."
        }
    }

    private companion object {
        const val CONFIG = "provider_config"
    }
}
