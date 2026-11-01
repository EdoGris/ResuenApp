package cl.lossless.player.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.lossless.player.desktop.DesktopEqualizer
import cl.lossless.player.desktop.S
import cl.lossless.player.desktop.Str
import cl.lossless.player.desktop.t
import cl.lossless.player.playback.eq.EqPreset
import cl.lossless.player.playback.eq.EqSpec

/** Nombre visible (traducido) de la presintonía; los datos vienen del módulo compartido. */
private fun EqPreset.label(): Str = when (id) {
    "flat" -> S.eqPresetFlat
    "rock" -> S.eqPresetRock
    "pop" -> S.eqPresetPop
    "jazz" -> S.eqPresetJazz
    "classical" -> S.eqPresetClassical
    "dance" -> S.eqPresetDance
    "hiphop" -> S.eqPresetHiphop
    "acoustic" -> S.eqPresetAcoustic
    "bass" -> S.eqPresetBass
    "vocal" -> S.eqPresetVocal
    else -> S.eqPresetCustom
}

/**
 * Ecualizador de 10 bandas: encendido, presintonías, preamp y una barra vertical por banda.
 * Se usa en el cuadro que abre la barra inferior y en Ajustes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EqualizerPanel(modifier: Modifier = Modifier) {
    val s by DesktopEqualizer.settings.collectAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = s.enabled,
                onCheckedChange = DesktopEqualizer::setEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Neon.OnAccent, checkedTrackColor = Neon.Accent,
                    uncheckedThumbColor = Neon.Muted, uncheckedTrackColor = Neon.Card, uncheckedBorderColor = Neon.Border,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (s.enabled) t(S.eqEnabled) else t(S.eqBypass),
                color = if (s.enabled) Neon.Accent else Neon.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            GhostButton(Icons.Rounded.RestartAlt, t(S.eqReset), DesktopEqualizer::reset)
        }

        // Con el ecualizador apagado se ve atenuado pero se puede seguir ajustando.
        Column(Modifier.alpha(if (s.enabled) 1f else 0.5f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopEqualizer.presets.forEach { preset ->
                    EqPill(t(preset.label()), selected = s.presetId == preset.id) { DesktopEqualizer.selectPreset(preset) }
                }
            }

            Row(Modifier.fillMaxWidth().height(190.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                BandColumn(
                    label = t(S.eqPreamp), value = s.preampDb,
                    min = EqSpec.PREAMP_MIN_DB, max = EqSpec.PREAMP_MAX_DB,
                    onChange = DesktopEqualizer::setPreamp,
                )
                Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 18.dp).background(Neon.Border))
                EqSpec.FREQUENCIES.forEachIndexed { i, freq ->
                    BandColumn(
                        label = EqSpec.label(freq), value = s.bands.getOrElse(i) { 0f },
                        min = EqSpec.BAND_MIN_DB, max = EqSpec.BAND_MAX_DB,
                        onChange = { DesktopEqualizer.setBand(i, it) },
                    )
                }
            }
            Text(t(S.eqBandsHint) + " · " + t(S.eqPreampHint), color = Neon.Faint, fontSize = 11.sp)
        }
        Text(if (s.isProcessing) t(S.eqNoteProcessing) else t(S.eqNoteBypass), color = Neon.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun EqPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        modifier = Modifier.clip(shape)
            .background(if (selected) Neon.Accent.copy(alpha = 0.15f) else Neon.Card)
            .border(1.dp, if (selected) Neon.Accent.copy(alpha = 0.5f) else Neon.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        color = if (selected) Neon.Accent else Neon.Text,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/** Valor en dB arriba, barra vertical y frecuencia abajo. */
@Composable
private fun BandColumn(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            (if (value > 0f) "+" else "") + (if (value % 1f == 0f) value.toInt().toString() else value.toString()),
            color = if (value != 0f) Neon.Accent else Neon.Muted, fontFamily = Mono, fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        VerticalSlider(value, min, max, onChange, Modifier.weight(1f).padding(vertical = 6.dp))
        Text(label, color = Neon.Text, fontFamily = Mono, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Barra vertical con la línea del 0 dB; arrastrar o hacer clic fija el valor, doble clic lo vuelve a 0. */
@Composable
private fun VerticalSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit, modifier: Modifier) {
    val change by rememberUpdatedState(onChange)
    fun valueAt(y: Float, height: Int): Float = max - (y / height).coerceIn(0f, 1f) * (max - min)
    BoxWithConstraints(
        modifier.width(28.dp)
            .pointerInput(min, max) {
                detectTapGestures(
                    onDoubleTap = { change(0f) },
                    onTap = { change(valueAt(it.y, size.height)) },
                )
            }
            .pointerInput(min, max) {
                detectVerticalDragGestures { pointer, _ ->
                    pointer.consume()
                    change(valueAt(pointer.position.y, size.height))
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val fraction = ((max - value) / (max - min)).coerceIn(0f, 1f)
        val zero = ((max - 0f) / (max - min)).coerceIn(0f, 1f)
        val thumb = 14.dp
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Neon.Border))
        // Tramo coloreado entre el 0 dB y el valor.
        val top = maxHeight * minOf(fraction, zero)
        val bottom = maxHeight * maxOf(fraction, zero)
        Box(Modifier.offset(y = top).width(4.dp).height(bottom - top).clip(RoundedCornerShape(2.dp)).background(Neon.Accent))
        Box(Modifier.offset(y = maxHeight * zero).width(12.dp).height(1.dp).background(Neon.Muted))
        Box(
            Modifier.offset(y = (maxHeight - thumb) * fraction).size(thumb)
                .glow(Neon.Accent, 8.dp, CircleShape).clip(CircleShape).background(Neon.Accent),
        )
    }
}
