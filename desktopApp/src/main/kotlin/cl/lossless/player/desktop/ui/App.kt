package cl.lossless.player.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.lossless.player.desktop.APP_VERSION
import cl.lossless.player.desktop.AppSettings
import cl.lossless.player.desktop.IsMac
import cl.lossless.player.desktop.Lang
import cl.lossless.player.desktop.LibraryScanner
import cl.lossless.player.desktop.LocalLang
import cl.lossless.player.desktop.Player
import cl.lossless.player.desktop.S
import cl.lossless.player.desktop.Track
import cl.lossless.player.desktop.sortedForLibrary
import cl.lossless.player.desktop.t
import cl.lossless.player.desktop.toAlbums
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.File
import java.text.Normalizer
import javax.swing.JFileChooser

private enum class Tab { SONGS, ALBUMS, ARTISTS, FOLDERS, SETTINGS }

private fun String.searchKey(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

@Composable
fun App(player: Player, searchFocus: FocusRequester, onMiniPlayer: () -> Unit) {
    var language by remember { mutableStateOf(AppSettings.language) }
    CompositionLocalProvider(LocalLang provides (language ?: Lang.system())) {
        NeonTheme {
            Box(Modifier.fillMaxSize().background(Neon.Night)) {
                Library(player, searchFocus, language, onMiniPlayer) { language = it; AppSettings.language = it }
            }
        }
    }
}

@Composable
private fun Library(player: Player, searchFocus: FocusRequester, language: Lang?, onMiniPlayer: () -> Unit, onLanguage: (Lang?) -> Unit) {
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var scanning by remember { mutableStateOf<Int?>(null) }
    val folders = remember { mutableStateListOf<String>().apply { addAll(AppSettings.folders) } }
    var tab by remember { mutableStateOf(Tab.SONGS) }
    var query by remember { mutableStateOf("") }
    var openAlbum by remember { mutableStateOf<String?>(null) }
    var openArtist by remember { mutableStateOf<String?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var panelOpen by remember { mutableStateOf(true) }

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
    fun pickFolder(): String? {
        if (IsMac) {
            // En macOS el selector nativo (Finder) solo elige carpetas con esta propiedad.
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            val dialog = FileDialog(null as java.awt.Frame?, chooseTitle, FileDialog.LOAD)
            dialog.isVisible = true
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
            return dialog.file?.let { File(dialog.directory, it).path }
        }
        val chooser = JFileChooser().apply {
            dialogTitle = chooseTitle
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.path else null
    }

    fun addFolder() {
        val path = pickFolder()
        if (path != null) {
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

    // La búsqueda también encuentra formatos ("flac", "24/96") como en la maqueta.
    val searchIndex = remember(tracks) {
        tracks.map { "${it.title} ${it.artist} ${it.album} ${it.albumArtist} ${it.formatLabel}".searchKey() }
    }
    val filtered = remember(tracks, query) {
        val q = query.trim().searchKey()
        if (q.isEmpty()) tracks else tracks.filterIndexed { i, _ -> q in searchIndex[i] }
    }
    val various = t(S.variousArtists)
    val unknownArtist = t(S.unknownArtist)
    val albums = remember(filtered, various) { filtered.toAlbums(various) }
    val artists = remember(filtered, unknownArtist) { filtered.toArtists(unknownArtist) }
    val folderGroups = remember(filtered) {
        filtered.groupBy { it.folder }.toSortedMap(String.CASE_INSENSITIVE_ORDER).map { (path, list) -> path to list }
    }

    fun select(target: Tab) {
        tab = target
        openAlbum = null; openArtist = null; openFolder = null
    }

    Column(Modifier.fillMaxSize()) {
        TitleBar(
            query = query,
            onQuery = {
                query = it
                if (tab == Tab.SETTINGS) tab = Tab.SONGS
            },
            searchFocus = searchFocus,
            outputFormat = state.outputFormat,
        )
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(
                selected = tab,
                trackCount = filtered.size,
                onSelect = ::select,
            )

            Column(Modifier.weight(1f).fillMaxHeight().background(Neon.Night)) {
                scanning?.let { n ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Text(t(S.scanning, n), fontFamily = Mono, fontSize = 11.sp, color = Neon.Muted)
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth().padding(top = 6.dp).height(2.dp),
                            color = Neon.Accent,
                            trackColor = Neon.Border,
                        )
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
                        tab == Tab.SONGS -> TrackTable(
                            filtered,
                            currentPath = current,
                            playing = state.playing,
                            header = { SongsHeader(folders, filtered) { player.playQueue(filtered, 0) } },
                        ) { player.playQueue(filtered, it) }
                        tab == Tab.ALBUMS -> {
                            val album = openAlbum?.let { key -> albums.firstOrNull { it.key == key } }
                            if (album != null) {
                                AlbumDetail(album, current, state.playing, onBack = { openAlbum = null }) { player.playQueue(album.tracks, it) }
                            } else {
                                AlbumGrid(albums) { openAlbum = it.key }
                            }
                        }
                        tab == Tab.ARTISTS -> {
                            val artist = openArtist?.let { name -> artists.firstOrNull { it.name == name } }
                            if (artist != null) {
                                ArtistDetail(artist, current, state.playing, onBack = { openArtist = null }) { player.playQueue(artist.tracks, it) }
                            } else {
                                ArtistGrid(artists) { openArtist = it.name }
                            }
                        }
                        else -> {
                            val folder = openFolder?.let { path -> folderGroups.firstOrNull { it.first == path } }
                            if (folder != null) {
                                val list = remember(folder) { folder.second.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }, { it.file.name.lowercase() })) }
                                FolderDetail(folder.first, list, current, state.playing, onBack = { openFolder = null }) { player.playQueue(list, it) }
                            } else {
                                FolderList(folderGroups) { openFolder = it }
                            }
                        }
                    }
                }
            }

            val track = state.current
            if (track != null && panelOpen) {
                RightPanel(
                    track = track,
                    state = state,
                    positionMs = position,
                    onSeek = player::seek,
                    onPlayQueueItem = { player.playQueue(state.queue, it) },
                    onClose = { panelOpen = false },
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                )
            }
        }
        PlayerBar(player, state, position, panelOpen = panelOpen && state.current != null, onMiniPlayer = onMiniPlayer) { panelOpen = !panelOpen }
    }
}

