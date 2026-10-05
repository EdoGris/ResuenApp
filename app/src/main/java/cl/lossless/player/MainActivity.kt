package cl.lossless.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cl.lossless.player.library.MusicRepository
import cl.lossless.player.library.Track
import cl.lossless.player.playback.PlaybackService
import cl.lossless.player.playback.PlaybackStatus
import com.google.common.util.concurrent.ListenableFuture

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { PlayerScreen(controller.value) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({ controller.value = future.get() }, mainExecutor)
    }

    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller.value = null
        super.onStop()
    }
}

@Composable
private fun PlayerScreen(controller: MediaController?) {
    val context = LocalContext.current
    val repo = remember { MusicRepository(context) }

    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasPermission = result[Manifest.permission.READ_MEDIA_AUDIO] == true }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        )
    }

    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    LaunchedEffect(hasPermission) { if (hasPermission) tracks = repo.loadTracks() }

    // Estado del reproductor
    var isPlaying by remember { mutableStateOf(false) }
    var nowPlaying by remember { mutableStateOf("") }
    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                isPlaying = player.isPlaying
                val md = player.mediaMetadata
                nowPlaying = listOfNotNull(md.title, md.artist).joinToString(" — ")
            }
        }
        controller?.addListener(listener)
        controller?.let { isPlaying = it.isPlaying }
        onDispose { controller?.removeListener(listener) }
    }

    val source by PlaybackStatus.source.collectAsStateWithLifecycle()
    val output by PlaybackStatus.output.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Text(
            "Lossless Player",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )

        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text("Origen: $source", style = MaterialTheme.typography.bodySmall)
                Text(
                    "Salida: $output",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (output.startsWith("BIT-PERFECT")) FontWeight.Bold else null
                )
            }
        }

        if (!hasPermission) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Se necesita permiso para leer tu música")
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                    ListItem(
                        headlineContent = {
                            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text("${track.artist} · ${track.album}", maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = { Text(track.containerLabel) },
                        modifier = Modifier.clickable(enabled = controller != null) {
                            controller?.run {
                                setMediaItems(tracks.map { it.toMediaItem() }, index, 0L)
                                prepare()
                                play()
                            }
                        }
                    )
                }
            }
        }

        // Controles
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    nowPlaying.ifEmpty { "Nada en reproducción" },
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = { controller?.seekToPreviousMediaItem() }) { Text("⏮") }
                    TextButton(onClick = {
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                    }) { Text(if (isPlaying) "⏸" else "▶") }
                    TextButton(onClick = { controller?.seekToNextMediaItem() }) { Text("⏭") }
                }
            }
        }
    }
}
