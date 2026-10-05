package cl.lossless.player.playback.eq

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Ecualizador de 10 bandas + preamp + refuerzo de graves + amplitud estéreo, aplicado dentro
 * del pipeline de ExoPlayer (antes del AudioTrack). Funciona igual con altavoz, Bluetooth o DAC USB.
 *
 * Mantiene el formato de entrada (16/24/32 bits o float): cuando el ecualizador está apagado
 * o en plano, copia los bytes sin tocarlos, así la salida sigue siendo bit-perfect.
 */
@OptIn(UnstableApi::class)
class EqualizerAudioProcessor(private val settings: StateFlow<EqualizerSettings>) : BaseAudioProcessor() {

    private var appliedSettings: EqualizerSettings? = null
    /** [canal][etapa] */
    private var filters: Array<Array<Biquad>> = emptyArray()
    private var preampGain = 1.0
    private var wasProcessing = false

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_24BIT, C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT ->
                inputAudioFormat
            else -> AudioFormat.NOT_SET // otros formatos pasan sin este procesador
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val output = replaceOutputBuffer(remaining)
        val s = settings.value

        if (!s.isProcessing) {
            wasProcessing = false
            output.put(inputBuffer)
            output.flip()
            return
        }
        if (s != appliedSettings) rebuild(s)
        if (!wasProcessing) {
            filters.forEach { channel -> channel.forEach(Biquad::reset) } // sin "pop" al activar
            wasProcessing = true
        }

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        val format = inputAudioFormat
        val channels = format.channelCount
        val frameSize = format.bytesPerFrame
        val stereoWidth = if (channels == 2 && s.virtualizer > 0f) 1.0 + 1.5 * s.virtualizer else 1.0
        // Compensa el aumento de nivel que produce ensanchar la imagen estéreo.
        val widthCompensation = 1.0 / (1.0 + 0.25 * (stereoWidth - 1.0))
        val frame = DoubleArray(channels)

        while (inputBuffer.remaining() >= frameSize) {
            for (ch in 0 until channels) {
                var x = read(inputBuffer, format.encoding) * preampGain
                for (stage in filters[ch]) x = stage.process(x)
                frame[ch] = x
            }
            if (stereoWidth != 1.0) {
                val mid = (frame[0] + frame[1]) * 0.5
                val side = (frame[0] - frame[1]) * 0.5 * stereoWidth
                frame[0] = (mid + side) * widthCompensation
                frame[1] = (mid - side) * widthCompensation
            }
            for (ch in 0 until channels) write(output, format.encoding, frame[ch])
        }
        inputBuffer.position(inputBuffer.limit()) // descarta un frame incompleto (no debería ocurrir)
        output.flip()
    }

    private fun rebuild(s: EqualizerSettings) {
        val format = inputAudioFormat
        val stages = EqMath.stages(s, format.sampleRate.toDouble())
        val sameShape = filters.size == format.channelCount && filters.firstOrNull()?.size == stages.size
        if (sameShape) {
            // Mismo número de etapas: solo se actualizan coeficientes y se conserva el estado.
            filters.forEach { channel -> channel.forEachIndexed { i, f -> f.setCoefficients(stages[i]) } }
        } else {
            filters = Array(format.channelCount) { Array(stages.size) { i -> Biquad(stages[i]) } }
        }
        preampGain = 10.0.pow(s.preampDb / 20.0)
        appliedSettings = s
    }

    override fun onFlush() {
        appliedSettings = null
        filters = emptyArray()
        wasProcessing = false
    }

    override fun onReset() = onFlush()

    // ---------- Conversión de muestras (little-endian, como entrega ExoPlayer) ----------

    private fun read(buffer: ByteBuffer, encoding: Int): Double = when (encoding) {
        C.ENCODING_PCM_16BIT -> buffer.getShort() / 32_768.0
        C.ENCODING_PCM_24BIT -> {
            val b0 = buffer.get().toInt() and 0xFF
            val b1 = buffer.get().toInt() and 0xFF
            val b2 = buffer.get().toInt() // con signo
            ((b2 shl 16) or (b1 shl 8) or b0) / 8_388_608.0
        }
        C.ENCODING_PCM_32BIT -> buffer.getInt() / 2_147_483_648.0
        else -> buffer.getFloat().toDouble()
    }

    private fun write(buffer: ByteBuffer, encoding: Int, sample: Double) {
        when (encoding) {
            C.ENCODING_PCM_16BIT ->
                buffer.putShort((sample * 32_768.0).roundToLong().coerceIn(-32_768L, 32_767L).toInt().toShort())
            C.ENCODING_PCM_24BIT -> {
                val v = (sample * 8_388_608.0).roundToLong().coerceIn(-8_388_608L, 8_388_607L).toInt()
                buffer.put((v and 0xFF).toByte())
                buffer.put(((v shr 8) and 0xFF).toByte())
                buffer.put(((v shr 16) and 0xFF).toByte())
            }
            C.ENCODING_PCM_32BIT ->
                buffer.putInt((sample * 2_147_483_648.0).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
            else -> buffer.putFloat(sample.coerceIn(-1.0, 1.0).toFloat())
        }
    }
}
