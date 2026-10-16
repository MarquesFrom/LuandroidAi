package ai.luandroid.app.data

data class ProviderConfig(
    val engine: EngineKind = EngineKind.OPENAI_COMPATIBLE,
    val name: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val workflowJson: String = "",
)

enum class EngineKind { OPENAI_COMPATIBLE, COMFYUI }

enum class CreateMode { IMAGE, EDIT, IMAGE_TO_VIDEO }

data class MediaAsset(
    val path: String,
    val mimeType: String,
    val prompt: String,
    val createdAt: Long,
)
