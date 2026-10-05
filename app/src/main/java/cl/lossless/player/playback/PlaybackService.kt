package cl.lossless.player.playback

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var bitPerfect: BitPerfectController
    private val io: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun onCreate() {
        super.onCreate()
        bitPerfect = BitPerfectController(this)

        val player = ExoPlayer.Builder(this, LosslessRenderersFactory(this))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pausa al desconectar audífonos/DAC
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                // Cambio automático de pista: reconfigura la salida si cambia la frecuencia.
                if (item != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    io.execute { prepareOutputFor(item) }
                }
            }
        })

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()
    }

    /** Lee el archivo y configura la salida (bit-perfect si hay DAC USB). */
    private fun prepareOutputFor(item: MediaItem) {
        val uri = item.localConfiguration?.uri ?: Uri.parse(item.mediaId)
        val spec = AudioProbe.probe(this, uri) ?: return
        PlaybackStatus.source.value = spec.describe()
        bitPerfect.apply(spec)
    }

    private inner class SessionCallback : MediaSession.Callback {
        // El MediaController no envía la URI al servicio: se reconstruye desde mediaId.
        private fun resolve(items: List<MediaItem>) =
            items.map { it.buildUpon().setUri(it.mediaId).build() }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            io.submit<MutableList<MediaItem>> { resolve(mediaItems).toMutableList() }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            io.submit<MediaSession.MediaItemsWithStartPosition> {
                val resolved = resolve(mediaItems)
                val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex
                // Configurar la salida ANTES de que ExoPlayer cree el AudioTrack.
                resolved.getOrNull(index)?.let { prepareOutputFor(it) }
                MediaSession.MediaItemsWithStartPosition(resolved, index, startPositionMs)
            }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        bitPerfect.release()
        io.shutdown()
        super.onDestroy()
    }
}
