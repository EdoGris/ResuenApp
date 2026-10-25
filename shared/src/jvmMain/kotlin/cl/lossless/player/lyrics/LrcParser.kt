/*
 * Basado en src/lrclib.js de Lyricslay — Copyright (c) 2026 Arzatar — Licencia MIT
 * (https://github.com/Arzatar/Lyricslay). Ver THIRD_PARTY_NOTICES.md.
 */
package cl.lossless.player.lyrics

/** Lector de letras en formato LRC ("[01:02.34]Texto"). */
object LrcParser {

    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val offsetTag = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""", RegexOption.IGNORE_CASE)
    /** Marcas por palabra del formato LRC "mejorado" (<00:12.34>). */
    private val wordTag = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val spaces = Regex("""\s+""")
    /** Duración máxima supuesta para la última palabra de una línea (el LRC no trae su fin). */
    private const val LAST_WORD_MS = 1_500L

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
            val body = line.replace(timeTag, "")
            val text = body.replace(wordTag, "").replace(spaces, " ").trim()
            if (text.isEmpty()) continue
            // Las marcas por palabra son absolutas: solo valen si la línea suena una sola vez.
            val words = if (tags.size == 1) parseWords(body, offsetMs) else null
            for (m in tags) {
                // [offset:+N] adelanta la letra N ms (convención del formato LRC).
                raw += LyricLine((toMs(m) - offsetMs).coerceAtLeast(0), text, words)
            }
        }
        if (raw.isEmpty()) return null
        return closeLastWords(dropDuplicatedSecondPass(raw).sortedBy { it.timeMs })
    }

    private fun toMs(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.toLong() ?: 0L
        return min * 60_000 + sec * 1_000 + frac
    }

    /** "<00:12.34>Hola <00:12.90>mundo" → palabras; el fin de cada una es el inicio de la siguiente. */
    private fun parseWords(body: String, offsetMs: Long): List<LyricWord>? {
        val marks = wordTag.findAll(body).toList()
        if (marks.isEmpty()) return null
        val starts = marks.map { (toMs(it) - offsetMs).coerceAtLeast(0) }
        val words = marks.mapIndexedNotNull { i, m ->
            val end = if (i + 1 < marks.size) marks[i + 1].range.first else body.length
            val text = body.substring(m.range.last + 1, end).trim()
            if (text.isEmpty()) null
            else LyricWord(starts[i], if (i + 1 < starts.size) starts[i + 1] else starts[i], text)
        }
        return words.takeIf { it.isNotEmpty() }
    }

    /** La última palabra de cada línea termina, a lo más, cuando empieza la línea siguiente. */
    private fun closeLastWords(lines: List<LyricLine>): List<LyricLine> = lines.mapIndexed { i, line ->
        val words = line.words ?: return@mapIndexed line
        val last = words.last()
        if (last.endMs > last.startMs) return@mapIndexed line
        val next = lines.getOrNull(i + 1)?.timeMs ?: (last.startMs + LAST_WORD_MS)
        val end = minOf(next, last.startMs + LAST_WORD_MS).coerceAtLeast(last.startMs)
        line.copy(words = words.dropLast(1) + last.copy(endMs = end))
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
