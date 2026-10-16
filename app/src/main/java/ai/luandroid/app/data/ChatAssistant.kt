package ai.luandroid.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatTurn(val role: String, val content: String)

data class ChatAction(val prompt: String, val mode: CreateMode)

data class ChatResult(val response: String, val pendingAction: ChatAction?)

class ChatAssistant {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    fun reply(
        config: ProviderConfig,
        apiKey: String,
        history: List<ChatTurn>,
        userMessage: String,
    ): ChatResult {
        require(config.chatBaseUrl.isNotBlank()) {
            "Configure um endpoint OpenAI-compatible para o chat na aba Motor."
        }
        require(config.chatModel.isNotBlank()) {
            "Escolha um modelo de chat nas configurações do motor."
        }
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put(
                        "content",
                        "Você é o assistente do LuandroidAi, em português. Ajude a planejar, " +
                            "pesquisar e criar/editar imagens ou vídeos com instruções concisas. " +
                            "Use a ferramenta de busca para fatos atuais; nunca diga que pesquisou se a " +
                            "ferramenta falhar ou não estiver configurada. Use a ferramenta de criação " +
                            "quando o usuário pedir uma criação visual. Criações precisam de confirmação " +
                            "explícita no app antes do envio ao motor.",
                    ),
            )
        history.takeLast(MAX_HISTORY_TURNS).forEach { turn ->
            if (turn.role == "user" || turn.role == "assistant") {
                messages.put(JSONObject().put("role", turn.role).put("content", turn.content))
            }
        }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        var pendingAction: ChatAction? = null
        repeat(MAX_TOOL_ROUNDS) {
            val response = complete(config, apiKey, messages)
            val choice = response.optJSONArray("choices")?.optJSONObject(0)
                ?: throw IOException("O modelo de chat não retornou uma resposta utilizável.")
            val assistant = choice.optJSONObject("message")
                ?: throw IOException("A resposta do modelo não contém uma mensagem.")
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
                val id = call.optString("id")
                val function = call.optJSONObject("function")
                    ?: throw IOException("O modelo retornou uma chamada de ferramenta inválida.")
                val name = function.optString("name")
                val arguments = JSONObject(function.optString("arguments", "{}"))
                val toolResult = when (name) {
                    SEARCH_TOOL -> search(config.searchUrl, arguments.optString("query"))
                    CREATE_TOOL -> {
                        if (pendingAction != null) {
                            JSONObject().put("error", "Uma criação já aguarda confirmação. Não prepare outra.").toString()
                        } else {
                            val mode = parseMode(arguments.optString("mode"))
                            val prompt = arguments.optString("prompt").trim()
                            require(prompt.isNotBlank()) { "O assistente preparou uma criação sem descrição." }
                            pendingAction = ChatAction(prompt, mode)
                            JSONObject()
                                .put("queued_for_confirmation", true)
                                .put("mode", mode.name)
                                .put("prompt", prompt)
                                .toString()
                        }
                    }
                    else -> JSONObject().put("error", "Ferramenta não reconhecida: $name").toString()
                }
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", id)
                        .put("content", toolResult),
                )
            }
        }
        throw IOException("O chat excedeu o limite de etapas de ferramentas. Tente uma solicitação mais simples.")
    }

    private fun complete(config: ProviderConfig, apiKey: String, messages: JSONArray): JSONObject {
        val base = OpenAiEndpoint.parseBaseUrl(config.chatBaseUrl)
        val tools = JSONArray()
            .put(toolSpec(SEARCH_TOOL, "Pesquisa na web usando o serviço SearXNG configurado.", "query"))
            .put(
                JSONObject()
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
                    ),
            )
        val payload = JSONObject()
            .put("model", config.chatModel)
            .put("messages", messages)
            .put("tools", tools)
            .put("tool_choice", "auto")
        val builder = Request.Builder()
            .url(OpenAiEndpoint.chatCompletionsUrl(base))
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        val response = client.newCall(builder.build()).execute()
        response.use {
            val body = it.readBoundedBody()
            if (!it.isSuccessful) {
                throw IOException("O chat retornou HTTP ${it.code}${if (body.isBlank()) "." else ": ${body.take(MAX_ERROR_CHARS)}"}")
            }
            return JSONObject(body)
        }
    }

    private fun search(searchUrl: String, query: String): String {
        require(query.isNotBlank()) { "A pesquisa não contém uma consulta." }
        if (searchUrl.isBlank()) {
            return JSONObject()
                .put("error", "A busca não está configurada. Defina a URL de uma instância SearXNG na aba Motor.")
                .toString()
        }
        val base = EngineUrlPolicy.parse(searchUrl, "SearXNG")
        val url = base.newBuilder()
            .addPathSegments("search")
            .addQueryParameter("q", query.take(MAX_SEARCH_QUERY_CHARS))
            .addQueryParameter("format", "json")
            .build()
        val response = client.newCall(
            Request.Builder().url(url).header("Accept", "application/json").get().build(),
        ).execute()
        response.use {
            val body = it.readBoundedBody()
            if (!it.isSuccessful) throw IOException("A busca SearXNG retornou HTTP ${it.code}.")
            val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
            val concise = JSONArray()
            for (index in 0 until minOf(results.length(), MAX_SEARCH_RESULTS)) {
                val item = results.optJSONObject(index) ?: continue
                concise.put(
                    JSONObject()
                        .put("title", item.optString("title").take(300))
                        .put("url", item.optString("url").take(1_000))
                        .put("snippet", item.optString("content").take(800)),
                )
            }
            return JSONObject().put("results", concise).toString()
        }
    }

    private fun toolSpec(name: String, description: String, required: String): JSONObject =
        JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject()
                    .put("name", name)
                    .put("description", description)
                    .put(
                        "parameters",
                        JSONObject()
                            .put("type", "object")
                            .put(
                                "properties",
                                JSONObject().put(required, JSONObject().put("type", "string")),
                            )
                            .put("required", JSONArray().put(required))
                            .put("additionalProperties", false),
                    ),
            )

    private fun parseMode(value: String): CreateMode = when (value) {
        "image" -> CreateMode.IMAGE
        "edit" -> CreateMode.EDIT
        "image_to_video" -> CreateMode.IMAGE_TO_VIDEO
        else -> throw IllegalArgumentException("O assistente pediu um modo de criação inválido.")
    }

    private fun okhttp3.Response.readBoundedBody(): String {
        val responseBody = body ?: throw IOException("O serviço retornou uma resposta vazia.")
        responseBody.use {
            if (it.contentLength() > MAX_RESPONSE_CHARS) {
                throw IOException("A resposta do serviço excedeu o limite de 4 MB do app.")
            }
            val source = it.source()
            val buffer = okio.Buffer()
            val output = okio.Buffer()
            var total = 0L
            while (true) {
                val count = source.read(buffer, 8 * 1024L)
                if (count < 0) break
                total += count
                if (total > MAX_RESPONSE_CHARS) {
                    throw IOException("A resposta do serviço excedeu o limite de 4 MB do app.")
                }
                output.write(buffer, count)
            }
            return output.readUtf8()
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SEARCH_TOOL = "web_search"
        const val CREATE_TOOL = "create_visual"
        const val MAX_HISTORY_TURNS = 20
        const val MAX_TOOL_ROUNDS = 4
        const val MAX_RESPONSE_CHARS = 4 * 1024 * 1024
        const val MAX_ERROR_CHARS = 2_000
        const val MAX_SEARCH_QUERY_CHARS = 500
        const val MAX_SEARCH_RESULTS = 5
    }
}
