package cl.lossless.player

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cl.lossless.player.playback.PlaybackService
import cl.lossless.player.ui.LibraryViewModel
import cl.lossless.player.ui.LosslessApp
import cl.lossless.player.ui.theme.LosslessTheme
import cl.lossless.player.update.UpdatePrompt
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)
    /** Se incrementa cada vez que se pide abrir "Reproduciendo" (p. ej. desde la notificación). */
    private val openNowPlaying = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Solo en un arranque nuevo: al recrear la actividad (rotación) el intent original se repetiría.
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val vm: LibraryViewModel = viewModel()
            val ui by vm.ui.collectAsStateWithLifecycle()
            LosslessTheme(
                mode = ui.themeMode,
                accent = ui.accent,
                tintedText = ui.tintedText,
                dynamicColor = ui.dynamicColor,
                font = ui.font,
                textSize = ui.textSize,
            ) {
                LosslessApp(
                    player = controller.value,
                    vm = vm,
                    openNowPlayingSignal = openNowPlaying.intValue,
                )
                UpdatePrompt()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_NOW_PLAYING, false) == true) openNowPlaying.intValue++
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        // Si onStop libera el future antes de conectar, viene cancelado: no hay controlador.
        future.addListener({
            if (!future.isCancelled) controller.value = runCatching { future.get() }.getOrNull()
        }, mainExecutor)
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller.value = null
        super.onStop()
    }

    companion object {
        const val EXTRA_OPEN_NOW_PLAYING = "cl.lossless.player.OPEN_NOW_PLAYING"
    }
}
