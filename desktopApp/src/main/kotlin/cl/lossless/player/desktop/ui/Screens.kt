package cl.lossless.player.desktop.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.lossless.player.desktop.APP_VERSION
import cl.lossless.player.desktop.Album
import cl.lossless.player.desktop.Covers
import cl.lossless.player.desktop.Lang
import cl.lossless.player.desktop.S
import cl.lossless.player.desktop.Track
import cl.lossless.player.desktop.khz
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
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Duración total de una lista, "12 h 04 min" o "48 min". */
private fun formatTotal(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "%d h %02d min".format(minutes / 60, minutes % 60) else "$minutes min"
}

/** Más de 16 bits o más de 48 kHz: se resalta en cian como en la maqueta. */
val Track.isHiRes: Boolean get() = bitsPerSample > 16 || sampleRate > 48_000

val Track.codecLabel: String get() = codec.ifBlank { file.extension.uppercase() }

/** "24-bit / 96 kHz", o null si el archivo no lo informa. */
val Track.specsLabel: String?
    get() = when {
        bitsPerSample > 0 && sampleRate > 0 -> "$bitsPerSample-bit / ${khz(sampleRate)} kHz"
        sampleRate > 0 -> "${khz(sampleRate)} kHz"
        else -> null
    }

@Composable
fun CoverImage(track: Track?, size: Dp, px: Int, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val key = track?.albumKey
    val version = Covers.version
    var image by remember(key, px, version) { mutableStateOf<ImageBitmap?>(key?.let { Covers.cached(it, px) }) }
    LaunchedEffect(key, px, version) {
        if (track != null && image == null && !Covers.isCached(track.albumKey, px)) {
            image = withContext(coverDispatcher) { Covers.load(track, px) }
        }
    }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier.size(size).clip(shape).background(Neon.Card).border(1.dp, Neon.Border, shape),
        contentAlignment = Alignment.Center,
    ) {
        val img = image
        if (img != null) {
            Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Rounded.MusicNote, null, tint = Neon.Faint, modifier = Modifier.size(size / 2.5f))
        }
    }
}

/** Tres barras que suben y bajan junto a la pista que suena. */
@Composable
fun PlayingBars(active: Boolean, color: Color = Neon.Accent) {
    val transition = rememberInfiniteTransition()
    val periods = listOf(800, 1100, 700)
    Row(Modifier.height(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        periods.forEachIndexed { i, period ->
            val h by transition.animateFloat(
                initialValue = 4f,
                targetValue = 16f,
                animationSpec = infiniteRepeatable(tween(period / 2, delayMillis = i * 100), RepeatMode.Reverse),
            )
            Box(Modifier.width(3.dp).height(if (active) h.dp else 6.dp).background(color))
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
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(Neon.Accent.copy(alpha = 0.10f))
                .border(1.dp, Neon.Accent.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.LibraryMusic, null, tint = Neon.Accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(t(S.emptyTitle), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Neon.TextStrong)
        Spacer(Modifier.height(8.dp))
        Text(t(S.emptyBody), color = Neon.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(Icons.Rounded.Add, t(S.addFolder), onAdd)
    }
}

/** Botón cian con resplandor ("Reproducir todo"). */
@Composable
fun PrimaryButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.glow(Neon.Accent, 12.dp, shape).clip(shape).background(Neon.Accent).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Neon.OnAccent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Neon.OnAccent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

/** Botón secundario con borde. */
@Composable
fun GhostButton(icon: ImageVector?, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.clip(shape).background(if (hovered) Neon.Hover else Neon.Card).border(1.dp, Neon.Border, shape)
            .hoverable(interaction).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Neon.Text, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = Neon.Text, fontSize = 12.sp)
    }
}

/** Cabecera de una vista: ruta monoespaciada en cian, título grande, resumen y acción. */
@Composable
private fun ViewHeader(
    title: String,
    eyebrow: String? = null,
    eyebrowIcon: ImageVector = Icons.Rounded.Storage,
    summary: String? = null,
    onBack: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(S.back), tint = Neon.Muted) }
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Icon(eyebrowIcon, null, tint = Neon.Accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        eyebrow.uppercase(), color = Neon.Accent, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.5.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(title, color = Neon.TextStrong, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (summary != null) Text(summary, color = Neon.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (action != null) action()
    }
}

@Composable
private fun summaryOf(tracks: List<Track>): String =
    t(S.librarySummary, tracks.size, formatTotal(tracks.sumOf { it.durationMs }), tracks.count { it.isHiRes })

@Composable
fun SongsHeader(folders: List<String>, tracks: List<Track>, onPlayAll: () -> Unit) {
    ViewHeader(
        title = t(S.allTracks),
        eyebrow = folders.singleOrNull() ?: t(S.folderCount, folders.size),
        summary = summaryOf(tracks),
        action = { if (tracks.isNotEmpty()) PrimaryButton(Icons.Rounded.PlayArrow, t(S.playAll), onPlayAll) },
    )
}

private val ColNumber = 48.dp
private val ColFormat = 150.dp
private val ColDuration = 64.dp

/** Tabla de pistas en un panel translúcido: #, título con carátula, artista, álbum, formato y duración. */
@Composable
fun TrackTable(
    tracks: List<Track>,
    currentPath: String?,
    playing: Boolean,
    showAlbum: Boolean = true,
    useTrackNumbers: Boolean = false,
    header: (@Composable () -> Unit)? = null,
    onPlay: (Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        if (header != null) item { header() }
        if (tracks.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    Text(t(S.noResults), color = Neon.Muted)
                }
            }
            return@LazyColumn
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Neon.Surface)
                    .border(1.dp, Neon.Border, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderCell("#", Modifier.width(ColNumber), TextAlign.Center)
                HeaderCell(t(S.colTitle), Modifier.weight(1.6f))
                HeaderCell(t(S.colArtist), Modifier.weight(1f))
                if (showAlbum) HeaderCell(t(S.colAlbum), Modifier.weight(1f))
                HeaderCell(t(S.colFormat), Modifier.width(ColFormat), TextAlign.Center)
                HeaderCell(t(S.colDuration), Modifier.width(ColDuration), TextAlign.End)
            }
        }
        itemsIndexed(tracks, key = { _, it -> it.path }) { i, track ->
            val number = if (useTrackNumbers && track.trackNumber > 0) track.trackNumber else i + 1
            TrackContextMenu(track) {
                TrackRow(
                    track,
                    number = number,
                    current = track.path == currentPath,
                    playing = playing,
                    showAlbum = showAlbum,
                    last = i == tracks.lastIndex,
                ) { onPlay(i) }
            }
        }
    }
}

