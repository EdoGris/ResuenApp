package cl.lossless.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import cl.lossless.player.MainActivity
import cl.lossless.player.R
import android.net.Uri
import android.view.KeyEvent
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
    private lateinit var playbackPrefs: PlaybackPrefs
    private val io: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun onCreate() {
        super.onCreate()
        bitPerfect = BitPerfectController(this)
        playbackPrefs = PlaybackPrefs(this)

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

        // Notificación multimedia con el isotipo de la app en la barra de estado.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_resuen) }
        )

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            // Tocar la notificación (o el reproductor de la pantalla de bloqueo) abre la app
            // directamente en "Reproduciendo".
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_OPEN_NOW_PLAYING, true)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .setBitmapLoader(
                LogoFallbackBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader(this))) { logoBitmap }
            )
            .build()
    }

    /** Logo de la app como carátula de respaldo (se genera una sola vez). */
    private val logoBitmap: Bitmap by lazy {
        getDrawable(R.mipmap.ic_launcher)!!.toBitmap(512, 512)
    }

    /** Lee el archivo y configura la salida (bit-perfect si hay DAC USB). */
    private fun prepareOutputFor(item: MediaItem) {
        val uri = item.localConfiguration?.uri ?: Uri.parse(item.mediaId)
        val spec = AudioProbe.probe(this, uri) ?: return
        PlaybackStatus.source.value = spec.describe()
        bitPerfect.apply(spec)
    }

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * Botones de audífonos y controles Bluetooth. Los gestos táctiles de los audífonos
         * (Sony, Sennheiser, Jabra…) los traduce el propio audífono a estas teclas estándar:
         * play/pausa, siguiente y anterior ya los atiende Media3 (también el doble toque de
         * play/pausa, que avanza). Aquí solo se cambia el avance y retroceso rápido.
         */
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            val event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
            val skipNext = when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> true
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> false
                else -> return false
            }
            if (!playbackPrefs.mediaKeysSkip) return false
            // Una sola vez por pulsación, aunque se mantenga presionado.
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                val player = session.player
                if (skipNext) player.seekToNextMediaItem()
                else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                else player.seekTo(0)
            }
            return true
        }


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
