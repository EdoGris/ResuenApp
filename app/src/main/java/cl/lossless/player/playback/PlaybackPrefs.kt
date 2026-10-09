package cl.lossless.player.playback

import android.content.Context

/** Ajustes que lee el servicio de reproducción. */
class PlaybackPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)

    /**
     * Los botones de avance y retroceso de los audífonos (en muchos modelos, mantener
     * presionado el lado derecho o izquierdo) cambian de canción en vez de adelantar o
     * retroceder unos segundos.
     */
    var mediaKeysSkip: Boolean
        get() = prefs.getBoolean(KEY_MEDIA_KEYS_SKIP, true)
        set(value) = prefs.edit().putBoolean(KEY_MEDIA_KEYS_SKIP, value).apply()

    private companion object {
        const val KEY_MEDIA_KEYS_SKIP = "media_keys_skip"
    }
}
