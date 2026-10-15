package cl.lossless.player.ui.lyrics

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.lossless.player.lyrics.LyricLine
import cl.lossless.player.lyrics.Lyrics
import cl.lossless.player.lyrics.LyricsAligner
import cl.lossless.player.lyrics.LyricsRepository.AlignStage
import cl.lossless.player.sync.NetworkMode
import cl.lossless.player.ui.components.swipeToSkip
import cl.lossless.player.ui.player.PlayerUiState
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Tras desplazar a mano, el desplazamiento automático espera este tiempo. */
private const val MANUAL_SCROLL_PAUSE_MS = 4_000L

@Composable
fun LyricsView(
    state: LyricsUiState,
    player: PlayerUiState,
    onSeek: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onRetry: () -> Unit,
    onPlayFrom: (Long) -> Unit,
    onSaveSync: suspend (List<LyricLine>) -> Boolean,
    onAlignNow: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
    swipeToSkip: Boolean = false,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
) {
    var editing by rememberSaveable(state.trackKey) { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            // Al marcar tiempos tocando no se cambia de canción por un roce.
            .swipeToSkip(swipeToSkip && !editing, onNext = onNext, onPrevious = onPrevious),
        contentAlignment = Alignment.Center,
    ) {
        val lyrics = state.lyrics
        val onTapSync = { editing = true }
        when {
            state.loading -> CircularProgressIndicator()
            editing && lyrics != null -> TapSyncEditor(
                lines = lyrics.synced?.map { it.text }
                    ?: LyricsAligner.splitLines(lyrics.plain.orEmpty()).map { it.text },
                player = player,
                onPlayFrom = onPlayFrom,
                onSave = onSaveSync,
                onClose = { editing = false },
            )
            lyrics?.isSynced == true ->
                SyncedLyrics(lyrics, state, player, onSeek, onAdjustOffset, onResetOffset, onRetry, onTapSync, onAlignNow, onSyncNow)
            lyrics?.plain != null -> PlainLyrics(lyrics, state, player, onRetry, onTapSync, onAlignNow, onSyncNow)
            lyrics?.instrumental == true -> Message("Instrumental", "Esta pista no tiene letra.", null, null)
            state.notFound -> Message(
                title = if (state.offline) "Sin conexión" else "Letra no encontrada",
                body = when {
                    state.offline -> "No se pudo consultar LRCLIB. Revisa tu conexión."
                    state.onlineDisabled -> "No hay letra en el archivo. Activa la búsqueda en internet en " +
                        "Ajustes → Letras, o agrega un .lrc junto a la canción."
                    else -> "No hay letra en el archivo ni en LRCLIB. Puedes agregar un archivo .lrc " +
                        "con el mismo nombre que la canción, en su carpeta."
                },
                actionLabel = "Buscar de nuevo",
                onAction = onRetry,
            )
            else -> Message("Sin letra", "No hay ninguna pista en reproducción.", null, null)
        }
    }
}

/** Posición actual interpolada en cada fotograma a partir de la última lectura del reproductor. */
@Composable
private fun rememberLivePosition(player: PlayerUiState): Long {
    val position by produceState(player.positionMs, player.positionMs, player.positionUpdatedAt, player.isPlaying) {
        value = player.positionMs
        if (!player.isPlaying) return@produceState
        while (true) {
            withFrameMillis { }
            value = player.positionMs + (SystemClock.elapsedRealtime() - player.positionUpdatedAt)
        }
    }
    return position
}

@Composable
private fun SyncedLyrics(
    lyrics: Lyrics,
    state: LyricsUiState,
    player: PlayerUiState,
    onSeek: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onRetry: () -> Unit,
    onTapSync: () -> Unit,
    onAlignNow: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val lines = lyrics.synced.orEmpty()
    val position = rememberLivePosition(player)
    val lyricPosition = position - state.offsetMs
    val current = lyrics.lineIndexAt(lyricPosition)
    val listState = rememberLazyListState()
    var lastManualScroll by remember { mutableLongStateOf(0L) }
    val manualScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) lastManualScroll = SystemClock.elapsedRealtime()
                return Offset.Zero
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // Relleno de media altura: la línea "en la cima" de la lista queda centrada.
            val centerPadding = (maxHeight / 2 - 28.dp).coerceAtLeast(0.dp)
            LaunchedEffect(current) {
                val idle = SystemClock.elapsedRealtime() - lastManualScroll > MANUAL_SCROLL_PAUSE_MS
                if (current >= 0 && idle) listState.animateScrollToItem(current)
            }
            // Si se desplazó a mano, al terminar la pausa vuelve a la línea actual.
            LaunchedEffect(lastManualScroll) {
                if (lastManualScroll == 0L) return@LaunchedEffect
                delay(MANUAL_SCROLL_PAUSE_MS)
                val line = lyrics.lineIndexAt(player.positionMs - state.offsetMs)
                if (line >= 0) listState.animateScrollToItem(line)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(manualScroll),
                contentPadding = PaddingValues(vertical = centerPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(lines, key = { i, l -> "${l.timeMs}#$i" }) { i, line ->
                    LyricLineText(
                        line = line,
                        isCurrent = i == current,
                        isPast = i < current,
                        // Solo la línea actual sigue el reloj por fotograma (resaltado por palabra).
                        currentWord = if (i == current) line.wordIndexAt(lyricPosition) else -1,
                        onClick = { onSeek(line.timeMs + state.offsetMs) },
                    )
                }
            }
        }
        SyncButton(state, onSyncNow)
        LyricsFooter(state, onAdjustOffset, onResetOffset, onRetry, onTapSync, onAlignNow, synced = true)
    }
}

