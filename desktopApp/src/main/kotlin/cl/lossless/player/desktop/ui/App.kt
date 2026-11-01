package cl.lossless.player.desktop.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.ui.unit.DpOffset
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.zIndex
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.lossless.player.desktop.APP_VERSION
import cl.lossless.player.desktop.DesktopLyrics
import cl.lossless.player.desktop.DesktopPlaylists
import cl.lossless.player.desktop.DesktopUpdater
import cl.lossless.player.desktop.AppSettings
import cl.lossless.player.desktop.IsMac
import cl.lossless.player.desktop.Lang
import cl.lossless.player.desktop.LibraryScanner
import cl.lossless.player.desktop.LocalLang
import cl.lossless.player.desktop.Player
import cl.lossless.player.desktop.S
import cl.lossless.player.desktop.Track
import cl.lossless.player.desktop.albumKeysByPath
import cl.lossless.player.desktop.sortedForLibrary
import cl.lossless.player.desktop.t
import cl.lossless.player.desktop.toAlbums
import cl.lossless.player.update.AvailableUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.FileDialog
import java.io.File
import java.text.Normalizer
import javax.swing.JFileChooser

private enum class Tab { SONGS, ALBUMS, ARTISTS, FOLDERS, PLAYLISTS, SETTINGS }

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
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var panelOpen by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<TrackDialog?>(null) }
    val selection = remember { TrackSelection() }

    fun rescan() {
        if (scanning != null) return
        scanning = 0
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                LibraryScanner.scan(folders.toList(), tracks) { n -> scanning = n }
            }
            tracks = result.sortedForLibrary()
            selection.clear()
            scanning = null
        }
    }

    LaunchedEffect(Unit) {
        tracks = withContext(Dispatchers.IO) { LibraryScanner.loadCache() }.sortedForLibrary()
        if (folders.isNotEmpty()) rescan()
    }

    // Busca una versión nueva al abrir; si la hay, aparece el aviso en la barra superior.
    LaunchedEffect(Unit) { DesktopUpdater.check(quiet = true) }
    fun installUpdate(update: AvailableUpdate) {
        scope.launch { DesktopUpdater.install(update, player) }
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
        openAlbum = null; openArtist = null; openFolder = null; openPlaylist = null
        selection.clear()
    }

    val actions = remember {
        TrackActions(
            editTags = { dialog = TrackDialog.EditTags(it) },
            editSelection = {
                val chosen = tracks.filter { it.path in selection.paths }
                if (chosen.isNotEmpty()) dialog = TrackDialog.EditTags(chosen)
            },
            // La carátula es del álbum (aunque esté repartido en varias carpetas): se guarda en todas sus pistas.
            findCover = { t ->
                val keys = tracks.albumKeysByPath()
                dialog = TrackDialog.FindCover(tracks.filter { keys[it.path] == keys[t.path] }.ifEmpty { listOf(t) })
            },
            findLyrics = { dialog = TrackDialog.FindLyrics(it) },
            addToPlaylist = { if (it.isNotEmpty()) dialog = TrackDialog.AddToPlaylist(it) },
        )
    }

    fun onTagsSaved(updated: List<Track>) {
        val byPath = updated.associateBy { it.path }
        tracks = tracks.map { byPath[it.path] ?: it }.sortedForLibrary()
        player.replaceTracks(byPath)
        DesktopLyrics.forget(byPath.keys)
        selection.clear()
        // Si el álbum abierto cambió de nombre, se sigue mostrando con el nombre nuevo.
        if (openAlbum != null) {
            val keys = tracks.albumKeysByPath()
            if (openAlbum !in keys.values) openAlbum = updated.firstOrNull()?.let { keys[it.path] }
        }
        val snapshot = tracks
        scope.launch(Dispatchers.IO) { LibraryScanner.saveCache(snapshot) }
    }

    // Canciones de la lista abierta: las de la biblioteca, o leídas del disco si están fuera de ella.
    val playlist = openPlaylist?.let { DesktopPlaylists[it] }
    val libraryByPath = remember(tracks) { tracks.associateBy { it.path } }
    var outside by remember { mutableStateOf<Map<String, Track?>>(emptyMap()) }
    LaunchedEffect(playlist?.paths, libraryByPath) {
        val unknown = playlist?.paths.orEmpty().filter { it !in libraryByPath && it !in outside }.distinct()
        if (unknown.isNotEmpty()) {
            val read = withContext(Dispatchers.IO) {
                unknown.associateWith { path -> File(path).takeIf { it.isFile }?.let { LibraryScanner.readTrack(it.toPath()) } }
            }
            outside = outside + read
        }
    }
    val playlistTracks = remember(playlist, libraryByPath, outside) {
        playlist?.paths.orEmpty().map { path ->
            libraryByPath[path] ?: outside[path] ?: Track(path, File(path).nameWithoutExtension, "", "", "", 0, 0, 0, "", 0, 0, 2, 0)
        }
    }
    val playlistMissing = remember(playlist, libraryByPath, outside) {
        playlist?.paths.orEmpty().filter { libraryByPath[it] == null && outside[it] == null }.toSet()
    }

    val track = state.current
    val showPanel = track != null && panelOpen
    val density = LocalDensity.current

    @Composable
    fun Panel() {
        if (track == null) return
        val left = Appearance.panelLeft
        if (!left) PanelResizeHandle(density) { dx -> Appearance.resizePanel(Appearance.panelWidth - dx) }
        RightPanel(
            track = track,
            state = state,
            positionMs = position,
            onSeek = player::seek,
            onPlayQueueItem = { player.playQueue(state.queue, it) },
            onClose = { panelOpen = false },
            modifier = Modifier.width(Appearance.panelWidth.dp).fillMaxHeight(),
        )
        if (left) PanelResizeHandle(density) { dx -> Appearance.resizePanel(Appearance.panelWidth + dx) }
    }

    CompositionLocalProvider(LocalTrackActions provides actions, LocalTrackSelection provides selection) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TitleBar(
                    query = query,
                    onQuery = {
                        query = it
                        if (tab == Tab.SETTINGS) tab = Tab.SONGS
                    },
                    searchFocus = searchFocus,
                    outputFormat = state.outputFormat,
                    onUpdate = ::installUpdate,
                )
                // El menú lateral flota sobre el contenido cuando se abre al pasar el mouse;
                // en la fila solo se reserva el ancho del riel (o el menú entero si está fijado).
                Box(Modifier.weight(1f).fillMaxWidth()) {
                val railSlot by animateDpAsState(if (Appearance.sidebarCollapsed) SIDEBAR_RAIL else SIDEBAR_OPEN)
                Row(Modifier.fillMaxSize()) {
                    Spacer(Modifier.width(railSlot + 1.dp).fillMaxHeight())
                    if (showPanel && Appearance.panelLeft) Panel()

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
                                    onCheckUpdate = { scope.launch { DesktopUpdater.check() } },
                                    onInstallUpdate = ::installUpdate,
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
                                tab == Tab.PLAYLISTS -> {
                                    if (playlist != null) {
                                        PlaylistDetail(
                                            playlist, playlistTracks, playlistMissing, current, state.playing,
                                            onBack = { openPlaylist = null },
                                            onRename = { dialog = TrackDialog.PlaylistName(playlist.id, playlist.name) },
                                            onDelete = { dialog = TrackDialog.DeletePlaylist(playlist.id, playlist.name) },
                                        ) { i ->
                                            // Solo se encolan las que existen; -1 es "Reproducir todo".
                                            val playable = playlistTracks.filter { it.path !in playlistMissing }
                                            val start = if (i < 0) 0 else playlistTracks.take(i).count { it.path !in playlistMissing }
                                            if (playable.isNotEmpty()) player.playQueue(playable, start)
                                        }
                                    } else {
                                        PlaylistList(
                                            onNew = { dialog = TrackDialog.PlaylistName(null, "") },
                                            onOpen = { openPlaylist = it.id },
                                            onRename = { dialog = TrackDialog.PlaylistName(it.id, it.name) },
                                            onDelete = { dialog = TrackDialog.DeletePlaylist(it.id, it.name) },
                                        )
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

                    if (showPanel && !Appearance.panelLeft) Panel()
                }
                Sidebar(
                    selected = tab,
                    trackCount = filtered.size,
                    playlistCount = DesktopPlaylists.all.size,
                    onSelect = ::select,
                    modifier = Modifier.align(Alignment.TopStart).fillMaxHeight(),
                )
                }
                PlayerBar(
                    player, state, position,
                    panelOpen = panelOpen && state.current != null,
                    onMiniPlayer = onMiniPlayer,
                    onEqualizer = { dialog = TrackDialog.Equalizer },
                ) { panelOpen = !panelOpen }
            }
            dialog?.let { d -> TrackDialogHost(d, player, onDismiss = { dialog = null }, onUpdated = ::onTagsSaved) }
        }
    }
}

