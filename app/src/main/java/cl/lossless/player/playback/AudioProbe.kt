package cl.lossless.player.playback

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import cl.lossless.player.R

/** Características del audio de origen, leídas del archivo antes de reproducirlo. */
data class AudioSpec(val mime: String, val sampleRate: Int, val channelCount: Int) {
    fun codecLabel(context: Context): String = when (mime) {
        MediaFormat.MIMETYPE_AUDIO_MPEG -> context.getString(R.string.player_codec_lossy, "MP3")
        MediaFormat.MIMETYPE_AUDIO_FLAC -> context.getString(R.string.player_codec_lossless, "FLAC")
        "audio/alac" -> context.getString(R.string.player_codec_lossless, "ALAC")
        MediaFormat.MIMETYPE_AUDIO_RAW -> context.getString(R.string.player_codec_lossless, "WAV")
        MediaFormat.MIMETYPE_AUDIO_AAC -> context.getString(R.string.player_codec_lossy, "AAC")
        else -> mime
    }

    fun describe(context: Context): String = context.resources.getQuantityString(
        R.plurals.player_source_description,
        channelCount,
        codecLabel(context),
        (sampleRate / 1000.0).toString(),
        channelCount,
    )
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
