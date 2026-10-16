package ai.luandroid.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageGenerationParametersTest {
    @Test
    fun parsesComfyParametersFromNestedMetadata() {
        val parsed = ImageGenerationParameters.parse(
            """{"parameters":{"width":1024,"height":1536,"steps":24,"cfg":6.5}}""",
        )

        assertEquals(1024, parsed.getInt("width"))
        assertEquals(1536, parsed.getInt("height"))
        assertEquals(24, parsed.getInt("steps"))
        assertEquals(6.5, parsed.getDouble("cfg"), 0.0)
    }

    @Test
    fun retainsComfySamplerValues() {
        val parameters = ImageGenerationParameters.parse(
            """{"seed":12345,"steps":28,"cfg":7.5,"width":768,"height":1024,"sampler_name":"euler","negative_prompt":"blur"}""",
        )
        assertEquals(12345L, parameters.getLong("seed"))
        assertEquals(28, parameters.getInt("steps"))
        assertEquals(7.5, parameters.getDouble("cfg"), 0.0)
        assertEquals(768, parameters.getInt("width"))
        assertEquals("euler", parameters.getString("sampler_name"))
        assertEquals("blur", parameters.getString("negative_prompt"))
    }

    @Test
    fun rejectsUnsupportedOrInvalidImageParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            ImageGenerationParameters.parse("""{"quality":"fantastic"}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImageGenerationParameters.parse("""{"size":"10x10"}""")
        }
    }

    @Test
    fun hasReadyToUseOllamaAndComfyDefaults() {
        val config = ProviderConfig()

        assertEquals(DEFAULT_COMFY_URL, config.baseUrl)
        assertEquals(DEFAULT_OLLAMA_URL, config.chatBaseUrl)
        assertEquals(DEFAULT_OLLAMA_MODEL, config.chatModel)
    }
}