/** Clic derecho sobre una pista: editar etiquetas, buscar carátula o letra. */
@Composable
private fun TrackContextMenu(track: Track, content: @Composable () -> Unit) {
    val actions = LocalTrackActions.current ?: return content()
    val edit = t(S.editTags)
    val cover = t(S.searchCover)
    val lyrics = t(S.searchLyrics)
    ContextMenuArea(
        items = {
            listOf(
                ContextMenuItem(edit) { actions.editTags(listOf(track)) },
                ContextMenuItem(cover) { actions.findCover(track) },
                ContextMenuItem(lyrics) { actions.findLyrics(track) },
            )
        },
        content = content,
    )
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.Start) {
    Text(
        text.uppercase(), modifier = modifier.padding(horizontal = 6.dp), textAlign = align,
        color = Neon.Muted, fontFamily = Mono, fontSize = 11.sp, maxLines = 1,
    )
}

@Composable
private fun TrackRow(track: Track, number: Int, current: Boolean, playing: Boolean, showAlbum: Boolean, last: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = if (last) RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp) else RoundedCornerShape(0.dp)
    Box(
        Modifier.fillMaxWidth().clip(shape)
            .background(Neon.Surface.copy(alpha = 0.75f))
            .border(1.dp, Neon.Border.copy(alpha = 0.6f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(
                    when {
                        current -> Neon.Accent.copy(alpha = 0.10f)
                        hovered -> Neon.Hover.copy(alpha = 0.8f)
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(ColNumber), contentAlignment = Alignment.Center) {
                if (current) {
                    PlayingBars(active = playing)
                } else {
                    Text(
                        "%02d".format(number), fontFamily = Mono, fontSize = 12.sp,
                        color = if (hovered) Neon.Accent else Neon.Faint,
                    )
                }
            }
            Row(Modifier.weight(1.6f).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(track, size = 32.dp, px = 64, corner = 4.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = if (current || hovered) Neon.Accent else Neon.Text,
                )
            }
            Text(
                track.artist.ifBlank { t(S.unknownArtist) }, Modifier.weight(1f).padding(horizontal = 6.dp),
                color = Neon.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (showAlbum) {
                Text(
                    track.album.ifBlank { track.folderName }, Modifier.weight(1f).padding(horizontal = 6.dp),
                    color = Neon.Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.width(ColFormat), contentAlignment = Alignment.Center) {
                NeonBadge(track.formatLabel, color = if (track.isHiRes) Neon.Accent else Neon.Muted, emphasized = track.isHiRes)
            }
            Text(
                formatTime(track.durationMs), Modifier.width(ColDuration).padding(horizontal = 6.dp), textAlign = TextAlign.End,
                color = Neon.Muted, fontFamily = Mono, fontSize = 12.sp,
            )
        }
        if (current) Box(Modifier.width(2.dp).height(48.dp).background(Neon.Accent))
    }
}

/** Tarjeta translúcida que se ilumina en cian al pasar el mouse. */
@Composable
private fun GlassCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.clip(shape).background(Neon.Card.copy(alpha = 0.6f))
            .border(1.dp, if (hovered) Neon.Accent.copy(alpha = 0.4f) else Neon.Border, shape)
            .hoverable(interaction).clickable(onClick = onClick)
            .padding(12.dp),
    ) { content() }
}

@Composable
fun AlbumGrid(albums: List<Album>, onOpen: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(180.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            ViewHeader(title = t(S.albums), summary = t(S.albumCount, albums.size))
        }
        if (albums.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(t(S.noResults), color = Neon.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(48.dp))
            }
        }
        items(albums, key = { it.key }) { album ->
            GlassCard(onClick = { onOpen(album) }) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        CoverImage(album.tracks.first(), size = 220.dp, px = 360, modifier = Modifier.fillMaxSize(), corner = 8.dp)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        album.title.ifBlank { t(S.unknownAlbum) }, color = Neon.TextStrong, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        album.artist.ifBlank { t(S.unknownArtist) }, color = Neon.Muted, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumDetail(album: Album, currentPath: String?, playing: Boolean, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    TrackTable(album.tracks, currentPath, playing, showAlbum = false, useTrackNumbers = true, header = {
        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, t(S.back), tint = Neon.Muted) }
            Spacer(Modifier.width(8.dp))
            CoverImage(album.tracks.first(), size = 160.dp, px = 360, corner = 12.dp, modifier = Modifier.glow(Neon.Accent, 18.dp, RoundedCornerShape(12.dp)))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                SectionLabel(album.tracks.first().formatLabel, color = Neon.Accent)
                Spacer(Modifier.height(4.dp))
                Text(album.title.ifBlank { t(S.unknownAlbum) }, color = Neon.TextStrong, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(album.artist.ifBlank { t(S.unknownArtist) }, color = Neon.Muted, fontSize = 13.sp)
                Text(summaryOf(album.tracks), color = Neon.Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(Icons.Rounded.PlayArrow, t(S.playAll)) { onPlay(0) }
                    LocalTrackActions.current?.let { actions ->
                        GhostButton(Icons.Rounded.Edit, t(S.editAlbum)) { actions.editTags(album.tracks) }
                        GhostButton(Icons.Rounded.AddPhotoAlternate, t(S.searchCover).removeSuffix("…")) { actions.findCover(album.tracks.first()) }
                    }
                }
            }
        }
    }, onPlay = onPlay)
}

