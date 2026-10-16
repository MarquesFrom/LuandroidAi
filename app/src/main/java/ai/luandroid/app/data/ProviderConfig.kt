package ai.luandroid.app.data

data class ProviderConfig(
    val baseUrl: String = DEFAULT_COMFY_URL,
    val chatBaseUrl: String = DEFAULT_OLLAMA_URL,
    val chatModel: String = DEFAULT_OLLAMA_MODEL,
    val workflowJson: String = "",
    val generationParametersJson: String = "",
)

const val DEFAULT_COMFY_URL = "http://10.0.2.2:8188"
const val DEFAULT_OLLAMA_URL = "http://10.0.2.2:11434"
const val DEFAULT_OLLAMA_MODEL = "qwen3:8b"
const val ALTERNATE_OLLAMA_MODEL = "gemma2:2b"
val SUPPORTED_CHAT_MODELS = listOf(DEFAULT_OLLAMA_MODEL, ALTERNATE_OLLAMA_MODEL)

enum class CreateMode { IMAGE, EDIT, IMAGE_TO_VIDEO }

data class MediaAsset(
    val path: String,
    val mimeType: String,
    val prompt: String,
    val createdAt: Long,
)