/** Barra superior: logo, versión, búsqueda global (Ctrl+F / ⌘F) y estado de la salida de audio. */
@Composable
private fun TitleBar(query: String, onQuery: (String) -> Unit, searchFocus: FocusRequester, outputFormat: String?) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(Neon.Night).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(228.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(AppIcon, contentDescription = null, modifier = Modifier.size(26.dp).glow(Neon.Accent, 8.dp, RoundedCornerShape(6.dp)))
            Spacer(Modifier.width(8.dp))
            Text("ResuenApp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Neon.Text, letterSpacing = 0.6.sp)
            Spacer(Modifier.width(8.dp))
            NeonBadge("v$APP_VERSION")
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            SearchField(query, onQuery, searchFocus, Modifier.widthIn(max = 460.dp).fillMaxWidth())
        }
        Row(Modifier.width(228.dp), horizontalArrangement = Arrangement.End) {
            if (outputFormat != null) {
                val shape = RoundedCornerShape(50)
                Row(
                    Modifier.clip(shape).background(Neon.Amber.copy(alpha = 0.10f)).border(1.dp, Neon.Amber.copy(alpha = 0.30f), shape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bolt, null, tint = Neon.Amber, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(outputFormat.uppercase(), color = Neon.Amber, fontFamily = Mono, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Border))
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, focus: FocusRequester, modifier: Modifier) {
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier.height(30.dp).clip(shape).background(Neon.Card.copy(alpha = 0.8f))
            .border(1.dp, if (focused) Neon.Accent.copy(alpha = 0.6f) else Neon.Border, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = Neon.Muted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) Text(t(S.searchHint), color = Neon.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = Neon.Text, fontSize = 12.sp, fontFamily = Appearance.font.family),
                cursorBrush = SolidColor(Neon.Accent),
                modifier = Modifier.fillMaxWidth().focusRequester(focus)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Rounded.Close, t(S.back), tint = Neon.Muted,
                modifier = Modifier.size(15.dp).clip(RoundedCornerShape(50)).clickable { onQuery("") },
            )
        } else {
            NeonBadge(if (IsMac) "⌘F" else "Ctrl+F", color = Neon.Faint)
        }
    }
}

/** Barra lateral izquierda: secciones de la biblioteca y Ajustes al pie. */
@Composable
private fun Sidebar(selected: Tab, trackCount: Int, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.width(240.dp).fillMaxHeight().background(Neon.Surface.copy(alpha = 0.75f)).padding(10.dp),
    ) {
        SectionLabel(t(S.library), Modifier.padding(start = 12.dp, bottom = 8.dp, top = 6.dp))
        NavItem(Icons.Rounded.MusicNote, t(S.songs), selected == Tab.SONGS, badge = "$trackCount") { onSelect(Tab.SONGS) }
        NavItem(Icons.Rounded.Album, t(S.albums), selected == Tab.ALBUMS) { onSelect(Tab.ALBUMS) }
        NavItem(Icons.Rounded.Mic, t(S.artists), selected == Tab.ARTISTS) { onSelect(Tab.ARTISTS) }
        NavItem(Icons.Rounded.Folder, t(S.folders), selected == Tab.FOLDERS) { onSelect(Tab.FOLDERS) }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Border))
        Spacer(Modifier.height(8.dp))
        NavItem(Icons.Rounded.Settings, t(S.settings), selected == Tab.SETTINGS) { onSelect(Tab.SETTINGS) }
    }
    Box(Modifier.width(1.dp).fillMaxHeight().background(Neon.Border))
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, badge: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = when {
        selected -> Neon.Accent
        hovered -> Neon.Text
        else -> Neon.Muted
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(shape)
            .background(
                when {
                    selected -> Neon.Accent.copy(alpha = 0.10f)
                    hovered -> Neon.Hover
                    else -> Color.Transparent
                },
            )
            .border(1.dp, if (selected) Neon.Accent.copy(alpha = 0.20f) else Color.Transparent, shape)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        if (badge != null) Text(badge, color = color.copy(alpha = 0.6f), fontFamily = Mono, fontSize = 10.sp)
    }
}