/** Un artista (el del álbum si existe) con todas sus pistas. */
data class Artist(val name: String, val tracks: List<Track>, val albumCount: Int)

fun List<Track>.toArtists(unknown: String): List<Artist> =
    groupBy { it.albumArtist.ifBlank { it.artist }.trim().ifBlank { unknown } }
        .map { (name, list) ->
            val sorted = list.sortedWith(compareBy({ it.album.lowercase() }, { it.discNumber }, { it.trackNumber }, { it.title.lowercase() }))
            Artist(name, sorted, list.map { it.albumKey }.distinct().size)
        }
        .sortedBy { it.name.lowercase() }

@Composable
fun ArtistGrid(artists: List<Artist>, onOpen: (Artist) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            ViewHeader(title = t(S.artists), summary = "${artists.size}")
        }
        items(artists, key = { it.name }) { artist ->
            GlassCard(onClick = { onOpen(artist) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Neon.Accent.copy(alpha = 0.10f))
                            .border(1.dp, Neon.Accent.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(artist.name.take(1).uppercase(), color = Neon.Accent, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(artist.name, color = Neon.TextStrong, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${t(S.albumCount, artist.albumCount)} · ${t(S.trackCount, artist.tracks.size)}",
                            color = Neon.Muted, fontSize = 11.sp, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistDetail(artist: Artist, currentPath: String?, playing: Boolean, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    TrackTable(artist.tracks, currentPath, playing, header = {
        ViewHeader(
            title = artist.name,
            summary = summaryOf(artist.tracks),
            onBack = onBack,
            action = { PrimaryButton(Icons.Rounded.PlayArrow, t(S.playAll)) { onPlay(0) } },
        )
    }, onPlay = onPlay)
}

@Composable
fun FolderList(folders: List<Pair<String, List<Track>>>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        item { ViewHeader(title = t(S.folders), summary = t(S.folderCount, folders.size)) }
        if (folders.isEmpty()) item { Text(t(S.noResults), color = Neon.Muted, modifier = Modifier.padding(24.dp)) }
        items(folders, key = { it.first }) { (path, list) ->
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (hovered) Neon.Hover.copy(alpha = 0.6f) else Color.Transparent)
                    .hoverable(interaction).clickable { onOpen(path) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Folder, null, tint = Neon.Amber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        File(path).name, color = if (hovered) Neon.TextStrong else Neon.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(path, color = Neon.Faint, fontFamily = Mono, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(t(S.trackCount, list.size), color = Neon.Muted, fontFamily = Mono, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun FolderDetail(path: String, tracks: List<Track>, currentPath: String?, playing: Boolean, onBack: () -> Unit, onPlay: (Int) -> Unit) {
    TrackTable(tracks, currentPath, playing, header = {
        ViewHeader(
            title = File(path).name,
            eyebrow = path,
            eyebrowIcon = Icons.Rounded.Folder,
            summary = summaryOf(tracks),
            onBack = onBack,
            action = { PrimaryButton(Icons.Rounded.PlayArrow, t(S.playAll)) { onPlay(0) } },
        )
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ViewHeader(title = t(S.settings))

        SettingsCard(t(S.libraryFolders)) {
            folders.forEach { path ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Folder, null, tint = Neon.Amber, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(path, Modifier.weight(1f), color = Neon.Text, fontFamily = Mono, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemove(path) }) { Icon(Icons.Rounded.Delete, t(S.removeFolder), tint = Neon.Muted) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(Icons.Rounded.Add, t(S.addFolder), onAdd)
                if (folders.isNotEmpty()) GhostButton(Icons.Rounded.Refresh, t(S.rescan), onRescan)
            }
        }

        SettingsCard(t(S.language)) {
            LanguageOption(t(S.languageSystem), selected = language == null) { onLanguage(null) }
            Lang.entries.forEach { lang ->
                LanguageOption(lang.nativeName, selected = language == lang) { onLanguage(lang) }
            }
        }

        AppearanceCard()

        SettingsCard(t(S.output)) {
            Text(t(S.outputShared), color = Neon.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (outputFormat != null) Text(outputFormat, color = Neon.Teal, fontFamily = Mono, fontSize = 12.sp)
            Text(t(S.outputNote), color = Neon.Muted, fontSize = 12.sp)
        }

        SettingsCard(t(S.about)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(AppIcon, null, Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Text("ResuenApp · ${t(S.version, APP_VERSION)}", color = Neon.Text, fontSize = 13.sp)
            }
        }
    }
}

/** Tema, color de acento, tipo de letra y tamaño del texto, como en Ajustes de Android. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard() {
    SettingsCard(t(S.appearance)) {
        SubLabel(t(S.theme))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                Pill(t(mode.label), selected = Appearance.theme == mode) { Appearance.set(mode) }
            }
        }

        SubLabel(t(S.accentColor, t(Appearance.accent.label)))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Accent.entries.forEach { accent ->
                val selected = Appearance.accent == accent
                val color = accent.color(Appearance.palette.dark)
                Box(
                    Modifier.size(32.dp)
                        .then(if (selected) Modifier.glow(color, 10.dp, CircleShape) else Modifier)
                        .clip(CircleShape).background(color)
                        .border(if (selected) 2.dp else 0.dp, if (selected) Neon.TextStrong else Color.Transparent, CircleShape)
                        .clickable { Appearance.set(accent) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) Icon(Icons.Rounded.Check, t(accent.label), tint = Palette.of(Appearance.theme, accent).onAccent, modifier = Modifier.size(18.dp))
                }
            }
        }

        SubLabel(t(S.font, Appearance.font.brandName ?: t(S.fontSystem)))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppFont.entries.forEach { font ->
                Pill(font.brandName ?: t(S.fontSystem), selected = Appearance.font == font, fontFamily = font.family) { Appearance.set(font) }
            }
        }

        SubLabel(t(S.textSize))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextSize.entries.forEach { size ->
                Pill(t(size.label), selected = Appearance.textSize == size) { Appearance.set(size) }
            }
        }

        SubLabel(t(S.panelSide))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(t(S.sideRight), selected = !Appearance.panelLeft) { Appearance.setPanelLeft(false) }
            Pill(t(S.sideLeft), selected = Appearance.panelLeft) { Appearance.setPanelLeft(true) }
        }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(text, color = Neon.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Pill(label: String, selected: Boolean, fontFamily: FontFamily? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        modifier = Modifier.clip(shape)
            .background(if (selected) Neon.Accent.copy(alpha = 0.15f) else Neon.Card)
            .border(1.dp, if (selected) Neon.Accent.copy(alpha = 0.5f) else Neon.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        color = if (selected) Neon.Accent else Neon.Text,
        fontSize = 12.sp,
        fontFamily = fontFamily,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 760.dp).fillMaxWidth()
            .glass(RoundedCornerShape(12.dp), Neon.Card.copy(alpha = 0.6f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionLabel(title, color = Neon.Accent)
        Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Border))
        content()
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Neon.Accent, unselectedColor = Neon.Faint),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = if (selected) Neon.TextStrong else Neon.Text, fontSize = 13.sp)
    }
}
