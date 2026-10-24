package cl.lossless.player.ui.equalizer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.lossless.player.R
import cl.lossless.player.playback.eq.EqPreset
import cl.lossless.player.playback.eq.nameRes
import cl.lossless.player.playback.eq.EqSpec
import cl.lossless.player.playback.eq.EqualizerSettings

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    bitPerfectOutput: Boolean,
    vm: EqualizerViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    EqualizerContent(
        settings = settings,
        presets = vm.presets,
        bitPerfectOutput = bitPerfectOutput,
        onBack = onBack,
        onEnabled = vm::setEnabled,
        onPreset = vm::selectPreset,
        onBand = vm::setBand,
        onPreamp = vm::setPreamp,
        onBassBoost = vm::setBassBoost,
        onVirtualizer = vm::setVirtualizer,
        onReset = vm::reset,
    )
}

/** Contenido sin ViewModel (reutilizable y previsualizable). */
@Composable
fun EqualizerContent(
    settings: EqualizerSettings,
    presets: List<EqPreset>,
    bitPerfectOutput: Boolean,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onPreset: (EqPreset) -> Unit,
    onBand: (Int, Float) -> Unit,
    onPreamp: (Float) -> Unit,
    onBassBoost: (Float) -> Unit,
    onVirtualizer: (Float) -> Unit,
    onReset: () -> Unit,
) {
    val enabled = settings.enabled
    val dimmed = Modifier.alpha(if (enabled) 1f else 0.45f)

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Barra superior con el interruptor principal
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.player_eq_back)) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.player_equalizer), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(if (enabled) R.string.player_eq_enabled else R.string.player_eq_disabled_bypass),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = onEnabled)
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            ) {
                PresetCarousel(presets, settings.presetId, enabled, onPreset, dimmed)

                ResponseCurve(
                    settings = settings,
                    enabled = enabled,
                    modifier = dimmed.fillMaxWidth().height(110.dp).padding(horizontal = 20.dp, vertical = 8.dp),
                )

                BandSliders(settings.bands, enabled, onBand, dimmed.padding(horizontal = 8.dp))

                Card(
                    modifier = dimmed.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LabeledSlider(
                            title = stringResource(R.string.player_eq_preamp),
                            valueText = formatDb(settings.preampDb),
                            supporting = stringResource(R.string.player_eq_preamp_hint),
                            value = settings.preampDb,
                            onValueChange = onPreamp,
                            range = EqSpec.PREAMP_MIN_DB..EqSpec.PREAMP_MAX_DB,
                            enabled = enabled,
                        )
                        LabeledSlider(
                            title = stringResource(R.string.player_eq_bass_boost),
                            valueText = stringResource(R.string.player_eq_percent, (settings.bassBoost * 100).toInt()),
                            value = settings.bassBoost,
                            onValueChange = onBassBoost,
                            range = 0f..1f,
                            enabled = enabled,
                        )
                        LabeledSlider(
                            title = stringResource(R.string.player_eq_virtualizer),
                            valueText = stringResource(R.string.player_eq_percent, (settings.virtualizer * 100).toInt()),
                            value = settings.virtualizer,
                            onValueChange = onVirtualizer,
                            range = 0f..1f,
                            enabled = enabled,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onReset,
                    enabled = enabled,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Rounded.RestartAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_eq_reset))
                }

                BitPerfectNote(settings, bitPerfectOutput)
            }
        }
    }
}

@Composable
private fun PresetCarousel(
    presets: List<EqPreset>,
    selectedId: String,
    enabled: Boolean,
    onSelect: (EqPreset) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    // Mantiene visible el preset seleccionado (p. ej. al pasar a "Personalizado").
    LaunchedEffect(selectedId) {
        val index = presets.indexOfFirst { it.id == selectedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(presets, key = { it.id }) { preset ->
            FilterChip(
                selected = preset.id == selectedId,
                onClick = { onSelect(preset) },
                enabled = enabled,
                label = { Text(stringResource(preset.nameRes)) },
            )
        }
    }
}

@Composable
private fun BandSliders(
    bands: List<Float>,
    enabled: Boolean,
    onBand: (Int, Float) -> Unit,
    modifier: Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        EqSpec.FREQUENCIES.forEachIndexed { i, freq ->
            val value = bands.getOrElse(i) { 0f }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatDb(value).removeSuffix(" dB"),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = if (value != 0f && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                VerticalBandSlider(
                    value = value,
                    onValueChange = { onBand(i, it) },
                    label = stringResource(R.string.player_eq_band_description, EqSpec.label(freq)),
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().height(230.dp).padding(vertical = 6.dp),
                )
                Text(
                    EqSpec.label(freq),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
    Text(
        stringResource(R.string.player_eq_bands_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BitPerfectNote(settings: EqualizerSettings, bitPerfectOutput: Boolean) {
    val text = stringResource(
        when {
            settings.isProcessing && bitPerfectOutput -> R.string.player_eq_note_bit_perfect
            settings.isProcessing -> R.string.player_eq_note_processing
            else -> R.string.player_eq_note_bypass
        }
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Info, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp, top = 2.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
