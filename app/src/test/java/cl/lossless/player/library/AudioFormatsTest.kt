package cl.lossless.player.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFormatsTest {

    @Test
    fun wavIsRecognizedByMimeAndExtension() {
        listOf("audio/wav", "audio/x-wav", "audio/vnd.wave").forEach {
            assertTrue(it, it in AudioFormats.supportedMimes)
            assertEquals("WAV", AudioFormats.containerLabel(it))
        }
        val wavMime = AudioFormats.extensions.getValue("wav")
        assertTrue(wavMime in AudioFormats.supportedMimes)
        assertEquals("WAV", AudioFormats.containerLabel(wavMime))
    }

    @Test
    fun existingLabelsAreUnchanged() {
        assertEquals("MP3", AudioFormats.containerLabel("audio/mpeg"))
        assertEquals("FLAC", AudioFormats.containerLabel("audio/flac"))
        assertEquals("FLAC", AudioFormats.containerLabel("audio/x-flac"))
        assertEquals("M4A", AudioFormats.containerLabel("audio/mp4"))
    }
}
