package cl.lossless.player.lyrics

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder

/**
 * Decodifica la pista con MediaCodec y pasa las muestras a [VocalActivity], que hace la
 * detección (código compartido, sin dependencias de Android).
 */
object VocalAnalyzer {

    /** Decodifica y analiza la pista. null si el códec no está disponible (p. ej. ALAC). */
    suspend fun analyze(context: Context, uri: Uri): List<VocalSegment>? {
        val features = extract(context, uri) ?: return null
        return VocalActivity.detect(features)
    }

    // ---------- Decodificación ----------

    private suspend fun extract(context: Context, uri: Uri): VocalActivity.Features? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            codec = runCatching { MediaCodec.createDecoderByType(mime) }.getOrNull() ?: return null
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var accumulator = VocalActivity.Accumulator(sampleRate)
            val info = MediaCodec.BufferInfo()
            var inputDone = false

            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val out = codec.outputFormat
                        sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        else AudioFormat.ENCODING_PCM_16BIT
                        if (accumulator.count == 0) accumulator = VocalActivity.Accumulator(sampleRate)
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        while (buffer.remaining() >= channels * bytesPerSample(encoding)) {
                            val left = readSample(buffer, encoding)
                            val right = if (channels >= 2) readSample(buffer, encoding) else left
                            repeat(channels - 2) { readSample(buffer, encoding) }
                            accumulator.add(left, right)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return accumulator.features()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun bytesPerSample(encoding: Int) = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_8BIT -> 1
        else -> 2
    }

    private fun readSample(b: java.nio.ByteBuffer, encoding: Int): Double = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> b.getFloat().toDouble()
        AudioFormat.ENCODING_PCM_32BIT -> b.getInt() / 2_147_483_648.0
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
            val b0 = b.get().toInt() and 0xFF; val b1 = b.get().toInt() and 0xFF; val b2 = b.get().toInt()
            ((b2 shl 16) or (b1 shl 8) or b0) / 8_388_608.0
        }
        AudioFormat.ENCODING_PCM_8BIT -> ((b.get().toInt() and 0xFF) - 128) / 128.0
        else -> b.getShort() / 32_768.0
    }
}
