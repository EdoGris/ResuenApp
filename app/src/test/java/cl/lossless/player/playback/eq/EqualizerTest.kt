package cl.lossless.player.playback.eq

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.pow

@OptIn(UnstableApi::class)
class EqualizerTest {

    private val fs = 48_000.0

    @Test
    fun peakingHasRequestedGainAtCenterAndNoneFarAway() {
        val c = BiquadCoefficients.peaking(1_000.0, 6.0, EqSpec.BAND_Q.toDouble(), fs)
        assertEquals(6.0, c.magnitudeDb(1_000.0, fs), 0.01)
        assertEquals(0.0, c.magnitudeDb(20.0, fs), 0.1)
    }

    @Test
    fun lowShelfBoostsLowFrequencies() {
        val c = BiquadCoefficients.lowShelf(110.0, 10.0, fs)
        assertEquals(10.0, c.magnitudeDb(10.0, fs), 0.2)
        assertEquals(0.0, c.magnitudeDb(10_000.0, fs), 0.1)
    }

    @Test
    fun flatSettingsHaveFlatResponse() {
        val r = EqMath.responseDb(EqualizerSettings(enabled = true), doubleArrayOf(31.0, 1_000.0, 16_000.0))
        r.forEach { assertEquals(0.0, it, 1e-9) }
    }

    @Test
    fun presetsHaveTenBands() {
        EqPresets.ALL.filterNot { it.isCustom }.forEach { assertEquals(it.name, EqSpec.BAND_COUNT, it.gains!!.size) }
    }

    @Test
    fun disabledEqualizerIsBitExact16() {
        val input = pcm16(shortArrayOf(1, -1, 32_767, -32_768, 12_345, -23_456))
        val settings = EqualizerSettings(enabled = false, bands = List(10) { 6f }, preampDb = 3f)
        assertArrayEquals(input, run(settings, C.ENCODING_PCM_16BIT, input))
    }

    @Test
    fun enabledButFlatIsBitExact24() {
        val input = byteArrayOf(1, 2, 3, -1, -2, -3, 0x7F, 0x00, -0x80, 0x10, 0x20, 0x30)
        assertArrayEquals(input, run(EqualizerSettings(enabled = true), C.ENCODING_PCM_24BIT, input))
    }

    @Test
    fun preampScales16BitSamples() {
        val gainDb = (20 * log10(2.0)).toFloat() // ≈ +6.02 dB → ×2
        val out = run(EqualizerSettings(enabled = true, preampDb = gainDb), C.ENCODING_PCM_16BIT, pcm16(shortArrayOf(1_000, -1_000, 20_000, -20_000)))
        val samples = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        assertEquals(2_000.0, samples.get(0).toDouble(), 1.0)
        assertEquals(-2_000.0, samples.get(1).toDouble(), 1.0)
        // Saturación en lugar de desbordamiento
        assertEquals(32_767, samples.get(2).toInt())
        assertEquals(-32_768, samples.get(3).toInt())
    }

    @Test
    fun preampScales24BitSamples() {
        val gain = 10.0.pow(-6.0 / 20)
        val value = 4_000_000
        val input = byteArrayOf(
            (value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte(), ((value shr 16) and 0xFF).toByte(),
            0, 0, 0,
        )
        val out = run(EqualizerSettings(enabled = true, preampDb = -6f), C.ENCODING_PCM_24BIT, input)
        val decoded = (out[0].toInt() and 0xFF) or ((out[1].toInt() and 0xFF) shl 8) or (out[2].toInt() shl 16)
        assertEquals(value * gain, decoded.toDouble(), 1.0)
    }

    private fun run(settings: EqualizerSettings, encoding: Int, input: ByteArray): ByteArray {
        val processor = EqualizerAudioProcessor(MutableStateFlow(settings))
        processor.configure(AudioFormat(44_100, 2, encoding))
        @Suppress("DEPRECATION")
        processor.flush()
        processor.queueInput(ByteBuffer.allocateDirect(input.size).order(ByteOrder.nativeOrder()).put(input).flip() as ByteBuffer)
        val output = processor.output
        return ByteArray(output.remaining()).also { output.get(it) }
    }

    private fun pcm16(samples: ShortArray): ByteArray {
        val buffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { buffer.putShort(it) }
        return buffer.array()
    }
}
