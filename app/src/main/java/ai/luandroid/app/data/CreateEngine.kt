package ai.luandroid.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CreateEngine {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    suspend fun create(
        config: ProviderConfig,
        mode: CreateMode,
        prompt: String,
        inputImages: List<File>,
        save: (ByteArray, String, String) -> MediaAsset,
    ): List<MediaAsset> = createComfy(config, mode, prompt, inputImages, save)

    private suspend fun createComfy(
        config: ProviderConfig,
        mode: CreateMode,
        prompt: String,
        inputImages: List<File>,
        save: (ByteArray, String, String) -> MediaAsset,
    ): List<MediaAsset> {
        require(config.workflowJson.isNotBlank()) {
            "Importe um workflow ComfyUI API nas configurações do motor antes de gerar."
        }
        if (mode != CreateMode.IMAGE) require(inputImages.isNotEmpty()) {
            "Escolha uma imagem para editar ou transformar em vídeo."
        }
        val base = parseComfyBase(config.baseUrl)
        require(inputImages.size <= MAX_REFERENCE_IMAGES) {
            "O app aceita até $MAX_REFERENCE_IMAGES imagens de referência."
        }
        val uploads = inputImages.map { inputImage ->
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("overwrite", "true")
                .addFormDataPart(
                    "image",
                    inputImage.name,
                    inputImage.asRequestBody((inputImage.mimeType() ?: "image/jpeg").toMediaType()),
                )
                .build()
            JSONObject(
                execute(request(endpoint(base, "upload/image")).post(multipart).build()).text(),
            )
        }
        val workflow = JSONObject(config.workflowJson)
        val parameters = if (config.generationParametersJson.isBlank()) {
            JSONObject()
        } else {
            ImageGenerationParameters.parse(config.generationParametersJson)
        }
        replacePlaceholders(workflow, prompt, uploads, parameters)
        val launch = JSONObject()
            .put("prompt", workflow)
            .put("client_id", UUID.randomUUID().toString())
        val promptId = JSONObject(
            execute(
                request(endpoint(base, "prompt"))
                    .post(launch.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build(),
            ).text(),
        ).optString("prompt_id")
        require(promptId.isNotBlank()) { "O ComfyUI não retornou um ID para o pedido. Verifique o workflow e os logs do servidor." }

        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(20)
        var output: List<ComfyFile> = emptyList()
        while (System.nanoTime() < deadline) {
            delay(1_500)
            val historyJson = JSONObject(
                execute(request(endpoint(base, "history/$promptId")).get().build()).text(),
            )
            val history = historyJson.optJSONObject(promptId)
            if (history != null) {
                val status = history.optJSONObject("status")
                val messages = status?.optJSONArray("messages")
                if (messages != null && messages.toString().contains("execution_error")) {
                    throw IOException("O ComfyUI informou um erro ao executar o workflow: $messages")
                }
                if (status?.optBoolean("completed", false) == true) {
                    output = collectFiles(history.optJSONObject("outputs"))
                    break
                }
            }
        }
        if (output.isEmpty()) {
            throw IOException("O ComfyUI não retornou mídia em 20 minutos. Verifique os nós de saída do workflow.")
        }
        val videoRequested = mode == CreateMode.IMAGE_TO_VIDEO
        val candidates = output.filter { it.mimeType.startsWith(if (videoRequested) "video/" else "image/") }
        require(candidates.isNotEmpty()) {
            if (videoRequested) "O workflow terminou sem gerar um vídeo." else "O workflow terminou sem gerar uma imagem."
        }
        return candidates.take(4).map { result ->
            val viewUrl = endpoint(base, "view")
                .newBuilder()
                .addQueryParameter("filename", result.filename)
                .addQueryParameter("subfolder", result.subfolder)
                .addQueryParameter("type", result.type)
                .build()
            val bytes = execute(request(viewUrl).get().build()).bytes
            save(bytes, result.mimeType, prompt)
        }
    }

    private fun replacePlaceholders(
        workflow: Any,
        prompt: String,
        uploads: List<JSONObject>,
        parameters: JSONObject,
    ) {
        when (workflow) {
            is JSONObject -> {
                val keys = workflow.keys().asSequence().toList()
                keys.forEach { key ->
                    val value = workflow.get(key)
                    when (value) {
                        is JSONObject, is JSONArray -> replacePlaceholders(value, prompt, uploads, parameters)
                        is String -> workflow.put(key, replaceParameter(value, prompt, uploads, parameters))
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until workflow.length()) {
                    when (val value = workflow.get(index)) {
                        is JSONObject, is JSONArray -> replacePlaceholders(value, prompt, uploads, parameters)
                        is String -> workflow.put(index, replaceParameter(value, prompt, uploads, parameters))
                    }
                }
            }
        }
    }

    private fun replaceParameter(
        value: String,
        prompt: String,
        uploads: List<JSONObject>,
        parameters: JSONObject,
    ): Any {
        val parameter = when (value) {
            "{{SEED}}" -> "seed"
            "{{STEPS}}" -> "steps"
            "{{CFG}}" -> "cfg"
            "{{WIDTH}}" -> "width"
            "{{HEIGHT}}" -> "height"
            "{{DENOISE}}" -> "denoise"
            "{{SAMPLER}}" -> "sampler_name"
            "{{SCHEDULER}}" -> "scheduler"
            "{{NEGATIVE_PROMPT}}" -> "negative_prompt"
            else -> null
        }
        if (parameter != null && parameters.has(parameter)) return parameters.get(parameter)
        return replaceString(value, prompt, uploads)
    }

    private fun replaceString(value: String, prompt: String, uploads: List<JSONObject>): String {
        fun component(index: Int, key: String): String =
            uploads.getOrNull(index)?.optString(key).orEmpty()
        val imageType = component(0, "type").ifBlank { "input" }
        return value
            .replace("{{PROMPT}}", prompt)
            .replace("{{IMAGE_FILENAME}}", component(0, "name"))
            .replace("{{IMAGE_SUBFOLDER}}", component(0, "subfolder"))
            .replace("{{IMAGE_TYPE}}", imageType)
            .replace("{{IMAGE2_FILENAME}}", component(1, "name"))
            .replace("{{IMAGE2_SUBFOLDER}}", component(1, "subfolder"))
            .replace("{{IMAGE2_TYPE}}", component(1, "type").ifBlank { "input" })
    }

    private fun collectFiles(outputs: JSONObject?): List<ComfyFile> {
        if (outputs == null) return emptyList()
        val results = mutableListOf<ComfyFile>()
        val keys = outputs.keys()
        while (keys.hasNext()) {
            val node = outputs.optJSONObject(keys.next()) ?: continue
            listOf("images", "videos", "gifs").forEach { field ->
                val files = node.optJSONArray(field) ?: return@forEach
                for (index in 0 until files.length()) {
                    val item = files.optJSONObject(index) ?: continue
                    val filename = item.optString("filename")
                    if (filename.isBlank()) continue
                    val mime = when {
                        filename.endsWith(".mp4", true) || filename.endsWith(".m4v", true) ->
                            "video/mp4"
                        filename.endsWith(".webm", true) -> "video/webm"
                        filename.endsWith(".mov", true) -> "video/quicktime"
                        filename.endsWith(".gif", true) -> "image/gif"
                        filename.endsWith(".webp", true) -> "image/webp"
                        filename.endsWith(".jpg", true) || filename.endsWith(".jpeg", true) ->
                            "image/jpeg"
                        field == "videos" -> "video/mp4"
                        else -> "image/png"
                    }
                    results += ComfyFile(
                        filename = filename,
                        subfolder = item.optString("subfolder"),
                        type = item.optString("type").ifBlank { "output" },
                        mimeType = mime,
                    )
                }
            }
        }
        return results.distinctBy { it.filename to it.subfolder }
    }

    private fun request(url: HttpUrl, apiKey: String = ""): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "application/json, image/*, video/*")
            .apply {
                if (apiKey.isNotBlank()) header("Authorization", "Bearer $apiKey")
            }

    private suspend fun execute(request: Request): HttpResult {
        val response = client.newCall(request).await()
        if (response.code !in 200..299) {
            val text = response.bytes.toString(Charsets.UTF_8).take(MAX_ERROR_CHARS)
            throw IOException("O motor retornou HTTP ${response.code}${if (text.isBlank()) "." else ": $text"}")
        }
        return response
    }

    private fun endpoint(base: HttpUrl, path: String): HttpUrl =
        base.newBuilder().addPathSegments(path).build()

    private fun parseComfyBase(value: String): HttpUrl {
        return EngineUrlPolicy.parse(value, "ComfyUI")
    }

    private fun Response.bytes(): ByteArray =
        body?.use { body ->
            if (body.contentLength() > MAX_RESPONSE_BYTES) {
                throw IOException("A resposta do motor ultrapassa o limite de 100 MB do app.")
            }
            val source = body.source()
            val buffer = okio.Buffer()
            val output = ByteArrayOutputStream()
            var total = 0L
            while (true) {
                val count = source.read(buffer, 8 * 1024L)
                if (count < 0) break
                total += count
                if (total > MAX_RESPONSE_BYTES) {
                    throw IOException("A resposta do motor ultrapassa o limite de 100 MB do app.")
                }
                output.write(buffer.readByteArray())
            }
            output.toByteArray()
        } ?: throw IOException("O motor retornou uma resposta vazia.")

    private fun HttpResult.text(): String = bytes.toString(Charsets.UTF_8)

    private data class ComfyFile(
        val filename: String,
        val subfolder: String,
        val type: String,
        val mimeType: String,
    )

    private data class HttpResult(val code: Int, val contentType: String?, val bytes: ByteArray)

    private suspend fun Call.await(): HttpResult = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use {
                        val bytes = it.bytes()
                        val result = HttpResult(it.code, it.header("Content-Type"), bytes)
                        if (continuation.isActive) continuation.resume(result)
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }

    private fun File.mimeType(): String? = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/png"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_ERROR_CHARS = 8_000
        const val MAX_RESPONSE_BYTES = 100 * 1024 * 1024
        const val MAX_REFERENCE_IMAGES = 2
    }
}
