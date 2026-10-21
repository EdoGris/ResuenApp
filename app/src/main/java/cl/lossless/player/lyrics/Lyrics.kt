package cl.lossless.player.lyrics

import androidx.annotation.StringRes
import cl.lossless.player.R

/** Una palabra con su tiempo (LRC "mejorado" o alineación del servidor). */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

/** Una línea de letra sincronizada; [words] solo si se conoce el tiempo de cada palabra. */
data class LyricLine(val timeMs: Long, val text: String, val words: List<LyricWord>? = null) {
    /** Índice de la palabra que suena en [positionMs] (-1 antes de la primera o sin palabras). */
    fun wordIndexAt(positionMs: Long): Int {
        val list = words ?: return -1
        var found = -1
        for (i in list.indices) {
            if (list[i].startMs <= positionMs) found = i else break
        }
        return found
    }
}

enum class LyricsSource(@StringRes val labelRes: Int) {
    SIDECAR(R.string.lyrics_source_sidecar),
    EMBEDDED(R.string.lyrics_source_embedded),
    LRCLIB(R.string.lyrics_source_lrclib),
    LYRICS_OVH(R.string.lyrics_source_lyrics_ovh),
    USER(R.string.lyrics_source_user),
    ALIGNED(R.string.lyrics_source_aligned),
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
    /** Tiempos calculados por la app (análisis del audio), no venían con la letra. */
    val estimated: Boolean = false,
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
