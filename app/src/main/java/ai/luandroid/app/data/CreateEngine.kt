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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    suspend fun discoverModels(config: ProviderConfig, apiKey: String): List<String> {
        val base = OpenAiEndpoint.parseBaseUrl(config.baseUrl)
        val request = request(OpenAiEndpoint.modelsUrl(base), apiKey).get().build()
        val json = JSONObject(execute(request).text())
        val data = json.optJSONArray("data") ?: JSONArray()
        return (0 until data.length())
            .mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()
    }

    suspend fun create(
        config: ProviderConfig,
        apiKey: String,
        mode: CreateMode,
        prompt: String,
        inputImage: File?,
        save: (ByteArray, String, String) -> MediaAsset,
    ): List<MediaAsset> = when (config.engine) {
        EngineKind.OPENAI_COMPATIBLE -> createOpenAi(config, apiKey, mode, prompt, inputImage, save)
        EngineKind.COMFYUI -> createComfy(config, apiKey, mode, prompt, inputImage, save)
    }

    private suspend fun createOpenAi(
        config: ProviderConfig,
        apiKey: String,
        mode: CreateMode,
        prompt: String,
        inputImage: File?,
        save: (ByteArray, String, String) -> MediaAsset,
    ): List<MediaAsset> {
        require(mode != CreateMode.IMAGE_TO_VIDEO) {
            "A criação de vídeo a partir de imagem precisa de um workflow ComfyUI. Selecione ComfyUI ou troque o modo."
        }
        val base = OpenAiEndpoint.parseBaseUrl(config.baseUrl)
        require(config.model.isNotBlank()) { "Informe o ID do modelo de imagem nas configurações do motor." }
        val url = if (mode == CreateMode.EDIT) {
            OpenAiEndpoint.imageEditsUrl(base)
        } else {
            OpenAiEndpoint.imageGenerationsUrl(base)
        }
        val body = if (mode == CreateMode.EDIT) {
            val image = requireNotNull(inputImage) { "Escolha uma imagem para editar." }
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", config.model)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart(
                    "image",
                    image.name,
                    image.asRequestBody((image.mimeType() ?: "image/jpeg").toMediaType()),
                )
                .build()
        } else {
            JSONObject()
                .put("model", config.model)
                .put("prompt", prompt)
                .put("n", 1)
                .put("size", "1024x1024")
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
        }
        val response = execute(request(url, apiKey).post(body).build())
        val item = JSONObject(response.text()).optJSONArray("data")
            ?.optJSONObject(0)
            ?: throw IOException("O motor de imagem não retornou nenhuma imagem.")
        val encoded = item.optString("b64_json")
        val image = if (encoded.isNotBlank()) {
            try {
                val bytes = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
                bytes to sniffImageMime(bytes)
            } catch (error: IllegalArgumentException) {
                throw IOException("O motor de imagem retornou dados base64 inválidos.", error)
            }
        } else {
            val imageUrl = item.optString("url")
            require(imageUrl.isNotBlank()) { "O motor não retornou os dados da imagem nem uma URL." }
            val parsedUrl = imageUrl.toHttpUrlOrNull()
                ?: throw IOException("O motor retornou uma URL de imagem inválida.")
            require(EngineUrlPolicy.isPermitted(parsedUrl)) {
                "O motor retornou uma URL pública sem criptografia. Configure HTTPS ou solicite a imagem em base64."
            }
            val downloaded = execute(request(parsedUrl, "").get().build())
            downloaded.bytes to (
                downloaded.contentType
                    ?.substringBefore(';')
                    ?.takeIf { it.startsWith("image/") }
                    ?: sniffImageMime(downloaded.bytes)
                )
        }
        return listOf(save(image.first, image.second, prompt))
    }

    private suspend fun createComfy(
        config: ProviderConfig,
        apiKey: String,
        mode: CreateMode,
        prompt: String,
        inputImage: File?,
        save: (ByteArray, String, String) -> MediaAsset,
    ): List<MediaAsset> {
        require(config.workflowJson.isNotBlank()) {
            "Importe um workflow ComfyUI API nas configurações do motor antes de gerar."
        }
        if (mode != CreateMode.IMAGE) requireNotNull(inputImage) {
            "Escolha uma imagem para editar ou transformar em vídeo."
        }
        val base = parseComfyBase(config.baseUrl)
        var upload: JSONObject? = null
        if (inputImage != null) {
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("overwrite", "true")
                .addFormDataPart(
                    "image",
                    inputImage.name,
                    inputImage.asRequestBody((inputImage.mimeType() ?: "image/jpeg").toMediaType()),
                )
                .build()
            upload = JSONObject(
                execute(request(endpoint(base, "upload/image"), apiKey).post(multipart).build()).text(),
            )
        }
        val workflow = JSONObject(config.workflowJson)
        replacePlaceholders(workflow, prompt, upload)
        val launch = JSONObject()
            .put("prompt", workflow)
            .put("client_id", UUID.randomUUID().toString())
        val promptId = JSONObject(
            execute(
                request(endpoint(base, "prompt"), apiKey)
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
                execute(request(endpoint(base, "history/$promptId"), apiKey).get().build()).text(),
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
            val bytes = execute(request(viewUrl, apiKey).get().build()).bytes
            save(bytes, result.mimeType, prompt)
        }
    }

    private fun replacePlaceholders(workflow: Any, prompt: String, upload: JSONObject?) {
        when (workflow) {
            is JSONObject -> {
                val keys = workflow.keys().asSequence().toList()
                keys.forEach { key ->
                    val value = workflow.get(key)
                    when (value) {
                        is JSONObject, is JSONArray -> replacePlaceholders(value, prompt, upload)
                        is String -> workflow.put(key, replaceString(value, prompt, upload))
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until workflow.length()) {
                    when (val value = workflow.get(index)) {
                        is JSONObject, is JSONArray -> replacePlaceholders(value, prompt, upload)
                        is String -> workflow.put(index, replaceString(value, prompt, upload))
                    }
                }
            }
        }
    }

    private fun replaceString(value: String, prompt: String, upload: JSONObject?): String {
        val imageName = upload?.optString("name").orEmpty()
        val imageSubfolder = upload?.optString("subfolder").orEmpty()
        val imageType = upload?.optString("type").orEmpty().ifBlank { "input" }
        return value
            .replace("{{PROMPT}}", prompt)
            .replace("{{IMAGE_FILENAME}}", imageName)
            .replace("{{IMAGE_SUBFOLDER}}", imageSubfolder)
            .replace("{{IMAGE_TYPE}}", imageType)
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

    private fun request(url: HttpUrl, apiKey: String): Request.Builder =
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

    private fun sniffImageMime(bytes: ByteArray): String = when {
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4e.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 3 &&
            bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() &&
            bytes[2] == 0xff.toByte() -> "image/jpeg"
        bytes.size >= 12 &&
            bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
            bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP" -> "image/webp"
        bytes.size >= 6 &&
            (bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) == "GIF87a" ||
                bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII) == "GIF89a") -> "image/gif"
        else -> "image/png"
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_ERROR_CHARS = 8_000
        const val MAX_RESPONSE_BYTES = 100 * 1024 * 1024
    }
}
