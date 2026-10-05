package cl.lossless.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.lossless.player.library.Track
import cl.lossless.player.library.folderDisplayPath
import cl.lossless.player.library.folderNode
import cl.lossless.player.library.volumeLabel
import cl.lossless.player.ui.components.EmptyState
import cl.lossless.player.ui.components.ScreenHeader
import cl.lossless.player.ui.components.TrackActions
import cl.lossless.player.ui.components.TrackCard
import cl.lossless.player.ui.components.albumTrackNumber

/** Explorador tipo administrador de archivos sobre las carpetas que contienen música. */
@Composable
fun FoldersScreen(
    tracks: List<Track>,
    actions: TrackActions,
    onPickFolder: () -> Unit,
    onExcludeFolder: (List<String>) -> Unit,
) {
    // Ruta actual como "primary/Music/Rock" (vacía = raíz con los volúmenes)
    var path by rememberSaveable { mutableStateOf("") }
    val segments = remember(path) { path.split('/').filter { it.isNotEmpty() } }
    val node = remember(tracks, segments) { tracks.folderNode(segments) }

    fun open(newSegments: List<String>) { path = newSegments.joinToString("/") }

    BackHandler(enabled = segments.isNotEmpty()) { open(segments.dropLast(1)) }

    // Si la carpeta actual se omitió (o ya no existe), subir hasta una que tenga música.
    if (segments.isNotEmpty() && node.subfolders.isEmpty() && node.tracks.isEmpty()) {
        LaunchedEffect(path) { open(segments.dropLast(1)) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (segments.isEmpty()) "Carpetas" else segments.last().let { if (segments.size == 1) volumeLabel(it) else it },
            trailingText = folderDisplayPath(segments),
            onBack = if (segments.isEmpty()) null else ({ open(segments.dropLast(1)) }),
            actions = {
                IconButton(onClick = onPickFolder) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Elegir carpeta (SD / USB)")
                }
            },
        )

        if (node.subfolders.isEmpty() && node.tracks.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.FolderOff,
                title = "Carpeta vacía",
                message = "No hay archivos de audio compatibles en esta ubicación.",
                actionLabel = "Elegir otra carpeta",
                onAction = onPickFolder,
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(node.subfolders, key = { "dir:" + it.name }) { entry ->
                FolderCard(
                    name = if (segments.isEmpty()) volumeLabel(entry.name) else entry.name,
                    path = null,
                    detail = "${entry.trackCount} pistas",
                    onExclude = { onExcludeFolder(entry.segments) },
                ) { open(entry.segments) }
            }
            if (node.tracks.isNotEmpty()) {
                item(key = "actions") {
                    Row(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(onClick = { actions.play(node.tracks, 0) }) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Reproducir carpeta")
                        }
                        OutlinedButton(onClick = { actions.shuffle(node.tracks) }) {
                            Icon(Icons.Rounded.Shuffle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Aleatorio")
                        }
                    }
                }
                itemsIndexed(node.tracks, key = { i, t -> "track:${t.uri}#$i" }) { i, track ->
                    TrackCard(
                        track = track,
                        number = albumTrackNumber(i, track),
                        actions = actions,
                        onClick = { actions.play(node.tracks, i) },
                    )
                }
            }
        }
    }
}
