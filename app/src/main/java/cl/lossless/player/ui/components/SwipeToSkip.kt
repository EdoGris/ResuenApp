package cl.lossless.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Deslizar en horizontal para cambiar de canción: de derecha a izquierda avanza, de
 * izquierda a derecha retrocede. El elemento sigue al dedo y vuelve a su lugar al soltar.
 */
@Composable
fun Modifier.swipeToSkip(enabled: Boolean, onNext: () -> Unit, onPrevious: () -> Unit): Modifier {
    if (!enabled) return this
    val threshold = with(LocalDensity.current) { 64.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    return this
        .graphicsLayer {
            translationX = offset.value
            alpha = 1f - (abs(offset.value) / size.width.coerceAtLeast(1f)).coerceAtMost(1f) * 0.5f
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    when {
                        offset.value <= -threshold -> next()
                        offset.value >= threshold -> previous()
                    }
                    scope.launch { offset.animateTo(0f) }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, dragAmount ->
                change.consume()
                // Algo de resistencia: el elemento se mueve menos que el dedo.
                scope.launch { offset.snapTo(offset.value + dragAmount * 0.6f) }
            }
        }
}
