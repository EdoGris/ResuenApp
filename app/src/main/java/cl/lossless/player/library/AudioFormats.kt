package cl.lossless.player.library

/** Formatos que la biblioteca reconoce: tipos MIME, extensiones y su rótulo de contenedor. */
object AudioFormats {

    private val wavMimes = setOf("audio/wav", "audio/x-wav", "audio/vnd.wave", "audio/wave")

    val supportedMimes: List<String> = listOf(
        "audio/mpeg",                              // MP3
        "audio/flac", "audio/x-flac",              // FLAC
        "audio/mp4", "audio/x-m4a", "audio/alac",  // ALAC (contenedor MP4/M4A)
    ) + wavMimes                                   // WAV (PCM sin comprimir)

    /** Extensión (en minúsculas) → tipo MIME normalizado, para carpetas no indexadas. */
    val extensions: Map<String, String> = mapOf(
        "mp3" to "audio/mpeg",
        "flac" to "audio/flac",
        "m4a" to "audio/mp4",
        "alac" to "audio/mp4",
        "wav" to "audio/x-wav",
    )

    /** Un .m4a puede ser ALAC (sin pérdida) o AAC (con pérdida); el códec real se ve al reproducir. */
    fun containerLabel(mimeType: String): String = when (mimeType) {
        "audio/mpeg" -> "MP3"
        "audio/flac", "audio/x-flac" -> "FLAC"
        in wavMimes -> "WAV"
        else -> "M4A"
    }
}
