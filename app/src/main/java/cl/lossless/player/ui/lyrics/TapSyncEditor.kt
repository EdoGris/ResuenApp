package cl.lossless.player.ui.lyrics

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cl.lossless.player.lyrics.LyricLine
import cl.lossless.player.ui.player.PlayerUiState
import kotlinx.coroutines.launch

/** Tiempo de reacción típico entre oír la línea y tocar: se descuenta de cada marca. */
private const val REACTION_MS = 150L

/**
 * "Sincronizar tocando": mientras suena la canción, se toca Marcar justo cuando empieza cada
 * línea. Al terminar se guarda como LRC exacto.
 */
@Composable
fun TapSyncEditor(
    lines: List<String>,
    player: PlayerUiState,
    onPlayFrom: (Long) -> Unit,
    onSave: suspend (List<LyricLine>) -> Boolean,
    onClose: () -> Unit,
) {
    val marks = remember(lines) { mutableStateListOf<Long>() }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val currentPlayer by rememberUpdatedState(player)
    val next = marks.size
    val done = next >= lines.size

    // Posición en este instante (interpolada desde la última lectura del reproductor).
    fun livePosition(): Long {
        val p = currentPlayer
        return if (p.isPlaying) p.positionMs + (SystemClock.elapsedRealtime() - p.positionUpdatedAt) else p.positionMs
    }

    LaunchedEffect(next) { if (lines.isNotEmpty()) listState.animateScrollToItem((next - 2).coerceAtLeast(0)) }

    Column(Modifier.fillMaxSize()) {
        Text("Sincronizar tocando", style = MaterialTheme.typography.titleMedium)
        Text(
            "Reproduce desde el inicio y toca «Marcar» justo cuando empiece a cantarse cada línea.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(lines) { i, text ->
                val isNext = i == next
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        marks.getOrNull(i)?.let(::formatMark) ?: "--:--.--",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        color = if (i < next) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isNext -> MaterialTheme.colorScheme.onPrimaryContainer
                            i < next -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                if (done) {
                    saving = true
                    scope.launch {
                        val toFolder = onSave(lines.mapIndexed { i, t -> LyricLine(marks[i], t) })
                        Toast.makeText(
                            context,
                            if (toFolder) "Guardado como .lrc junto a la canción" else "Sincronización guardada en la app",
                            Toast.LENGTH_SHORT,
                        ).show()
                        saving = false
                        onClose()
                    }
                } else {
                    val previous = marks.lastOrNull() ?: -1L
                    marks += (livePosition() - REACTION_MS).coerceAtLeast(previous + 100).coerceAtLeast(0)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            enabled = !saving && lines.isNotEmpty() && (done || player.isPlaying),
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Rounded.TouchApp, null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    done -> "Guardar"
                    !player.isPlaying -> "Reproduce para marcar"
                    else -> "Marcar línea ${next + 1} de ${lines.size}"
                },
                maxLines = 1,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { marks.clear(); onPlayFrom(0) }) {
                Icon(Icons.Rounded.Replay, null)
                Spacer(Modifier.width(6.dp))
                Text("Desde el inicio")
            }
            TextButton(onClick = { if (marks.isNotEmpty()) marks.removeAt(marks.lastIndex) }, enabled = marks.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Rounded.Undo, null)
                Spacer(Modifier.width(4.dp))
                Text("Deshacer")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Cancelar") }
        }
    }
}

private fun formatMark(ms: Long): String =
    "%02d:%02d.%02d".format(ms / 60_000, (ms % 60_000) / 1_000, (ms % 1_000) / 10)
