package cl.lossless.player.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.lossless.player.desktop.DesktopLyrics
import cl.lossless.player.desktop.LyricsResult
import cl.lossless.player.desktop.Player
import cl.lossless.player.desktop.PlayerState
import cl.lossless.player.desktop.S
import cl.lossless.player.desktop.Track
import cl.lossless.player.desktop.t
import cl.lossless.player.lyrics.Lyrics
import cl.lossless.player.lyrics.LyricsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NowPlayingPanel(track: Track, outputFormat: String?, positionMs: Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var lyrics by remember(track.path) { mutableStateOf<LyricsResult?>(null) }
    LaunchedEffect(track.path) {
        lyrics = withContext(Dispatchers.IO) { DesktopLyrics.load(track) }
    }
    Column(
        modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(track, size = 300.dp, px = 640, corner = 14.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            track.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOf(track.artist.ifBlank { t(S.unknownArtist) }, track.album).filter { it.isNotBlank() }.joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                if (outputFormat != null) t(S.outputFormat, track.formatLabel, outputFormat) else track.formatLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LyricsPane(lyrics, positionMs, onSeek)
        }
    }
}

@Composable
private fun LyricsPane(result: LyricsResult?, positionMs: Long, onSeek: (Long) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (result) {
        null -> CenteredNote(t(S.lyricsLoading))
        LyricsResult.None -> CenteredNote(t(S.lyricsNone))
        LyricsResult.Offline -> CenteredNote(t(S.lyricsOffline))
        is LyricsResult.Found -> {
            val lyrics = result.lyrics
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        lyrics.instrumental && !lyrics.isSynced && lyrics.plain.isNullOrBlank() -> CenteredNote(t(S.lyricsInstrumental))
                        lyrics.isSynced -> SyncedLyrics(lyrics, positionMs, onSeek)
                        else -> Text(
                            lyrics.plain.orEmpty(),
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                        )
                    }
                }
                Text(
                    t(S.lyricsSource, sourceLabel(lyrics.source)),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun sourceLabel(source: LyricsSource): String = t(
    when (source) {
        LyricsSource.SIDECAR -> S.sourceSidecar
        LyricsSource.LRCLIB -> S.sourceLrclib
        else -> S.sourceEmbedded
    },
)

@Composable
private fun SyncedLyrics(lyrics: Lyrics, positionMs: Long, onSeek: (Long) -> Unit) {
    val lines = lyrics.synced.orEmpty()
    val current = lyrics.lineIndexAt(positionMs)
    val listState = rememberLazyListState()
    LaunchedEffect(current) {
        if (current >= 0) listState.animateScrollToItem((current - 3).coerceAtLeast(0))
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 200.dp)) {
        itemsIndexed(lines) { i, line ->
            val active = i == current
            Text(
                line.text.ifBlank { "♪" },
                style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    active -> MaterialTheme.colorScheme.primary
                    i < current -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.fillMaxWidth()
                    .clickable { onSeek(line.timeMs) }
                    .padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CenteredNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun PlayerBar(player: Player, state: PlayerState, positionMs: Long) {
    val track = state.current
    val duration = state.durationMs.coerceAtLeast(1)
    var dragging by remember { mutableStateOf<Float?>(null) }
    var volume by remember { mutableFloatStateOf(player.volume) }

    Row(
        Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.width(300.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(track, size = 56.dp, px = 128)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(track?.title ?: t(S.nothingPlaying), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                val sub = state.error?.let { t(S.playbackError, it) } ?: track?.artist.orEmpty()
                Text(
                    sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = player::previous, enabled = track != null) { Icon(Icons.Rounded.SkipPrevious, t(S.previous)) }
                FilledIconButton(onClick = player::toggle, enabled = track != null, modifier = Modifier.size(44.dp)) {
                    if (state.playing) Icon(Icons.Rounded.Pause, t(S.pause)) else Icon(Icons.Rounded.PlayArrow, t(S.play))
                }
                IconButton(onClick = player::next, enabled = track != null) { Icon(Icons.Rounded.SkipNext, t(S.next)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shown = dragging?.let { (it * duration).toLong() } ?: positionMs
                Text(formatTime(shown), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
                Slider(
                    value = dragging ?: (positionMs.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { player.seek((it * duration).toLong()) }
                        dragging = null
                    },
                    enabled = track != null,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(24.dp),
                )
                Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
            }
        }

        Row(Modifier.width(200.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, t(S.volume), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = volume,
                onValueChange = { volume = it; player.setVolume(it) },
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
    }
}
