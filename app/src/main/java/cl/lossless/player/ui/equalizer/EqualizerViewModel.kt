package cl.lossless.player.ui.equalizer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import cl.lossless.player.playback.eq.EqPreset
import cl.lossless.player.playback.eq.EqPresets
import cl.lossless.player.playback.eq.EqSpec
import cl.lossless.player.playback.eq.EqualizerRepository
import cl.lossless.player.playback.eq.EqualizerSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * Lógica de presentación del ecualizador. Los cambios se aplican al audio en vivo:
 * el procesador del servicio lee el mismo [EqualizerRepository.settings].
 */
class EqualizerViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = EqualizerRepository.get(app)

    val settings: StateFlow<EqualizerSettings> = repository.settings
    val presets: List<EqPreset> = EqPresets.ALL

    fun setEnabled(enabled: Boolean) = repository.update { it.copy(enabled = enabled) }

    fun selectPreset(preset: EqPreset) = repository.update {
        it.copy(presetId = preset.id, bands = preset.gains ?: it.customBands)
    }

    /** Mover una banda pasa a "Personalizado" y guarda esa posición como la personalizada. */
    fun setBand(index: Int, db: Float) = repository.update {
        val value = snap(db.coerceIn(EqSpec.BAND_MIN_DB, EqSpec.BAND_MAX_DB))
        val bands = it.bands.toMutableList().also { b -> b[index] = value }
        it.copy(presetId = EqPresets.CUSTOM.id, bands = bands, customBands = bands)
    }

    fun setPreamp(db: Float) = repository.update {
        it.copy(preampDb = snap(db.coerceIn(EqSpec.PREAMP_MIN_DB, EqSpec.PREAMP_MAX_DB)))
    }

    fun setBassBoost(strength: Float) = repository.update { it.copy(bassBoost = strength.coerceIn(0f, 1f)) }

    fun setVirtualizer(strength: Float) = repository.update { it.copy(virtualizer = strength.coerceIn(0f, 1f)) }

    /** Vuelve a plano: bandas en 0 dB, sin preamp, graves ni virtualizador (conserva el encendido). */
    fun reset() = repository.update {
        it.copy(
            presetId = EqPresets.FLAT.id,
            bands = EqSpec.FLAT,
            preampDb = 0f,
            bassBoost = 0f,
            virtualizer = 0f,
        )
    }

    private fun snap(db: Float): Float = Math.round(db / EqSpec.BAND_STEP_DB) * EqSpec.BAND_STEP_DB
}
