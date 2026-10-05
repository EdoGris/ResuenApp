package cl.lossless.player.playback.eq

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fuente única del estado del ecualizador, compartida por la UI y el servicio de reproducción
 * (mismo proceso). Se persiste en SharedPreferences en cada cambio.
 */
class EqualizerRepository private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<EqualizerSettings> = _settings.asStateFlow()

    fun update(change: (EqualizerSettings) -> EqualizerSettings) {
        val next = change(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        save(next)
    }

    private fun load(): EqualizerSettings {
        val defaults = EqualizerSettings()
        return EqualizerSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            presetId = prefs.getString(KEY_PRESET, null)?.let { EqPresets.byId(it).id } ?: defaults.presetId,
            bands = parseBands(prefs.getString(KEY_BANDS, null)) ?: defaults.bands,
            customBands = parseBands(prefs.getString(KEY_CUSTOM, null)) ?: defaults.customBands,
            preampDb = prefs.getFloat(KEY_PREAMP, defaults.preampDb)
                .coerceIn(EqSpec.PREAMP_MIN_DB, EqSpec.PREAMP_MAX_DB),
            bassBoost = prefs.getFloat(KEY_BASS, defaults.bassBoost).coerceIn(0f, 1f),
            virtualizer = prefs.getFloat(KEY_VIRTUALIZER, defaults.virtualizer).coerceIn(0f, 1f),
        )
    }

    private fun save(s: EqualizerSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, s.enabled)
            .putString(KEY_PRESET, s.presetId)
            .putString(KEY_BANDS, s.bands.joinToString(","))
            .putString(KEY_CUSTOM, s.customBands.joinToString(","))
            .putFloat(KEY_PREAMP, s.preampDb)
            .putFloat(KEY_BASS, s.bassBoost)
            .putFloat(KEY_VIRTUALIZER, s.virtualizer)
            .apply()
    }

    private fun parseBands(raw: String?): List<Float>? =
        raw?.split(',')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == EqSpec.BAND_COUNT }
            ?.map { it.coerceIn(EqSpec.BAND_MIN_DB, EqSpec.BAND_MAX_DB) }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PRESET = "preset"
        private const val KEY_BANDS = "bands"
        private const val KEY_CUSTOM = "custom_bands"
        private const val KEY_PREAMP = "preamp_db"
        private const val KEY_BASS = "bass_boost"
        private const val KEY_VIRTUALIZER = "virtualizer"

        @Volatile private var instance: EqualizerRepository? = null

        fun get(context: Context): EqualizerRepository =
            instance ?: synchronized(this) {
                instance ?: EqualizerRepository(context.applicationContext).also { instance = it }
            }
    }
}
