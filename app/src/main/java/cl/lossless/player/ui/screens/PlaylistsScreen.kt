package cl.lossless.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.lossless.player.library.Playlist
import cl.lossless.player.library.Track
import cl.lossless.player.ui.components.EmptyState
import cl.lossless.player.ui.components.ExtraTrackAction
import cl.lossless.player.ui.components.PlaylistNameDialog
import cl.lossless.player.ui.components.ScreenHeader
import cl.lossless.player.ui.components.TrackActions
import cl.lossless.player.ui.components.TrackList

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    tracksById: Map<String, Track>,
    actions: TrackActions,
    onCreate: (String) -> Unit,
    onRename: (Playlist, String) -> Unit,
    onDelete: (Playlist) -> Unit,
    onRemoveTrack: (Playlist, Track) -> Unit,
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }

    val open = playlists.firstOrNull { it.id == openId }
    if (open != null) {
        BackHandler { openId = null }
        // Las pistas que ya no están en la biblioteca simplemente no se muestran.
        val tracks = remember(open, tracksById) { open.trackUris.mapNotNull(tracksById::get) }
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "", onBack = { openId = null })
            if (tracks.isEmpty()) {
                EmptyState(
                    icon = if (open.isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                    title = open.name,
                    message = if (open.isFavorites) "Marca canciones con ♥ desde el menú ⋮ o el reproductor."
                    else "Añade canciones desde el menú ⋮ de cualquier pista.",
                )
            } else {
                TrackList(
                    tracks = tracks,
                    actions = actions,
                    modifier = Modifier.fillMaxSize(),
                    extraActions = listOf(ExtraTrackAction("Quitar de la playlist") { onRemoveTrack(open, it) }),
                    header = {
                        item(key = "header") {
                            CollectionHeader(
                                title = open.name,
                                subtitle = if (open.isFavorites) "Tus canciones favoritas" else "Playlist local",
                                tracks = tracks,
                                cover = tracks.firstNotNullOfOrNull(actions.coverFor),
                                onEditCover = null,
                                onPlay = { actions.play(tracks, 0) },
                                onShuffle = { actions.shuffle(tracks) },
                            )
                        }
                    },
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title = "Playlists", actions = {
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, contentDescription = "Nueva playlist") }
            })
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(playlists, key = { it.id }) { p ->
                    PlaylistCard(
                        playlist = p,
                        availableCount = p.trackUris.count { it in tracksById },
                        onClick = { openId = p.id },
                        onRename = { renaming = p },
                        onDelete = { onDelete(p) },
                    )
                }
            }
        }
    }

    if (creating) {
        PlaylistNameDialog(
            title = "Nueva playlist",
            onConfirm = { onCreate(it); creating = false },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { p ->
        PlaylistNameDialog(
            title = "Renombrar playlist",
            initial = p.name,
            confirmLabel = "Guardar",
            onConfirm = { onRename(p, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    availableCount: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playlist.isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$availableCount pistas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (playlist.isFavorites) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}
