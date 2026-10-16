package ai.luandroid.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePresetTest {
    @Test
    fun comfyPresetUsesEditablePrivateLanExampleAndNoEmbeddedCredentials() {
        val preset = EnginePreset.COMFYUI_LAN

        assertEquals(EngineKind.COMFYUI, preset.engine)
        assertEquals("ComfyUI (servidor próprio)", preset.providerName)
        assertEquals("http://192.168.1.10:8188", preset.baseUrl)
        assertTrue(preset.model.isBlank())
        assertTrue(preset.chatModel.isBlank())
    }

    @Test
    fun openAiPresetPreselectsDocumentedImageAndChatModelsWithoutApiKeys() {
        val preset = EnginePreset.OPENAI_IMAGES

        assertEquals("OpenAI", preset.providerName)
        assertEquals("https://api.openai.com/v1", preset.baseUrl)
        assertEquals("gpt-image-1", preset.model)
        assertEquals(preset.baseUrl, preset.chatBaseUrl)
        assertEquals("gpt-4.1-mini", preset.chatModel)
    }
}
