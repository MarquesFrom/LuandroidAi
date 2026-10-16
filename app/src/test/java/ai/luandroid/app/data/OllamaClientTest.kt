package ai.luandroid.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OllamaClientTest {
    @Test
    fun acceptsStandardOllamaModelIdentifiers() {
        assertTrue(OllamaClient.isValidModelName("gemma2:9b"))
        assertTrue(OllamaClient.isValidModelName("qwen3:8b"))
        assertTrue(OllamaClient.isValidModelName("qwen3-vl:8b"))
        assertTrue(OllamaClient.isValidModelName("hf.co/org/model:latest"))
    }

    @Test
    fun rejectsShellSyntaxAndWhitespaceInModelIdentifiers() {
        assertFalse(OllamaClient.isValidModelName("qwen3:8b; rm -rf /"))
        assertFalse(OllamaClient.isValidModelName("model\ncommand"))
        assertFalse(OllamaClient.isValidModelName(""))
    }

    @Test
    fun defaultOllamaEndpointIsAllowedOnLocalNetwork() {
        assertEquals("10.0.2.2", EngineUrlPolicy.parse(DEFAULT_OLLAMA_URL, "Ollama").host)
    }

    @Test
    fun offersQwenAsDefaultAndGemma2TwoBAsTheOnlyAlternative() {
        assertEquals("qwen3:8b", DEFAULT_OLLAMA_MODEL)
        assertEquals(listOf("qwen3:8b", "gemma2:2b"), SUPPORTED_CHAT_MODELS)
        assertEquals("10.0.2.2", EngineUrlPolicy.parse(DEFAULT_COMFY_URL, "ComfyUI").host)
    }
}
