package ai.luandroid.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateEngineTest {
    @Test
    fun acceptsVideosAndAnimatedGifsForImageToVideoRequests() {
        assertTrue(isExpectedComfyOutput("video/mp4", CreateMode.IMAGE_TO_VIDEO))
        assertTrue(isExpectedComfyOutput("image/gif", CreateMode.IMAGE_TO_VIDEO))
        assertFalse(isExpectedComfyOutput("image/png", CreateMode.IMAGE_TO_VIDEO))
    }

    @Test
    fun acceptsImagesButNotVideosForImageRequests() {
        assertTrue(isExpectedComfyOutput("image/png", CreateMode.IMAGE))
        assertFalse(isExpectedComfyOutput("video/mp4", CreateMode.IMAGE))
    }
}
