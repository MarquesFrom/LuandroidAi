package ai.luandroid.app.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

@Suppress("UseKtx")
class MediaLibrary(private val context: Context) {
    private val directory = File(context.filesDir, "media")
    private val preferences = context.getSharedPreferences("media_library", Context.MODE_PRIVATE)

    fun importImage(uri: Uri): File {
        val mime = context.contentResolver.getType(uri)
            ?: throw IllegalArgumentException("O arquivo selecionado não é uma imagem compatível.")
        require(mime.lowercase() in setOf("image/png", "image/jpeg", "image/jpg", "image/webp")) {
            "Escolha uma imagem PNG, JPEG ou WebP."
        }
        directory.mkdirs()
        val output = File(directory, "input-${UUID.randomUUID()}.${extensionFor(mime)}")
        try {
            val source = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Não foi possível abrir a imagem selecionada."
            }
            source.use { input ->
                output.outputStream().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_INPUT_BYTES) {
                            "Imagens maiores que 25 MB não são compatíveis."
                        }
                        sink.write(buffer, 0, count)
                    }
                }
            }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    fun save(bytes: ByteArray, mimeType: String, prompt: String): MediaAsset {
        require(bytes.isNotEmpty()) { "O motor retornou um arquivo de mídia vazio." }
        require(bytes.size <= MAX_OUTPUT_BYTES) { "O arquivo gerado ultrapassa o limite de 100 MB do app." }
        directory.mkdirs()
        val file = File(directory, "create-${UUID.randomUUID()}.${extensionFor(mimeType)}")
        file.writeBytes(bytes)
        val asset = MediaAsset(file.absolutePath, mimeType, prompt, System.currentTimeMillis())
        val updated = (load() + asset).takeLast(MAX_ASSETS)
        val json = JSONArray()
        updated.forEach {
            json.put(
                JSONObject()
                    .put("path", it.path)
                    .put("mimeType", it.mimeType)
                    .put("prompt", it.prompt)
                    .put("createdAt", it.createdAt),
            )
        }
        check(preferences.edit().putString(ASSETS, json.toString()).commit()) {
            "O arquivo foi salvo, mas não foi possível atualizar o índice da galeria."
        }
        return asset
    }

    fun load(): List<MediaAsset> {
        val json = preferences.getString(ASSETS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val item = array.getJSONObject(index)
                val path = item.getString("path")
                if (!File(path).isFile) return@mapNotNull null
                MediaAsset(
                    path = path,
                    mimeType = item.getString("mimeType"),
                    prompt = item.optString("prompt"),
                    createdAt = item.optLong("createdAt"),
                )
            }
        } catch (error: Exception) {
            throw IllegalStateException("Não foi possível ler o índice da galeria local.", error)
        }
    }

    private fun extensionFor(mimeType: String): String = when (mimeType.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/quicktime" -> "mov"
        "image/gif" -> "gif"
        else -> if (mimeType.startsWith("video/")) "mp4" else "png"
    }

    private companion object {
        const val ASSETS = "assets"
        const val MAX_INPUT_BYTES = 25L * 1024 * 1024
        const val MAX_OUTPUT_BYTES = 100 * 1024 * 1024
        const val MAX_ASSETS = 100
    }
}
