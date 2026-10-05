package cl.lossless.player.playback.eq

/** Constantes del ecualizador gráfico de 10 bandas (frecuencias ISO por octava). */
object EqSpec {
    val FREQUENCIES = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)
    val BAND_COUNT = FREQUENCIES.size

    const val BAND_MIN_DB = -12f
    const val BAND_MAX_DB = 12f
    const val BAND_STEP_DB = 0.5f

    const val PREAMP_MIN_DB = -12f
    const val PREAMP_MAX_DB = 6f

    /** Ganancia máxima del refuerzo de graves al 100 %. */
    const val BASS_BOOST_MAX_DB = 10f
    const val BASS_BOOST_FREQ = 110f

    /** Q de un ecualizador gráfico de una octava. */
    const val BAND_Q = 1.41f

    val FLAT: List<Float> = List(BAND_COUNT) { 0f }

    fun label(freq: Float): String =
        if (freq >= 1000f) "${(freq / 1000f).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}k"
        else freq.toInt().toString()
}

/** Presintonía: [gains] null significa "Personalizado" (usa las bandas guardadas por el usuario). */
data class EqPreset(val id: String, val name: String, val gains: List<Float>?) {
    val isCustom: Boolean get() = gains == null
}

object EqPresets {
    val FLAT = EqPreset("flat", "Plano", EqSpec.FLAT)
    val CUSTOM = EqPreset("custom", "Personalizado", null)

    val ALL: List<EqPreset> = listOf(
        FLAT,
        EqPreset("rock", "Rock", gains(4, 3, 2, 0, -1, -1, 0, 2, 3, 4)),
        EqPreset("pop", "Pop", gains(-1, 1, 3, 4, 3, 0, -1, -1, 1, 2)),
        EqPreset("jazz", "Jazz", gains(3, 2, 1, 2, -1, -1, 0, 1, 2, 3)),
        EqPreset("classical", "Clásica", gains(4, 3, 2, 2, -1, -2, -1, 0, 2, 3)),
        EqPreset("dance", "Electrónica", gains(4, 4, 2, 0, -2, 2, 3, 3, 2, 0)),
        EqPreset("hiphop", "Hip-Hop", gains(5, 4, 2, 3, -1, -1, 1, -1, 2, 3)),
        EqPreset("acoustic", "Acústico", gains(3, 2, 1, 1, 2, 1, 2, 3, 3, 2)),
        EqPreset("bass", "Realce de graves", gains(6, 5, 4, 2, 0, 0, 0, 0, 0, 0)),
        EqPreset("vocal", "Voz / Podcast", gains(-2, -1, 0, 2, 4, 4, 3, 1, 0, -1)),
        CUSTOM,
    )

    fun byId(id: String): EqPreset = ALL.firstOrNull { it.id == id } ?: FLAT

    private fun gains(vararg db: Int) = db.map { it.toFloat() }
}

/** Estado completo del ecualizador (lo que se persiste y lo que lee el procesador de audio). */
data class EqualizerSettings(
    val enabled: Boolean = false,
    val presetId: String = EqPresets.FLAT.id,
    /** Ganancias activas por banda, en dB. */
    val bands: List<Float> = EqSpec.FLAT,
    /** Última posición manual del usuario (preset "Personalizado"). */
    val customBands: List<Float> = EqSpec.FLAT,
    val preampDb: Float = 0f,
    /** 0..1 */
    val bassBoost: Float = 0f,
    /** 0..1 */
    val virtualizer: Float = 0f,
) {
    /** true si el audio realmente se modifica (si no, el procesador deja pasar los bytes intactos). */
    val isProcessing: Boolean
        get() = enabled && (bands.any { it != 0f } || preampDb != 0f || bassBoost > 0f || virtualizer > 0f)
}