/**
 * Borde del panel de reproducción: una línea de 1 dp con una zona de 7 dp que se arrastra para
 * cambiar el ancho del panel ([onDrag] recibe el desplazamiento horizontal en dp).
 */
@Composable
private fun PanelResizeHandle(density: Density, onDrag: (Float) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    Box(
        Modifier.width(7.dp).fillMaxHeight()
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { px -> onDrag(with(density) { px.toDp().value }) },
                onDragStarted = { dragging = true },
                onDragStopped = { dragging = false; Appearance.savePanelWidth() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.width(if (hovered || dragging) 2.dp else 1.dp).fillMaxHeight()
                .background(if (hovered || dragging) Neon.Accent.copy(alpha = 0.7f) else Neon.Border),
        )
    }
}

/** Barra superior: logo, versión, búsqueda global (Ctrl+F / ⌘F) y estado de la salida de audio. */
@Composable
private fun TitleBar(
    query: String,
    onQuery: (String) -> Unit,
    searchFocus: FocusRequester,
    outputFormat: String?,
    onUpdate: (AvailableUpdate) -> Unit,
) {
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
        Row(Modifier.width(228.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            UpdatePill(onUpdate)
            if (outputFormat != null) {
                val shape = RoundedCornerShape(50)
                Row(
                    Modifier.clip(shape).background(Neon.Accent.copy(alpha = 0.10f)).border(1.dp, Neon.Accent.copy(alpha = 0.30f), shape)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Bolt, null, tint = Neon.Accent, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(outputFormat.uppercase(), color = Neon.Accent, fontFamily = Mono, fontSize = 10.sp, maxLines = 1)
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

private val SIDEBAR_RAIL = 76.dp
private val SIDEBAR_OPEN = 240.dp

/**
 * Menú lateral al estilo de Magnific: un riel angosto con ícono y nombre corto debajo que, al
 * dejar el mouse encima un instante, se despliega flotando sobre el contenido con los nombres
 * completos y los contadores, y se vuelve a recoger al salir. Con el botón de la chincheta
 * queda fijado abierto (y entonces sí empuja el contenido); con la flecha vuelve a ser riel.
 */
@Composable
private fun Sidebar(selected: Tab, trackCount: Int, playlistCount: Int, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val pinned = !Appearance.sidebarCollapsed
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var peek by remember { mutableStateOf(false) }
    // Pequeña pausa al entrar para que cruzar el riel con el mouse no lo abra sin querer.
    LaunchedEffect(hovered, pinned) {
        if (pinned) { peek = false; return@LaunchedEffect }
        if (hovered) { delay(180); peek = true } else { delay(120); peek = false }
    }
    val expanded = pinned || peek
    val floating = expanded && !pinned
    val width by animateDpAsState(if (expanded) SIDEBAR_OPEN else SIDEBAR_RAIL)
    val choose: (Tab) -> Unit = { tab -> onSelect(tab); peek = false }

    Row(modifier.zIndex(2f)) {
        Column(
            Modifier.width(width).fillMaxHeight()
                .then(if (floating) Modifier.shadow(24.dp, RectangleShape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
                .background(if (floating) Neon.Surface else Neon.Surface.copy(alpha = 0.75f))
                .hoverable(interaction)
                .padding(horizontal = if (expanded) 10.dp else 8.dp, vertical = 10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(32.dp).padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
            ) {
                if (expanded) {
                    SectionLabel(t(S.library), Modifier.padding(start = 12.dp).weight(1f), color = Neon.Accent)
                    if (pinned) {
                        SmallIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, t(S.collapseSidebar), onClick = Appearance::toggleSidebar)
                    } else {
                        SmallIconButton(Icons.Rounded.PushPin, t(S.pinSidebar), size = 15.dp, onClick = Appearance::toggleSidebar)
                    }
                } else {
                    Icon(Icons.Rounded.Menu, t(S.expandSidebar), tint = Neon.Faint, modifier = Modifier.size(18.dp))
                }
            }
            NavItem(Icons.Rounded.MusicNote, t(S.songs), selected == Tab.SONGS, expanded, badge = "$trackCount") { choose(Tab.SONGS) }
            NavItem(Icons.Rounded.Album, t(S.albums), selected == Tab.ALBUMS, expanded) { choose(Tab.ALBUMS) }
            NavItem(Icons.Rounded.Mic, t(S.artists), selected == Tab.ARTISTS, expanded) { choose(Tab.ARTISTS) }
            NavItem(Icons.Rounded.Folder, t(S.folders), selected == Tab.FOLDERS, expanded) { choose(Tab.FOLDERS) }
            NavItem(Icons.AutoMirrored.Rounded.QueueMusic, t(S.playlists), selected == Tab.PLAYLISTS, expanded, badge = "$playlistCount") { choose(Tab.PLAYLISTS) }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Border))
            Spacer(Modifier.height(8.dp))
            NavItem(Icons.Rounded.Settings, t(S.settings), selected == Tab.SETTINGS, expanded) { choose(Tab.SETTINGS) }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(if (floating) Neon.Accent.copy(alpha = 0.25f) else Neon.Border))
    }
}

/** Abierto: ícono, nombre y contador en una fila. Riel: ícono con el nombre en pequeño debajo. */
@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, expanded: Boolean, badge: String? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (expanded) 8.dp else 10.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = when {
        selected -> Neon.Accent
        hovered -> Neon.Text
        else -> Neon.Muted
    }
    val base = Modifier.fillMaxWidth().padding(vertical = if (expanded) 1.dp else 2.dp).clip(shape)
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
    // El ícono siempre lleva el color de acento; el texto se ilumina al elegirlo o pasar el mouse.
    val tint = if (selected || hovered) color else Neon.Accent.copy(alpha = 0.75f)
    if (expanded) {
        Row(base.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            if (badge != null) Text(badge, color = color.copy(alpha = 0.6f), fontFamily = Mono, fontSize = 10.sp, maxLines = 1, softWrap = false)
        }
    } else {
        Column(base.padding(horizontal = 2.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, color = color, fontSize = 9.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}
