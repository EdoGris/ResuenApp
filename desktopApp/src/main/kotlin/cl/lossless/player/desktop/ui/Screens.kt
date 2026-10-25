package cl.lossless.player.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.lossless.player.desktop.APP_VERSION
import cl.lossless.player.desktop.Album
import cl.lossless.player.desktop.Covers
import cl.lossless.player.desktop.Lang
import cl.lossless.player.desktop.S
import cl.lossless.player.desktop.Track
import cl.lossless.player.desktop.t
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/** Pocos hilos para leer carátulas: abrir cientos de archivos a la vez satura el disco. */
private val coverDispatcher = Executors.newFixedThreadPool(3) { r -> Thread(r, "covers").apply { isDaemon = true } }
    .asCoroutineDispatcher()

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun CoverImage(track: Track?, size: Dp, px: Int, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val key = track?.albumKey
    var image by remember(key, px) { mutableStateOf<ImageBitmap?>(key?.let { Covers.cached(it, px) }) }
    LaunchedEffect(key, px) {
        if (track != null && image == null && !Covers.isCached(track.albumKey, px)) {
            image = withContext(coverDispatcher) { Covers.load(track, px) }
        }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size / 2.5f))
        }
    }
}

@Composable
fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(t(S.emptyTitle), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(t(S.emptyBody), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(t(S.addFolder))
        }
    }
}

@Composable
fun TrackList(tracks: List<Track>, currentPath: String?, showAlbum: Boolean, header: (@Composable () -> Unit)? = null, onPlay: (Int) -> Unit) {
    if (tracks.isEmpty() && header == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(t(S.noResults), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        if (header != null) item { header() }
        itemsIndexed(tracks, key = { _, it -> it.path }) { i, track ->
            TrackRow(track, number = if (showAlbum) null else track.trackNumber, playing = track.path == currentPath, showAlbum = showAlbum) { onPlay(i) }
        }
    }
}

@Composable
private fun TrackRow(track: Track, number: Int?, playing: Boolean, showAlbum: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (playing) colors.primaryContainer.copy(alpha = 0.45f) else colors.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            when {
                playing -> Icon(Icons.Rounded.GraphicEq, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                number != null && number > 0 -> Text("$number", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                else -> Icon(Icons.Rounded.MusicNote, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1.4f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium,
                color = if (playing) colors.primary else colors.onSurface,
            )
            Text(
                track.artist.ifBlank { t(S.unknownArtist) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        if (showAlbum) {
            Text(
                track.album.ifBlank { track.folderName },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        Text(track.formatLabel, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(formatTime(track.durationMs), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.width(52.dp))
    }
}

@Composable
fun AlbumGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    if (albums.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(t(S.noResults), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(albums, key = { it.key }) { album ->
            Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable { onOpen(album) }.padding(6.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    CoverImage(album.tracks.first(), size = 200.dp, px = 360, modifier = Modifier.fillMaxSize(), corner = 10.dp)
                }
                Spacer(Modifier.height(8.dp))
                Text(album.title.ifBlank { t(S.unknownAlbum) }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    album.artist.ifBlank { t(S.unknownArtist) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AlbumDetail(album: Album, currentPath: String?, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    TrackList(album.tracks, currentPath, showAlbum = false, header = {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(S.back)) }
            CoverImage(album.tracks.first(), size = 160.dp, px = 360, corner = 10.dp)
            Column(Modifier.weight(1f)) {
                Text(album.title.ifBlank { t(S.unknownAlbum) }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(album.artist.ifBlank { t(S.unknownArtist) }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${t(S.trackCount, album.tracks.size)} · ${album.tracks.first().formatLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { onPlay(0) }) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(t(S.playAll))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }, onPlay = onPlay)
}

@Composable
fun FolderList(folders: List<Pair<String, List<Track>>>, onOpen: (String) -> Unit) {
    if (folders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(t(S.noResults), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
        items(folders, key = { it.first }) { (path, list) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(path) }.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(t(S.trackCount, list.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FolderDetail(path: String, tracks: List<Track>, currentPath: String?, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    TrackList(tracks, currentPath, showAlbum = true, header = {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(S.back)) }
            Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(File(path).name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = { onPlay(0) }) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(t(S.playAll))
            }
        }
    }, onPlay = onPlay)
}

@Composable
fun SettingsScreen(
    folders: List<String>,
    language: Lang?,
    outputFormat: String?,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onRescan: () -> Unit,
    onLanguage: (Lang?) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(t(S.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

        SettingsCard(t(S.libraryFolders)) {
            folders.forEach { path ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(path, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemove(path) }) { Icon(Icons.Rounded.Delete, t(S.removeFolder)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text(t(S.addFolder))
                }
                if (folders.isNotEmpty()) {
                    OutlinedButton(onClick = onRescan) {
                        Icon(Icons.Rounded.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text(t(S.rescan))
                    }
                }
            }
        }

        SettingsCard(t(S.language)) {
            LanguageOption(t(S.languageSystem), selected = language == null) { onLanguage(null) }
            Lang.entries.forEach { lang ->
                LanguageOption(lang.nativeName, selected = language == lang) { onLanguage(lang) }
            }
        }

        SettingsCard(t(S.output)) {
            Text(t(S.outputShared), fontWeight = FontWeight.Medium)
            if (outputFormat != null) Text(outputFormat, color = MaterialTheme.colorScheme.primary)
            Text(t(S.outputNote), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsCard(t(S.about)) {
            Text("ResuenApp · ${t(S.version, APP_VERSION)}")
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().width(720.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}
