package ai.luandroid.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class ChatFile(val path: String, val mimeType: String, val analysisImagePaths: List<String> = listOf(path))

data class ChatTurn(
    val role: String,
    val content: String,
    val attachments: List<ChatFile> = emptyList(),
)

data class ChatAction(
    val prompt: String,
    val mode: CreateMode,
    val referenceImages: List<String> = emptyList(),
)

data class ChatResult(val response: String, val pendingAction: ChatAction?)

class ChatAssistant {
    private val ollama = OllamaClient()

    fun reply(
        config: ProviderConfig,
        history: List<ChatTurn>,
        userMessage: String,
        attachments: List<ChatFile> = emptyList(),
    ): ChatResult {
        require(config.chatModel.isNotBlank()) { "Escolha um modelo Ollama na aba Motor." }
        require(attachments.isEmpty()) {
            "Qwen3 e Gemma 2 2B são modelos de texto. Para usar uma imagem de referência, abra a aba Criar."
        }
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "Você é o assistente do LuandroidAi e responde em português. Ajude a planejar " +
                            "criações visuais com instruções úteis e concisas. Use a ferramenta de criação " +
                            "quando o usuário pedir para gerar, editar ou animar uma imagem. " +
                            "Vídeos anexados são representados por quadros amostrados; não diga que " +
                            "editou o vídeo inteiro. Criações precisam de confirmação explícita no app.",
                    ),
            )
        history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            if (turn.role == "user" || turn.role == "assistant") messages.put(messageJson(turn))
        }
        val currentTurn = ChatTurn("user", userMessage, attachments)
        messages.put(messageJson(currentTurn))

        var pendingAction: ChatAction? = null
        repeat(MAX_TOOL_ROUNDS) {
            val response = complete(config, messages)
            val assistant = response.optJSONObject("message")
                ?: throw IOException("O modelo Ollama não retornou uma mensagem.")
            val toolCalls = assistant.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                val content = assistant.optString("content").trim()
                return ChatResult(
                    response = content.ifBlank { "O modelo não retornou texto. Tente novamente." },
                    pendingAction = pendingAction,
                )
            }

            messages.put(assistant)
            for (index in 0 until toolCalls.length()) {
                val call = toolCalls.optJSONObject(index) ?: continue
                val function = call.optJSONObject("function")
                    ?: throw IOException("O modelo retornou uma chamada de ferramenta inválida.")
                val name = function.optString("name")
                require(name == CREATE_TOOL) { "Ferramenta Ollama não reconhecida: $name" }
                val arguments = when (val value = function.opt("arguments")) {
                    is JSONObject -> value
                    is String -> JSONObject(value)
                    else -> JSONObject()
                }
                val result = if (pendingAction != null) {
                    JSONObject().put("error", "Uma criação já aguarda confirmação.").toString()
                } else {
                    val mode = parseMode(arguments.optString("mode"))
                    val prompt = arguments.optString("prompt").trim()
                    require(prompt.isNotBlank()) { "O assistente preparou uma criação sem descrição." }
                    val references = currentTurn.attachments
                        .flatMap(ChatFile::analysisImagePaths)
                        .filter { it.isNotBlank() && java.io.File(it).isFile }
                        .take(MAX_REFERENCE_IMAGES)
                    pendingAction = ChatAction(prompt, mode, references)
                    JSONObject()
                        .put("queued_for_confirmation", true)
                        .put("mode", mode.name)
                        .put("prompt", prompt)
                        .toString()
                }
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_name", name)
                        .put("content", result),
                )
            }
        }
        throw IOException("O chat excedeu o limite de etapas de ferramentas. Tente uma solicitação mais simples.")
    }

    private fun complete(config: ProviderConfig, messages: JSONArray): JSONObject {
        val tool = JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", CREATE_TOOL)
                    .put("description", "Prepara uma criação visual que o usuário confirmará no app.")
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put(
                                "properties",
                                JSONObject()
                                    .put(
                                        "mode",
                                        JSONObject()
                                            .put("type", "string")
                                            .put("enum", JSONArray(listOf("image", "edit", "image_to_video"))),
                                    )
                                    .put("prompt", JSONObject().put("type", "string")),
                            )
                            .put("required", JSONArray(listOf("mode", "prompt")))
                            .put("additionalProperties", false),
                    ),
            )
        val payload = JSONObject()
            .put("model", config.chatModel)
            .put("messages", messages)
            .put("stream", false)
        if (config.chatModel.startsWith("qwen3", ignoreCase = true)) {
            payload.put("tools", JSONArray().put(tool))
        }
        return ollama.chat(config.chatBaseUrl, payload)
    }

    private fun messageJson(turn: ChatTurn): JSONObject {
        val images = JSONArray()
        val text = StringBuilder(turn.content)
        turn.attachments.take(MAX_CHAT_ATTACHMENTS).forEach { attachment ->
            attachment.analysisImagePaths.take(MAX_VIDEO_FRAMES).forEach { imagePath ->
                val file = java.io.File(imagePath)
                require(file.isFile) { "Um arquivo anexado ao chat não está mais disponível." }
                images.put(Base64.encodeToString(optimizedImage(file), Base64.NO_WRAP))
            }
            if (attachment.mimeType.startsWith("video/")) {
                text.append("\nO arquivo anexado é um vídeo. As imagens enviadas são quadros amostrados do vídeo.")
            }
        }
        return JSONObject()
            .put("role", turn.role)
            .put("content", text.toString())
            .apply { if (images.length() > 0) put("images", images) }
    }

    private fun optimizedImage(file: java.io.File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Não foi possível ler a imagem anexada ao chat."
        }
        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_IMAGE_SIDE ||
            bounds.outHeight / sampleSize > MAX_IMAGE_SIDE
        ) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        ) ?: throw IOException("Não foi possível preparar a imagem anexada ao chat.")
        return bitmap.useBitmap {
            val output = java.io.ByteArrayOutputStream()
            check(it.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                "Não foi possível codificar a imagem anexada ao chat."
            }
            output.toByteArray()
        }
    }

    private inline fun <T> Bitmap.useBitmap(block: (Bitmap) -> T): T =
        try {
            block(this)
        } finally {
            recycle()
        }

    private fun parseMode(value: String): CreateMode = when (value) {
        "image" -> CreateMode.IMAGE
        "edit" -> CreateMode.EDIT
        "image_to_video" -> CreateMode.IMAGE_TO_VIDEO
        else -> throw IllegalArgumentException("O assistente pediu um modo de criação inválido.")
    }

    private companion object {
        const val CREATE_TOOL = "create_visual"
        const val MAX_HISTORY_TURNS = 20
        const val MAX_CHAT_ATTACHMENTS = 2
        const val MAX_VIDEO_FRAMES = 3
        const val MAX_IMAGE_SIDE = 1280
        const val MAX_REFERENCE_IMAGES = 2
        const val MAX_TOOL_ROUNDS = 4
    }
}
