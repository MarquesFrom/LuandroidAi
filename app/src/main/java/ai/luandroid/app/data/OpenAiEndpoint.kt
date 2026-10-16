package ai.luandroid.app.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object OpenAiEndpoint {
    fun parseBaseUrl(value: String): HttpUrl {
        val parsed = EngineUrlPolicy.parse(value, "motor")

        val path = parsed.encodedPath.trimEnd('/')
        val normalizedPath = when {
            path.isEmpty() -> "/v1"
            path.endsWith("/v1") -> path
            else -> "$path/v1"
        }
        return parsed.newBuilder()
            .encodedPath(normalizedPath)
            .build()
    }

    fun modelsUrl(base: HttpUrl): HttpUrl =
        base.newBuilder().addPathSegments("models").build()

    fun chatCompletionsUrl(base: HttpUrl): HttpUrl =
        base.newBuilder().addPathSegments("chat/completions").build()

    fun imageGenerationsUrl(base: HttpUrl): HttpUrl =
        base.newBuilder().addPathSegments("images/generations").build()

    fun imageEditsUrl(base: HttpUrl): HttpUrl =
        base.newBuilder().addPathSegments("images/edits").build()
}
