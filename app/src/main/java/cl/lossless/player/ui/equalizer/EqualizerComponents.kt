package cl.lossless.player.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import cl.lossless.player.playback.eq.EqSpec
import cl.lossless.player.playback.eq.EqMath
import cl.lossless.player.playback.eq.EqualizerSettings
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** "+3 dB", "-2.5 dB", "0 dB". */
fun formatDb(db: Float): String {
    val text = if (db % 1f == 0f) abs(db).toInt().toString() else "%.1f".format(abs(db))
    return when {
        db > 0f -> "+$text dB"
        db < 0f -> "-$text dB"
        else -> "0 dB"
    }
}

/**
 * Slider vertical de una banda: el centro es 0 dB, el relleno va desde el centro hasta el valor.
 * Arrastrar o tocar fija el valor; doble toque vuelve a 0 dB. Vibra en cada dB entero.
 */
@Composable
fun VerticalBandSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    min: Float = EqSpec.BAND_MIN_DB,
    max: Float = EqSpec.BAND_MAX_DB,
    step: Float = EqSpec.BAND_STEP_DB,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    var heightPx by remember { mutableFloatStateOf(1f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thumbRadius = with(density) { 11.dp.toPx() }

    fun valueAt(y: Float): Float {
        val usable = (heightPx - 2 * thumbRadius).coerceAtLeast(1f)
        val fraction = 1f - ((y - thumbRadius) / usable).coerceIn(0f, 1f)
        val raw = min + fraction * (max - min)
        return ((raw / step).roundToInt() * step).coerceIn(min, max)
    }

    fun emit(v: Float) {
        if (v == currentValue) return
        if (v.roundToInt() != currentValue.roundToInt() || v == 0f) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        currentOnChange(v)
    }

    Canvas(
        modifier
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectVerticalDragGestures(
                    onDragStart = { emit(valueAt(it.y)) },
                ) { change, _ ->
                    change.consume()
                    emit(valueAt(change.position.y))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { emit(valueAt(it.y)) },
                    onDoubleTap = { emit(0f) },
                )
            }
            .semantics {
                contentDescription = label
                stateDescription = formatDb(value)
                progressBarRangeInfo = ProgressBarRangeInfo(value, min..max, ((max - min) / step).toInt() - 1)
                if (enabled) {
                    setProgress { target -> emit(((target / step).roundToInt() * step).coerceIn(min, max)); true }
                } else {
                    disabled()
                }
            }
    ) {
        val cx = size.width / 2
        val top = thumbRadius
        val bottom = size.height - thumbRadius
        fun yOf(v: Float) = bottom - (v - min) / (max - min) * (bottom - top)
        val zeroY = yOf(0f)
        val valueY = yOf(value)
        val active = if (enabled) colors.primary else colors.outline
        val trackWidth = 6.dp.toPx()

        drawLine(colors.surfaceContainerHighest, Offset(cx, top), Offset(cx, bottom), trackWidth, StrokeCap.Round)
        // Marca de 0 dB
        drawLine(colors.outline, Offset(cx - 10.dp.toPx(), zeroY), Offset(cx + 10.dp.toPx(), zeroY), 2.dp.toPx(), StrokeCap.Round)
        // Relleno desde 0 dB hasta el valor
        if (value != 0f) drawLine(active, Offset(cx, zeroY), Offset(cx, valueY), trackWidth, StrokeCap.Round)
        // Pulgar
        drawCircle(active, thumbRadius, Offset(cx, valueY))
        drawCircle(if (enabled) colors.onPrimary else colors.surface, thumbRadius * 0.4f, Offset(cx, valueY))
    }
}

/** Curva de respuesta total del ecualizador (20 Hz – 20 kHz, ±12 dB). */
@Composable
fun ResponseCurve(settings: EqualizerSettings, enabled: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val freqs = remember { DoubleArray(120) { i -> 20.0 * 10.0.pow(3.0 * i / 119) } }
    val response = remember(settings) { EqMath.responseDb(settings.copy(enabled = true), freqs) }
    Canvas(modifier) {
        val range = 15f
        fun yOf(db: Double) = (size.height / 2 - (db.toFloat().coerceIn(-range, range) / range) * (size.height / 2))
        fun xOf(f: Double) = ((log10(f) - log10(20.0)) / 3.0).toFloat() * size.width

        // Rejilla: 0 dB, ±6, ±12 y las frecuencias de las bandas
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        listOf(-12.0, -6.0, 6.0, 12.0).forEach { db ->
            drawLine(colors.outlineVariant, Offset(0f, yOf(db)), Offset(size.width, yOf(db)), 1f, pathEffect = dash)
        }
        EqSpec.FREQUENCIES.forEach { f ->
            drawLine(colors.outlineVariant, Offset(xOf(f.toDouble()), 0f), Offset(xOf(f.toDouble()), size.height), 1f, pathEffect = dash)
        }
        drawLine(colors.outline, Offset(0f, yOf(0.0)), Offset(size.width, yOf(0.0)), 1.5f)

        val line = Path()
        freqs.forEachIndexed { i, f ->
            val p = Offset(xOf(f), yOf(response[i]))
            if (i == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y)
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, yOf(0.0))
            lineTo(0f, yOf(0.0))
            close()
        }
        val color = if (enabled) colors.primary else colors.outline
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.05f))))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Slider horizontal con etiqueta y valor (preamp, graves, virtualizador). */
@Composable
fun LabeledSlider(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    steps: Int = 0,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                valueText,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        }
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            enabled = enabled,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}
