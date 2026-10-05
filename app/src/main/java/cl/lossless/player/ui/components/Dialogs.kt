package cl.lossless.player.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.lossless.player.covers.CoverCandidate
import cl.lossless.player.covers.CoverRef
import cl.lossless.player.library.Playlist
import cl.lossless.player.ui.CoverDialogState

/** Elegir carátula: imagen propia del teléfono o resultados de búsqueda en internet. */
@Composable
fun CoverDialog(
    dialog: CoverDialogState,
    current: CoverRef?,
    writesToFolder: Boolean,
    onChoose: (CoverCandidate) -> Unit,
    onChooseLocal: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    // Galería: selector de fotos del sistema (no requiere permisos)
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onChooseLocal) }
    // Archivos: cualquier imagen (Descargas, carpeta del disco, tarjeta SD…)
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onChooseLocal) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        title = { Text(dialog.album.title, maxLines = 2) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(Modifier.size(56.dp), cover = current)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(dialog.album.artist, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (writesToFolder) "Se guardará también como cover.jpg en la carpeta del disco"
                            else "Se guardará en la app. Para crear cover.jpg, elige la carpeta " +
                                "de tu música en Ajustes y activa «Guardar cover.jpg».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Desde el teléfono", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            galleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !dialog.saving,
                    ) {
                        Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Galería")
                    }
                    FilledTonalButton(
                        onClick = { filePicker.launch(arrayOf("image/*")) },
                        enabled = !dialog.saving,
                    ) {
                        Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Archivos")
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Buscar en internet", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                when {
                    dialog.searching || dialog.saving -> Box(
                        Modifier.fillMaxWidth().height(120.dp), Alignment.Center
                    ) { CircularProgressIndicator() }
                    else -> {
                        dialog.message?.let { Text(it) }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.heightIn(max = 420.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(dialog.candidates, key = { it.fullUrl }) { c ->
                                Column(Modifier.clickable { onChoose(c) }) {
                                    CoverImage(Modifier.fillMaxWidth().aspectRatio(1f), model = c.previewUrl)
                                    Text(
                                        c.title, style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        c.artist, style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${c.source}${c.year?.let { " · $it" } ?: ""} · ${(c.score * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

/** Pide un nombre (crear o renombrar playlist). */
@Composable
fun PlaylistNameDialog(
    title: String,
    initial: String = "",
    confirmLabel: String = "Crear",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Nombre") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Elegir a qué playlist añadir una pista (o crear una nueva). */
@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onSelect: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a playlist") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text("Nueva playlist…") },
                        leadingContent = { Icon(Icons.Rounded.Add, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = onCreateNew),
                    )
                }
                items(playlists, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name) },
                        supportingContent = { Text("${p.trackUris.size} pistas") },
                        leadingContent = {
                            Icon(if (p.isFavorites) Icons.Rounded.Favorite else Icons.AutoMirrored.Rounded.QueueMusic, null)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onSelect(p) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
