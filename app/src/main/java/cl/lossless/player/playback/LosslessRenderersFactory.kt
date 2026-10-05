package cl.lossless.player.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import cl.lossless.player.playback.eq.EqualizerAudioProcessor
import cl.lossless.player.playback.eq.EqualizerRepository

/**
 * - Usa los decodificadores nativos de Android (MediaCodec) y, si un formato no está
 *   soportado en el dispositivo (típicamente ALAC), recurre a FFmpeg.
 * - Salida PCM float de 32 bits: evita truncar FLAC/ALAC de 24 bits a 16 bits.
 * - Sin parámetros de velocidad en el AudioTrack (no hay procesamiento de tiempo/tono).
 * - Ecualizador propio en el pipeline: si está apagado deja pasar los bytes intactos.
 */
@OptIn(UnstableApi::class)
class LosslessRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_ON) // nativo primero, FFmpeg de respaldo
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(true)
        .setEnableAudioTrackPlaybackParams(false)
        .setAudioProcessors(arrayOf(EqualizerAudioProcessor(EqualizerRepository.get(context).settings)))
        .build()
}
