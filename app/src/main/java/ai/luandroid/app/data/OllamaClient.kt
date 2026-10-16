package ai.luandroid.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.util.concurrent.TimeUnit

class OllamaClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()
    private val pullClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .callTimeout(60, TimeUnit.MINUTES)
        .build()

    fun listModels(baseUrl: String): List<String> {
        val response = execute(request(baseUrl, "api/tags").get().build())
        val json = JSONObject(response)
        val models = json.optJSONArray("models") ?: JSONArray()
        return (0 until models.length())
            .mapNotNull { index ->
                val model = models.optJSONObject(index)
                model?.optString("name")?.takeIf(String::isNotBlank)
                    ?: model?.optString("model")?.takeIf(String::isNotBlank)
            }
            .distinct()
            .sorted()
    }

    fun chat(baseUrl: String, payload: JSONObject): JSONObject =
        JSONObject(execute(request(baseUrl, "api/chat").post(jsonBody(payload)).build()))

    fun pullModel(baseUrl: String, model: String, onProgress: (String) -> Unit) {
        require(model.isNotBlank()) { "Informe o nome do modelo Ollama para instalar." }
        require(isValidModelName(model)) {
            "O nome do modelo não é válido."
        }
        val payload = JSONObject().put("model", model).put("stream", true)
        val request = request(baseUrl, "api/pull")
            .post(jsonBody(payload))
            .build()
        pullClient.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Ollama retornou uma resposta vazia.")
            if (!response.isSuccessful) {
                val text = body.source().readByteArray(MAX_RESPONSE_BYTES).toString(Charsets.UTF_8)
                throw IOException(
                    "Ollama retornou HTTP ${response.code}${if (text.isBlank()) "." else ": ${text.take(MAX_ERROR_CHARS)}"}",
                )
            }
            var completed = false
            val source = body.source()
            while (!source.exhausted()) {
                val line = try {
                    source.readUtf8LineStrict(MAX_STREAM_LINE_BYTES)
                } catch (error: EOFException) {
                    throw IOException("Ollama encerrou o progresso de instalação de forma inesperada.", error)
                }
                if (line.isBlank()) continue
                val item = JSONObject(line)
                item.optString("error").takeIf(String::isNotBlank)?.let { throw IOException(it) }
                val status = item.optString("status")
                if (status == "success") completed = true
                onProgress(progressText(status, item))
            }
            if (!completed) throw IOException("Ollama encerrou a instalação sem confirmar sucesso.")
        }
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Ollama retornou uma resposta vazia.")
            val bytes = body.source().readByteArray(MAX_RESPONSE_BYTES)
            val text = bytes.toString(Charsets.UTF_8)
            if (!response.isSuccessful) {
                val detail = runCatching { JSONObject(text).optString("error") }
                    .getOrDefault("")
                    .ifBlank { text }
                throw IOException(
                    "Ollama retornou HTTP ${response.code}${if (detail.isBlank()) "." else ": ${detail.take(MAX_ERROR_CHARS)}"}",
                )
            }
            return text
        }
    }

    private fun request(baseUrl: String, path: String): Request.Builder {
        val base = EngineUrlPolicy.parse(baseUrl, "Ollama")
        val normalizedPath = base.encodedPath.trimEnd('/').removeSuffix("/api")
        val root = base.newBuilder()
            .encodedPath(if (normalizedPath.isBlank()) "/" else "$normalizedPath/")
            .build()
        return Request.Builder()
            .url(root.newBuilder().addPathSegments(path).build())
            .header("Accept", "application/json")
    }

    private fun jsonBody(value: JSONObject) =
        value.toString().toRequestBody(JSON_MEDIA_TYPE)

    private fun progressText(status: String, item: JSONObject): String {
        val total = item.optLong("total").coerceAtLeast(0)
        val completed = item.optLong("completed").coerceAtLeast(0)
        if (total > 0) {
            val percent = (completed * 100 / total).coerceIn(0, 100)
            return "$status — $percent%"
        }
        return status.ifBlank { "Instalando modelo…" }
    }

    companion object {
        fun isValidModelName(model: String): Boolean =
            model.matches(Regex("[A-Za-z0-9._:/-]{1,$MAX_MODEL_NAME_CHARS}"))
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_MODEL_NAME_CHARS = 200
        private const val MAX_STREAM_LINE_BYTES = 64L * 1024
        private const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024
        private const val MAX_ERROR_CHARS = 2_000
    }
}
