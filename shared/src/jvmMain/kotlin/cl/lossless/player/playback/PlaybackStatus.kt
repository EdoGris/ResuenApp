package cl.lossless.player.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** Estado compartido entre el servicio y la UI (mismo proceso). */
object PlaybackStatus {
    /** Prefijo fijo (no se traduce) de [output] cuando la salida es bit-perfect; la UI lo detecta. */
    const val BIT_PERFECT_PREFIX = "BIT-PERFECT"
    /** Valor inicial de [source] y [output] hasta que el servicio los describe (ya traducidos). */
    const val UNKNOWN = "—"

    val source = MutableStateFlow(UNKNOWN)
    val output = MutableStateFlow(UNKNOWN)
}
