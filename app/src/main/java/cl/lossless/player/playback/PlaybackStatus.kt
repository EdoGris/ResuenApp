package cl.lossless.player.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** Estado compartido entre el servicio y la UI (mismo proceso). */
object PlaybackStatus {
    val source = MutableStateFlow("—")
    val output = MutableStateFlow("Salida estándar del sistema")
}
