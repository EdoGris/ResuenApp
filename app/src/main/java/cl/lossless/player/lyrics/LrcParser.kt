/*
 * Basado en src/lrclib.js de Lyricslay — Copyright (c) 2026 Arzatar — Licencia MIT
 * (https://github.com/Arzatar/Lyricslay). Ver THIRD_PARTY_NOTICES.md.
 */
package cl.lossless.player.lyrics

/** Lector de letras en formato LRC ("[01:02.34]Texto"). */
object LrcParser {

    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""", RegexOption.IGNORE_CASE)
    /** Marcas por palabra del formato LRC "mejorado" (<00:12.34>), que aquí no se usan. */
    private val wordTag = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{1,3})?>""")

    /**
     * Algunas letras traen una segunda copia completa de la canción en el mismo archivo
     * (p. ej. la transliteración tras la original): el reloj vuelve casi a cero después de
     * avanzar bastante. Un estribillo repetido avanza en el tiempo, nunca retrocede así.
     */
    private const val MIN_RUNNING_MAX_MS = 60_000L
    private const val RESTART_DROP_RATIO = 0.1

    /** Líneas ordenadas por tiempo, o null si el texto no tiene marcas de tiempo. */
    fun parse(lrc: String?): List<LyricLine>? {
        if (lrc.isNullOrBlank()) return null
        var offsetMs = 0L
        val raw = mutableListOf<LyricLine>()
        for (line in lrc.lineSequence()) {
            offsetTag.find(line)?.let { offsetMs = it.groupValues[1].toLong(); return@let }
            val tags = timeTag.findAll(line).toList()
            if (tags.isEmpty()) continue
            val text = line.replace(timeTag, "").replace(wordTag, "").trim()
            if (text.isEmpty()) continue
            for (m in tags) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.toLong() ?: 0L
                // [offset:+N] adelanta la letra N ms (convención del formato LRC).
                raw += LyricLine((min * 60_000 + sec * 1_000 + frac - offsetMs).coerceAtLeast(0), text)
            }
        }
        if (raw.isEmpty()) return null
        return dropDuplicatedSecondPass(raw).sortedBy { it.timeMs }
    }

    /** Recibe las líneas en el orden del archivo (antes de ordenar). */
    internal fun dropDuplicatedSecondPass(lines: List<LyricLine>): List<LyricLine> {
        var runningMax = 0L
        lines.forEachIndexed { i, line ->
            if (runningMax >= MIN_RUNNING_MAX_MS && line.timeMs < runningMax * RESTART_DROP_RATIO) {
                return lines.subList(0, i)
            }
            if (line.timeMs > runningMax) runningMax = line.timeMs
        }
        return lines
    }

    /** Interpreta un texto que puede ser LRC o letra simple. */
    fun toLyrics(text: String, source: LyricsSource): Lyrics? {
        if (text.isBlank()) return null
        val synced = parse(text)
        return if (synced != null) Lyrics(synced, plain = synced.joinToString("\n") { it.text }, source = source)
        else Lyrics(null, plain = text.trim(), source = source)
    }
}
