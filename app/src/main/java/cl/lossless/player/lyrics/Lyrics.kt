package cl.lossless.player.lyrics

/** Una línea de letra sincronizada. */
data class LyricLine(val timeMs: Long, val text: String)

enum class LyricsSource(val label: String) {
    SIDECAR("Archivo .lrc"),
    EMBEDDED("Etiquetas del archivo"),
    LRCLIB("LRCLIB"),
}

/**
 * Letra de una pista: sincronizada ([synced]) cuando hay marcas de tiempo; si no, solo texto
 * ([plain]). Una pista instrumental puede no tener ninguna de las dos.
 */
data class Lyrics(
    val synced: List<LyricLine>?,
    val plain: String?,
    val source: LyricsSource,
    val instrumental: Boolean = false,
) {
    val isSynced: Boolean get() = !synced.isNullOrEmpty()

    /** Índice de la línea que suena en [positionMs] (-1 antes de la primera). */
    fun lineIndexAt(positionMs: Long): Int {
        val lines = synced ?: return -1
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
        }
        return found
    }
}