@Composable
private fun LyricLineText(line: LyricLine, isCurrent: Boolean, isPast: Boolean, currentWord: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val color by animateColorAsState(
        when {
            isCurrent -> colors.primary
            isPast -> colors.onSurface.copy(alpha = 0.35f)
            else -> colors.onSurface.copy(alpha = 0.6f)
        },
        label = "lyricColor",
    )
    val scale by animateFloatAsState(if (isCurrent) 1.06f else 1f, label = "lyricScale")
    val words = line.words
    // Con tiempos por palabra, la línea actual se va "llenando" como en karaoke.
    val text = if (isCurrent && words != null) {
        buildAnnotatedString {
            words.forEachIndexed { i, word ->
                if (i > 0) append(' ')
                val sung = i <= currentWord
                withStyle(SpanStyle(color = if (sung) colors.primary else colors.onSurface.copy(alpha = 0.6f))) {
                    append(word.text)
                }
            }
        }
    } else buildAnnotatedString { append(line.text) }
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
    )
}

/** Letra sin marcas de tiempo: desplazamiento proporcional al avance de la canción. */
@Composable
private fun PlainLyrics(
    lyrics: Lyrics,
    state: LyricsUiState,
    player: PlayerUiState,
    onRetry: () -> Unit,
    onTapSync: () -> Unit,
    onAlignNow: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val scroll = rememberScrollState()
    var lastManualScroll by remember { mutableLongStateOf(0L) }
    val manualScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) lastManualScroll = SystemClock.elapsedRealtime()
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(player.positionMs, player.durationMs) {
        val idle = SystemClock.elapsedRealtime() - lastManualScroll > MANUAL_SCROLL_PAUSE_MS
        if (!idle || player.durationMs <= 0 || scroll.maxValue <= 0) return@LaunchedEffect
        val target = (scroll.maxValue * player.positionMs / player.durationMs).toInt()
        if (abs(target - scroll.value) > 4) scroll.animateScrollTo(target)
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            lyrics.plain.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(manualScroll)
                .verticalScroll(scroll)
                .padding(vertical = 24.dp, horizontal = 8.dp),
        )
        SyncButton(state, onSyncNow)
        LyricsFooter(state, {}, {}, onRetry, onTapSync, onAlignNow, synced = false)
    }
}

/** "Sincronizar": letra sin tiempos (o solo estimados) → tiempos exactos del servidor o estimados. */
@Composable
private fun SyncButton(state: LyricsUiState, onSyncNow: () -> Unit) {
    if (!state.canSync) return
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        FilledTonalButton(onClick = onSyncNow, shape = RoundedCornerShape(50)) {
            Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    state.alignServerUrl.isBlank() -> "Sincronizar (estimado)"
                    state.alignment is AlignmentUi.Failed -> "Reintentar sincronización"
                    else -> "Sincronizar con la música"
                }
            )
        }
    }
}

@Composable
private fun LyricsFooter(
    state: LyricsUiState,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onRetry: () -> Unit,
    onTapSync: () -> Unit,
    onAlignNow: () -> Unit,
    synced: Boolean,
) {
    val lyrics = state.lyrics
    val alignment = state.alignment
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Fuente: ${lyrics?.source?.label.orEmpty()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                when {
                    alignment is AlignmentUi.Working -> when (alignment.stage) {
                        AlignStage.HASHING -> "Preparando el audio…"
                        AlignStage.UPLOADING -> "Subiendo el audio al servidor…"
                        AlignStage.QUEUED -> "En cola en el servidor…"
                        AlignStage.PROCESSING -> "Alineando en el servidor…"
                    } + if (lyrics?.estimated == true) " · por ahora, estimada" else ""
                    alignment is AlignmentUi.Queued ->
                        "En la cola de sincronización" + if (lyrics?.estimated == true) " · por ahora, estimada" else ""
                    alignment is AlignmentUi.WaitingNetwork -> "Sincronización exacta: esperando " + when (alignment.mode) {
                        NetworkMode.WIFI -> "Wi-Fi"
                        NetworkMode.MOBILE -> "datos móviles"
                        NetworkMode.ANY -> "conexión"
                    } + if (lyrics?.estimated == true) " · por ahora, estimada" else ""
                    alignment is AlignmentUi.Failed -> alignment.message
                    state.estimating -> "Estimando los tiempos…"
                    lyrics?.estimated == true -> "Sincronización estimada"
                    !synced -> "Sin sincronizar"
                    else -> ""
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    alignment is AlignmentUi.Failed -> MaterialTheme.colorScheme.error
                    alignment != null || lyrics?.estimated == true || state.estimating -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (alignment is AlignmentUi.WaitingNetwork || alignment is AlignmentUi.Failed) {
            IconButton(onClick = onAlignNow, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = "Sincronizar ahora con la red actual")
            }
        }
        IconButton(onClick = onTapSync, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.TouchApp, contentDescription = "Sincronizar tocando")
        }
        if (synced) {
            IconButton(onClick = { onAdjustOffset(-250) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Remove, contentDescription = "Letra antes")
            }
            Text(
                "%+.2f s".format(state.offsetMs / 1000f),
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = if (state.offsetMs != 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onResetOffset).padding(horizontal = 2.dp),
            )
            IconButton(onClick = { onAdjustOffset(250) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = "Letra después")
            }
        }
        IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Buscar de nuevo")
        }
    }
}

@Composable
private fun Message(title: String, body: String, actionLabel: String?, onAction: (() -> Unit)?) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction, shape = RoundedCornerShape(50)) { Text(actionLabel) }
        }
    }
}
