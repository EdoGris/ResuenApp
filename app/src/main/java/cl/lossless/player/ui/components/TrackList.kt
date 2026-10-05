package cl.lossless.player.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.lossless.player.covers.CoverRef
import cl.lossless.player.library.Track

/** Todo lo que una fila de pista necesita saber y puede hacer. */
class TrackActions(
    val currentMediaId: String?,
    val favorites: Set<String>,
    val coverFor: (Track) -> CoverRef?,
    val play: (tracks: List<Track>, index: Int) -> Unit,
    val shuffle: (tracks: List<Track>) -> Unit,
    val playNext: (Track) -> Unit,
    val enqueue: (Track) -> Unit,
    val toggleFavorite: (Track) -> Unit,
    val addToPlaylist: (Track) -> Unit,
) {
    fun isCurrent(track: Track) = track.uri.toString() == currentMediaId
    fun isFavorite(track: Track) = track.uri.toString() in favorites
}

/** Opción extra del menú ⋮ (p. ej. "Quitar de la playlist"). */
class ExtraTrackAction(val label: String, val onClick: (Track) -> Unit)

/** Tarjeta de pista: número/carátula, título, "artista • álbum", duración y calidad. */
@Composable
fun TrackCard(
    track: Track,
    number: String,
    actions: TrackActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    extraActions: List<ExtraTrackAction> = emptyList(),
) {
    val current = actions.isCurrent(track)
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (current) colors.primaryContainer else colors.surfaceContainer,
        border = BorderStroke(1.dp, if (current) colors.primary else colors.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackThumb(number = number, cover = actions.coverFor(track))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (current) colors.onPrimaryContainer else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current) colors.onPrimaryContainer.copy(alpha = 0.75f) else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatDuration(track.durationMs),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                FormatBadge(track.qualityLabel)
            }
            TrackMenu(track, actions, extraActions)
        }
    }
}

@Composable
private fun TrackMenu(track: Track, actions: TrackActions, extraActions: List<ExtraTrackAction>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Reproducir a continuación") },
                leadingIcon = { Icon(Icons.Rounded.SkipNext, null) },
                onClick = { open = false; actions.playNext(track) },
            )
            DropdownMenuItem(
                text = { Text("Añadir a la cola") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                onClick = { open = false; actions.enqueue(track) },
            )
            val favorite = actions.isFavorite(track)
            DropdownMenuItem(
                text = { Text(if (favorite) "Quitar de favoritos" else "Añadir a favoritos") },
                leadingIcon = { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                onClick = { open = false; actions.toggleFavorite(track) },
            )
            DropdownMenuItem(
                text = { Text("Añadir a playlist…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                onClick = { open = false; actions.addToPlaylist(track) },
            )
            extraActions.forEach { extra ->
                DropdownMenuItem(
                    text = { Text(extra.label) },
                    onClick = { open = false; extra.onClick(track) },
                )
            }
        }
    }
}

/** Lista de pistas; tocar una reproduce la lista completa desde ella. */
@Composable
fun TrackList(
    tracks: List<Track>,
    actions: TrackActions,
    modifier: Modifier = Modifier,
    number: (index: Int, track: Track) -> String = { i, _ -> "%02d".format(i + 1) },
    extraActions: List<ExtraTrackAction> = emptyList(),
    header: (LazyListScope.() -> Unit)? = null,
) {
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        header?.invoke(this)
        itemsIndexed(tracks, key = { i, t -> "${t.uri}#$i" }) { i, track ->
            TrackCard(
                track = track,
                number = number(i, track),
                actions = actions,
                onClick = { actions.play(tracks, i) },
                extraActions = extraActions,
            )
        }
    }
}

/** Número de pista del álbum ("01"), o la posición si no tiene etiqueta. */
fun albumTrackNumber(index: Int, track: Track): String =
    "%02d".format(if (track.trackNumber > 0) track.trackNumber % 1000 else index + 1)
