package ai.luandroid.app.data

data class ProviderConfig(
    val engine: EngineKind = EngineKind.OPENAI_COMPATIBLE,
    val name: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val chatBaseUrl: String = "",
    val chatModel: String = "",
    val searchUrl: String = "",
    val workflowJson: String = "",
)

enum class EngineKind { OPENAI_COMPATIBLE, COMFYUI }

enum class CreateMode { IMAGE, EDIT, IMAGE_TO_VIDEO }

enum class EnginePreset(
    val label: String,
    val engine: EngineKind,
    val providerName: String,
    val baseUrl: String,
    val model: String,
    val chatBaseUrl: String,
    val chatModel: String,
) {
    COMFYUI_LAN(
        label = "ComfyUI — imagem e vídeo",
        engine = EngineKind.COMFYUI,
        providerName = "ComfyUI (servidor próprio)",
        baseUrl = "http://192.168.1.10:8188",
        model = "",
        chatBaseUrl = "",
        chatModel = "",
    ),
    OPENAI_IMAGES(
        label = "OpenAI — imagem e chat",
        engine = EngineKind.OPENAI_COMPATIBLE,
        providerName = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        model = "gpt-image-1",
        chatBaseUrl = "https://api.openai.com/v1",
        chatModel = "gpt-4.1-mini",
    ),
    OPENAI_COMPATIBLE(
        label = "Servidor OpenAI-compatible",
        engine = EngineKind.OPENAI_COMPATIBLE,
        providerName = "Servidor OpenAI-compatible",
        baseUrl = "",
        model = "",
        chatBaseUrl = "",
        chatModel = "",
    ),
}

data class MediaAsset(
    val path: String,
    val mimeType: String,
    val prompt: String,
    val createdAt: Long,
)
