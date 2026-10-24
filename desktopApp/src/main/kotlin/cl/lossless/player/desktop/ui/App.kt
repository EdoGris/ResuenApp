package cl.lossless.player.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.lossless.player.desktop.Album
import cl.lossless.player.desktop.Lang
import cl.lossless.player.desktop.LibraryScanner
import cl.lossless.player.desktop.LocalLang
import cl.lossless.player.desktop.Player
import cl.lossless.player.desktop.S
import cl.lossless.player.desktop.AppSettings
import cl.lossless.player.desktop.Track
import cl.lossless.player.desktop.sortedForLibrary
import cl.lossless.player.desktop.t
import cl.lossless.player.desktop.toAlbums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import javax.swing.JFileChooser

private enum class Tab { SONGS, ALBUMS, FOLDERS, SETTINGS }

/** Colores de la app Android en modo oscuro con el acento azul por defecto. */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EA8FF),
    onPrimary = Color(0xFF0A1A33),
    primaryContainer = Color(0xFF243A5C),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondaryContainer = Color(0xFF243A5C),
    onSecondaryContainer = Color(0xFFD6E4FF),
    background = Color(0xFF161616),
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceContainerLowest = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerHighest = Color(0xFF303030),
    outline = Color(0xFF4A4A4A),
    outlineVariant = Color(0xFF333333),
)

private fun String.searchKey(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

@Composable
fun App(player: Player) {
    var language by remember { mutableStateOf(AppSettings.language) }
    CompositionLocalProvider(LocalLang provides (language ?: Lang.system())) {
        MaterialTheme(colorScheme = DarkColors) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Library(player, language) { language = it; AppSettings.language = it }
            }
        }
    }
}

@Composable
private fun Library(player: Player, language: Lang?, onLanguage: (Lang?) -> Unit) {
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var scanning by remember { mutableStateOf<Int?>(null) }
    val folders = remember { mutableStateListOf<String>().apply { addAll(AppSettings.folders) } }
    var tab by remember { mutableStateOf(Tab.SONGS) }
    var query by remember { mutableStateOf("") }
    var openAlbum by remember { mutableStateOf<String?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }

    fun rescan() {
        if (scanning != null) return
        scanning = 0
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LibraryScanner.scan(folders.toList(), tracks) { n -> scanning = n }
            }
            tracks = result.sortedForLibrary()
            scanning = null
        }
    }

    LaunchedEffect(Unit) {
        tracks = withContext(Dispatchers.IO) { LibraryScanner.loadCache() }.sortedForLibrary()
        if (folders.isNotEmpty()) rescan()
    }

    val chooseTitle = t(S.chooseFolder)
    fun addFolder() {
        val chooser = JFileChooser().apply {
            dialogTitle = chooseTitle
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val path = chooser.selectedFile.path
            if (path !in folders) {
                folders += path
                AppSettings.folders = folders.toList()
                rescan()
            }
        }
    }

    fun removeFolder(path: String) {
        folders -= path
        AppSettings.folders = folders.toList()
        tracks = tracks.filterNot { it.path.startsWith(path) }
        rescan()
    }

    val state by player.state.collectAsState()
    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            position = player.positionMs()
            delay(150)
        }
    }

    val searchIndex = remember(tracks) { tracks.map { "${it.title} ${it.artist} ${it.album} ${it.albumArtist}".searchKey() } }
    val filtered = remember(tracks, query) {
        val q = query.trim().searchKey()
        if (q.isEmpty()) tracks else tracks.filterIndexed { i, _ -> q in searchIndex[i] }
    }
    val various = t(S.variousArtists)
    val albums = remember(filtered, various) { filtered.toAlbums(various) }
    val folderGroups = remember(filtered) {
        filtered.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (path, list) -> path to list }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "ResuenApp",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
                RailItem(Tab.SONGS, tab, Icons.Rounded.LibraryMusic, t(S.songs)) { tab = it; openAlbum = null; openFolder = null }
                RailItem(Tab.ALBUMS, tab, Icons.Rounded.Album, t(S.albums)) { tab = it; openAlbum = null }
                RailItem(Tab.FOLDERS, tab, Icons.Rounded.Folder, t(S.folders)) { tab = it; openFolder = null }
                Spacer(Modifier.weight(1f))
                RailItem(Tab.SETTINGS, tab, Icons.Rounded.Settings, t(S.settings)) { tab = it }
                Spacer(Modifier.height(12.dp))
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (tab != Tab.SETTINGS && (tracks.isNotEmpty() || folders.isNotEmpty())) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            when (tab) {
                                Tab.SONGS -> t(S.songs)
                                Tab.ALBUMS -> t(S.albums)
                                else -> t(S.folders)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            t(S.trackCount, filtered.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            placeholder = { Text(t(S.search)) },
                            leadingIcon = { Icon(Icons.Rounded.Search, null) },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.width(320.dp),
                        )
                    }
                }
                scanning?.let { n ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Text(t(S.scanning, n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val current = state.current?.path
                    when {
                        tab == Tab.SETTINGS -> SettingsScreen(
                            folders = folders,
                            language = language,
                            outputFormat = state.outputFormat,
                            onAdd = ::addFolder,
                            onRemove = ::removeFolder,
                            onRescan = ::rescan,
                            onLanguage = onLanguage,
                        )
                        folders.isEmpty() && tracks.isEmpty() -> EmptyLibrary(onAdd = ::addFolder)
                        tab == Tab.SONGS -> TrackList(filtered, current, showAlbum = true) { player.playQueue(filtered, it) }
                        tab == Tab.ALBUMS -> {
                            val album = openAlbum?.let { key -> albums.firstOrNull { it.key == key } }
                            if (album != null) {
                                AlbumDetail(album, current, onBack = { openAlbum = null }) { player.playQueue(album.tracks, it) }
                            } else {
                                AlbumGrid(albums) { a: Album -> openAlbum = a.key }
                            }
                        }
                        else -> {
                            val folder = openFolder?.let { path -> folderGroups.firstOrNull { it.first == path } }
                            if (folder != null) {
                                val list = remember(folder) { folder.second.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.file.name.lowercase() })) }
                                FolderDetail(folder.first, list, current, onBack = { openFolder = null }) { player.playQueue(list, it) }
                            } else {
                                FolderList(folderGroups) { openFolder = it }
                            }
                        }
                    }
                }
            }

            state.current?.let { track ->
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NowPlayingPanel(track, state.outputFormat, position, onSeek = player::seek, modifier = Modifier.width(400.dp).fillMaxHeight())
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PlayerBar(player, state, position)
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.RailItem(
    tab: Tab,
    selected: Tab,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: (Tab) -> Unit,
) {
    NavigationRailItem(
        selected = tab == selected,
        onClick = { onClick(tab) },
        icon = { Icon(icon, null) },
        label = { Text(label) },
    )
}
