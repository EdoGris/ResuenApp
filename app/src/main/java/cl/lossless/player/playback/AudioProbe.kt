package cl.lossless.player.playback

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/** Características del audio de origen, leídas del archivo antes de reproducirlo. */
data class AudioSpec(val mime: String, val sampleRate: Int, val channelCount: Int) {
    val codecLabel: String
        get() = when (mime) {
            MediaFormat.MIMETYPE_AUDIO_MPEG -> "MP3 (con pérdida)"
            MediaFormat.MIMETYPE_AUDIO_FLAC -> "FLAC (sin pérdida)"
            "audio/alac" -> "ALAC (sin pérdida)"
            MediaFormat.MIMETYPE_AUDIO_AAC -> "AAC (con pérdida)"
            else -> mime
        }

    fun describe() = "$codecLabel · ${sampleRate / 1000.0} kHz · $channelCount canales"
}

object AudioProbe {
    fun probe(context: Context, uri: Uri): AudioSpec? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount)
                .map { extractor.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.let {
                    AudioSpec(
                        mime = it.getString(MediaFormat.KEY_MIME)!!,
                        sampleRate = it.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                        channelCount = it.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                    )
                }
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }
}
