package cl.lossless.player.desktop

import cl.lossless.player.playback.eq.Biquad
import cl.lossless.player.playback.eq.EqMath
import cl.lossless.player.playback.eq.EqPreset
import cl.lossless.player.playback.eq.EqPresets
import cl.lossless.player.playback.eq.EqSpec
import cl.lossless.player.playback.eq.EqualizerSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Ecualizador de 10 bandas de escritorio: mismas bandas, presintonías y filtros que en Android
 * (módulo :shared). Los cambios se oyen en vivo: el hilo de audio lee [settings] en cada cuadro.
 */
object DesktopEqualizer {

    private val prefs: Preferences = Preferences.userRoot().node("cl/lossless/resuenapp/equalizer")

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<EqualizerSettings> = _settings.asStateFlow()

    val presets: List<EqPreset> = EqPresets.ALL

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun selectPreset(preset: EqPreset) = update { it.copy(presetId = preset.id, bands = preset.gains ?: it.customBands) }

    /** Mover una banda pasa a "Personalizado" y guarda esa posición como la personalizada. */
    fun setBand(index: Int, db: Float) = update {
        val bands = it.bands.toMutableList().also { b -> b[index] = snap(db.coerceIn(EqSpec.BAND_MIN_DB, EqSpec.BAND_MAX_DB)) }
        it.copy(presetId = EqPresets.CUSTOM.id, bands = bands, customBands = bands)
    }

    fun setPreamp(db: Float) = update { it.copy(preampDb = snap(db.coerceIn(EqSpec.PREAMP_MIN_DB, EqSpec.PREAMP_MAX_DB))) }

    /** Vuelve a plano (bandas y preamp en 0 dB); conserva el encendido. */
    fun reset() = update { it.copy(presetId = EqPresets.FLAT.id, bands = EqSpec.FLAT, preampDb = 0f) }

    private fun update(change: (EqualizerSettings) -> EqualizerSettings) {
        val next = change(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        prefs.putBoolean("enabled", next.enabled)
        prefs.put("preset", next.presetId)
        prefs.put("bands", next.bands.joinToString(","))
        prefs.put("custom_bands", next.customBands.joinToString(","))
        prefs.putFloat("preamp_db", next.preampDb)
    }

    private fun load(): EqualizerSettings {
        val d = EqualizerSettings()
        return EqualizerSettings(
            enabled = prefs.getBoolean("enabled", d.enabled),
            presetId = EqPresets.byId(prefs.get("preset", d.presetId)).id,
            bands = parseBands(prefs.get("bands", null)) ?: d.bands,
            customBands = parseBands(prefs.get("custom_bands", null)) ?: d.customBands,
            preampDb = prefs.getFloat("preamp_db", d.preampDb).coerceIn(EqSpec.PREAMP_MIN_DB, EqSpec.PREAMP_MAX_DB),
        )
    }

    private fun parseBands(raw: String?): List<Float>? =
        raw?.split(',')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == EqSpec.BAND_COUNT }
            ?.map { it.coerceIn(EqSpec.BAND_MIN_DB, EqSpec.BAND_MAX_DB) }

    private fun snap(db: Float): Float = Math.round(db / EqSpec.BAND_STEP_DB) * EqSpec.BAND_STEP_DB
}

/**
 * Aplica el ecualizador a las muestras de una pista (enteros de 32 bits intercalados, como
 * los entrega [Decoder]). Apagado o en plano no toca las muestras: la salida queda idéntica
 * al archivo.
 */
class EqProcessor(
    private val sampleRate: Int,
    private val channels: Int,
    private val settings: StateFlow<EqualizerSettings> = DesktopEqualizer.settings,
) {
    private var applied: EqualizerSettings? = null
    /** [canal][etapa] */
    private var filters: Array<Array<Biquad>> = emptyArray()
    private var preamp = 1.0
    private var wasProcessing = false

    /** Procesa [samples] en el mismo arreglo. */
    fun process(samples: IntArray) {
        val s = settings.value
        if (!s.isProcessing) {
            wasProcessing = false
            return
        }
        if (s != applied) rebuild(s)
        if (!wasProcessing) {
            reset() // sin "pop" al activarlo
            wasProcessing = true
        }
        var i = 0
        while (i + channels <= samples.size) {
            for (ch in 0 until channels) {
                var x = samples[i + ch] / SCALE * preamp
                for (stage in filters[ch]) x = stage.process(x)
                samples[i + ch] = (x * SCALE).roundToLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
            }
            i += channels
        }
    }

    /** Borra el estado de los filtros (al saltar a otro punto de la pista). */
    fun reset() {
        filters.forEach { channel -> channel.forEach(Biquad::reset) }
    }

    private fun rebuild(s: EqualizerSettings) {
        val stages = EqMath.stages(s, sampleRate.toDouble())
        if (filters.size == channels && filters.firstOrNull()?.size == stages.size) {
            // Mismo número de etapas: solo cambian los coeficientes y se conserva el estado.
            filters.forEach { channel -> channel.forEachIndexed { i, f -> f.setCoefficients(stages[i]) } }
        } else {
            filters = Array(channels) { Array(stages.size) { i -> Biquad(stages[i]) } }
        }
        preamp = 10.0.pow(s.preampDb / 20.0)
        applied = s
    }

    private companion object {
        const val SCALE = 2_147_483_648.0
    }
}
