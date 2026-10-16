package ai.luandroid.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenAiEndpointTest {
    @Test
    fun addsV1AndBuildsChatPath() {
        val base = OpenAiEndpoint.parseBaseUrl("http://192.168.1.50:11434/")
        assertEquals(
            "http://192.168.1.50:11434/v1/chat/completions",
            OpenAiEndpoint.chatCompletionsUrl(base).toString(),
        )
    }

    @Test
    fun preservesCustomBasePathAndExistingV1() {
        val base = OpenAiEndpoint.parseBaseUrl("https://example.test/proxy/v1")
        assertEquals(
            "https://example.test/proxy/v1/models",
            OpenAiEndpoint.modelsUrl(base).toString(),
        )
    }

    @Test
    fun buildsImageGenerationAndEditRoutes() {
        val base = OpenAiEndpoint.parseBaseUrl("https://example.test/v1/")
        assertEquals(
            "https://example.test/v1/images/generations",
            OpenAiEndpoint.imageGenerationsUrl(base).toString(),
        )
        assertEquals(
            "https://example.test/v1/images/edits",
            OpenAiEndpoint.imageEditsUrl(base).toString(),
        )
    }

    @Test
    fun rejectsCredentialsEmbeddedInUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiEndpoint.parseBaseUrl("https://user:secret@example.test")
        }
    }

    @Test
    fun rejectsNonHttpSchemes() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiEndpoint.parseBaseUrl("file:///tmp/models")
        }
    }

    @Test
    fun allowsCleartextOnlyForPrivateLanEndpoints() {
        assertEquals(
            "http://192.168.1.50:11434/v1",
            OpenAiEndpoint.parseBaseUrl("http://192.168.1.50:11434").toString(),
        )
        assertEquals(
            "http://engine.local:8188/",
            EngineUrlPolicy.parse("http://engine.local:8188", "ComfyUI").toString(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            OpenAiEndpoint.parseBaseUrl("http://public.example.test/v1")
        }
    }
}
