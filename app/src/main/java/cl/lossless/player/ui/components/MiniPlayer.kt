package cl.lossless.player.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.lossless.player.R
import cl.lossless.player.covers.CoverRef
import cl.lossless.player.ui.player.PlayerUiState

/** Reproductor compacto fijado sobre la barra de navegación. Tocarlo abre "Reproduciendo". */
@Composable
fun MiniPlayer(
    state: PlayerUiState,
    number: String,
    cover: CoverRef?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit = {},
    swipeToSkip: Boolean = false,
) {
    val current = state.current ?: return
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                strokeCap = StrokeCap.Butt,
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .swipeToSkip(swipeToSkip, onNext = onNext, onPrevious = onPrevious)
                    .clickable(onClick = onExpand)
                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TrackThumb(number = number, cover = cover, model = current.artworkUri)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        current.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        current.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (state.isPlaying) R.string.library_pause else R.string.library_play),
                        modifier = Modifier.size(30.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = stringResource(R.string.library_next), modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}
