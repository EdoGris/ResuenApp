package cl.lossless.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import cl.lossless.player.lyrics.LyricLine
import cl.lossless.player.ui.lyrics.LyricsUiState
import cl.lossless.player.ui.lyrics.LyricsView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import cl.lossless.player.covers.CoverRef
import cl.lossless.player.library.Track
import cl.lossless.player.ui.components.FormatBadge
import cl.lossless.player.ui.components.LargeCover
import cl.lossless.player.ui.components.formatDuration
import cl.lossless.player.ui.player.PlayerActions
import cl.lossless.player.ui.player.PlayerUiState

/** Pantalla completa "Reproduciendo". */
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    track: Track?,
    cover: CoverRef?,
    isFavorite: Boolean,
    audioOutput: String,
    equalizerActive: Boolean,
    player: PlayerActions,
    onCollapse: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenEqualizer: () -> Unit,
    lyrics: LyricsUiState,
    onLoadLyrics: (force: Boolean) -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onSaveLyricsSync: suspend (List<LyricLine>) -> Boolean,
) {
    val current = state.current
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by rememberSaveable { mutableStateOf(false) }

    // La letra se busca solo cuando se muestra (y al cambiar de pista mientras está visible).
    LaunchedEffect(showLyrics, track?.uri) {
        if (showLyrics) onLoadLyrics(false)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Barra superior
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Cerrar", modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "REPRODUCIENDO",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        current?.album.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggleFavorite, enabled = current != null) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (showLyrics) {
                LyricsView(
                    state = lyrics,
                    player = state,
                    onSeek = player::seekTo,
                    onAdjustOffset = onAdjustLyricsOffset,
                    onResetOffset = onResetLyricsOffset,
                    onRetry = { onLoadLyrics(true) },
                    onPlayFrom = player::playFrom,
                    onSaveSync = onSaveLyricsSync,
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                )
            } else {
                Spacer(Modifier.weight(0.6f))

                LargeCover(
                    cover = cover,
                    model = current?.artworkUri,
                    modifier = Modifier.widthIn(max = 420.dp).aspectRatio(1f),
                )

                Spacer(Modifier.weight(0.6f))
            }

            // Título, artista y calidad
            Text(
                current?.title ?: "Nada en reproducción",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().basicMarquee(),
                textAlign = TextAlign.Center,
            )
            Text(
                current?.artist.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                track?.let { FormatBadge(it.qualityDetails) }
                // Con el EQ procesando, la señal ya no es idéntica al archivo: no se anuncia bit-perfect.
                if (equalizerActive) FormatBadge("EQ", emphasized = true)
                else if (audioOutput.startsWith("BIT-PERFECT")) FormatBadge("BIT-PERFECT", emphasized = true)
            }

            Spacer(Modifier.height(20.dp))
            SeekBar(state, onSeek = player::seekTo)

            // Controles
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToggleIcon(
                    icon = Icons.Rounded.Shuffle,
                    active = state.shuffle,
                    description = "Aleatorio",
                    onClick = player::toggleShuffle,
                )
                IconButton(onClick = player::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = player::togglePlayPause,
                    modifier = Modifier.size(76.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(42.dp),
                    )
                }
                IconButton(onClick = player::next, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(36.dp))
                }
                ToggleIcon(
                    icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    active = state.repeatMode != Player.REPEAT_MODE_OFF,
                    description = "Repetir",
                    onClick = player::cycleRepeat,
                )
            }

            if (!showLyrics) Spacer(Modifier.weight(0.4f)) else Spacer(Modifier.height(8.dp))

            // Accesos rápidos
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QuickAction(Icons.Rounded.Equalizer, "Ecualizador", active = equalizerActive, onClick = onOpenEqualizer)
                QuickAction(Icons.Rounded.Lyrics, "Letra", active = showLyrics, onClick = { showLyrics = !showLyrics })
                QuickAction(Icons.AutoMirrored.Rounded.QueueMusic, "Cola (${state.queue.size})", active = false) {
                    showQueue = true
                }
            }
        }
    }

    if (showQueue) {
        QueueSheet(state, player, onDismiss = { showQueue = false })
    }
}

/** Acceso compacto: ícono sobre etiqueta, resaltado si está activo. */
@Composable
private fun QuickAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

@Composable
private fun SeekBar(state: PlayerUiState, onSeek: (Long) -> Unit) {
    // Mientras se arrastra, se muestra la posición del dedo en vez de la real
    var dragging by remember { mutableStateOf<Float?>(null) }
    val max = state.durationMs.coerceAtLeast(1L).toFloat()
    val value = dragging ?: state.positionMs.toFloat().coerceIn(0f, max)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.toLong()) }
                dragging = null
            },
            valueRange = 0f..max,
            enabled = state.durationMs > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration(value.toLong()),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatDuration(state.durationMs),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (active) {
                Box(
                    Modifier
                        .padding(top = 30.dp)
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(state: PlayerUiState, player: PlayerActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Cola de reproducción",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.currentIndex.coerceAtLeast(0))
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(state.queue, key = { i, e -> "${e.mediaId}#$i" }) { i, entry ->
                val isCurrent = i == state.currentIndex
                ListItem(
                    headlineContent = {
                        Text(
                            entry.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                        )
                    },
                    supportingContent = { Text(entry.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = {
                        Text(
                            "%02d".format(i + 1),
                            fontFamily = FontFamily.Monospace,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        if (!isCurrent) {
                            IconButton(onClick = { player.removeQueueItem(i) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Quitar de la cola")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    ),
                    modifier = Modifier.clickable { player.skipToQueueItem(i) },
                )
            }
        }
    }
}
