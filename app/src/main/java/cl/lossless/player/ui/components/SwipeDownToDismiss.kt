package cl.lossless.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Deslizar hacia abajo para cerrar (minimizar) una pantalla completa. La pantalla sigue al dedo
 * y, al soltar, se cierra si bajó lo suficiente o se lanzó con fuerza; si no, vuelve a su sitio.
 *
 * También recoge el arrastre que sobra de listas internas (la letra): si la lista ya está arriba
 * y se sigue tirando hacia abajo, baja la pantalla.
 */
@Composable
fun Modifier.swipeDownToDismiss(onDismiss: () -> Unit): Modifier {
    val density = LocalDensity.current
    val distanceThreshold = with(density) { 140.dp.toPx() }
    val velocityThreshold = with(density) { 1200.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)

    fun release(velocityY: Float) {
        if (offset.value > distanceThreshold || (offset.value > 0f && velocityY > velocityThreshold)) {
            dismiss()
        } else {
            scope.launch { offset.animateTo(0f) }
        }
    }

    val nested = remember {
        object : NestedScrollConnection {
            // Si la pantalla ya está bajada y el dedo sube, primero se recoge la pantalla.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f || offset.value <= 0f) return Offset.Zero
                val used = maxOf(available.y, -offset.value)
                scope.launch { offset.snapTo(offset.value + used) }
                return Offset(0f, used)
            }

            // Lo que la lista no pudo desplazar hacia abajo (ya estaba arriba) baja la pantalla.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                scope.launch { offset.snapTo(offset.value + available.y) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value <= 0f) return Velocity.Zero
                release(available.y)
                return available
            }
        }
    }

    // El gesto va antes del desplazamiento visual para medir en coordenadas fijas, no en las de
    // la pantalla que se mueve con el dedo.
    return this
        .nestedScroll(nested)
        .pointerInput(Unit) {
            val tracker = VelocityTracker()
            detectVerticalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onDragEnd = { release(tracker.calculateVelocity().y) },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, dragAmount ->
                change.consume()
                tracker.addPosition(change.uptimeMillis, change.position)
                scope.launch { offset.snapTo((offset.value + dragAmount).coerceAtLeast(0f)) }
            }
        }
        .graphicsLayer { translationY = offset.value }
}
