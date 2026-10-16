package ai.luandroid.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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

    fun importChatMedia(uri: Uri): ChatMediaFile {
        val mime = context.contentResolver.getType(uri)?.lowercase()
            ?: throw IllegalArgumentException("Não foi possível identificar o tipo do arquivo.")
        require(mime in CHAT_IMAGE_TYPES || mime in CHAT_VIDEO_TYPES) {
            "Anexe uma imagem PNG, JPEG, WebP ou um vídeo MP4, WebM ou MOV."
        }
        directory.mkdirs()
        val output = File(directory, "chat-${UUID.randomUUID()}.${extensionFor(mime)}")
        try {
            val source = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Não foi possível abrir o arquivo selecionado."
            }
            source.use { input ->
                output.outputStream().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    val limit = if (mime.startsWith("video/")) MAX_VIDEO_BYTES else MAX_INPUT_BYTES
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= limit) {
                            if (mime.startsWith("video/")) "Vídeos maiores que 100 MB não são compatíveis."
                            else "Imagens maiores que 25 MB não são compatíveis."
                        }
                        sink.write(buffer, 0, count)
                    }
                }
            }
            val analysisImages = if (mime.startsWith("video/")) extractVideoFrames(output) else listOf(output)
            return ChatMediaFile(output, mime, analysisImages)
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    fun analysisImages(file: File, mimeType: String): List<File> {
        require(file.isFile) { "O arquivo da galeria não está mais disponível." }
        return if (mimeType.startsWith("video/")) extractVideoFrames(file) else listOf(file)
    }

    private fun extractVideoFrames(video: File): List<File> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<File>()
        try {
            retriever.setDataSource(video.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: throw IllegalArgumentException("Não foi possível ler a duração do vídeo.")
            val timestamps = listOf(0L, duration / 2, (duration - 1).coerceAtLeast(0)).distinct()
            timestamps.forEach { timestamp ->
                val frame = retriever.getFrameAtTime(
                    timestamp * 1_000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                ) ?: throw IllegalArgumentException("Não foi possível extrair quadros do vídeo.")
                val output = File(directory, "chat-frame-${UUID.randomUUID()}.jpg")
                try {
                    output.outputStream().use { sink ->
                        check(frame.compress(Bitmap.CompressFormat.JPEG, 88, sink)) {
                            "Não foi possível preparar o quadro do vídeo para análise."
                        }
                    }
                    frames += output
                } catch (error: Exception) {
                    output.delete()
                    throw error
                } finally {
                    frame.recycle()
                }
            }
            return frames
        } catch (error: Exception) {
            frames.forEach(File::delete)
            throw error
        } finally {
            retriever.release()
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
        val CHAT_IMAGE_TYPES = setOf("image/png", "image/jpeg", "image/jpg", "image/webp")
        val CHAT_VIDEO_TYPES = setOf("video/mp4", "video/webm", "video/quicktime")
        const val ASSETS = "assets"
        const val MAX_INPUT_BYTES = 25L * 1024 * 1024
        const val MAX_VIDEO_BYTES = 100L * 1024 * 1024
        const val MAX_OUTPUT_BYTES = 100 * 1024 * 1024
        const val MAX_ASSETS = 100
    }
}

data class ChatMediaFile(val file: File, val mimeType: String, val analysisImages: List<File>)
