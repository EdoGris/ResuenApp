package cl.lossless.player.lyrics

import kotlin.math.max

/**
 * Estima los tiempos de una letra sin marcas a partir de los tramos con voz.
 *
 * 1. Cada estrofa se ancla al inicio de un tramo con voz (tras un instrumental, la voz
 *    suele volver al empezar una estrofa), eligiendo la asignación más coherente con el
 *    largo de la letra (programación dinámica).
 * 2. Entre anclas, las líneas se reparten sobre el "tiempo cantado" (saltando los
 *    instrumentales) en proporción a sus sílabas.
 */
object LyricsAligner {

    /** Línea de texto y si empieza una estrofa (venía tras una línea en blanco). */
    data class TextLine(val text: String, val startsStanza: Boolean)

    /** Rótulos que no se cantan: "[Coro]", "(x2)", "Verse 1:". "(Oh, yeah)" sí se canta. */
    private val label = Regex(
        """^\s*(\[[^\]]*]|\(\s*(x\s*\d+|\d+\s*x|bis|repeat|repetir)\s*\)|""" +
            """(verse|verso|chorus|coro|estribillo|bridge|puente|intro|outro|pre-?chorus)\s*\d*\s*:?)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val vowelGroups = Regex("[aeiouyáéíóúüàèìòùâêîôûäëïö]+", RegexOption.IGNORE_CASE)

    /** Divide la letra en líneas cantables, marcando dónde empieza cada estrofa. */
    fun splitLines(plain: String): List<TextLine> {
        val out = mutableListOf<TextLine>()
        var newStanza = true
        for (raw in plain.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || label.matches(line)) { newStanza = true; continue }
            out += TextLine(line, newStanza)
            newStanza = false
        }
        return out
    }

    /** Sílabas aproximadas (grupos de vocales); para escrituras sin vocales latinas, por caracteres. */
    fun syllables(text: String): Int {
        val groups = vowelGroups.findAll(text).count()
        if (groups > 0) return groups
        val letters = text.count { it.isLetterOrDigit() }
        return max(1, letters / 2)
    }

    /**
     * Tiempos estimados para [lines]. Sin tramos (no se pudo analizar el audio) se usa toda la
     * canción salvo una intro y un final típicos.
     */
    fun align(lines: List<TextLine>, segments: List<VocalSegment>, durationMs: Long): List<LyricLine> {
        if (lines.isEmpty()) return emptyList()
        val vocal = segments.filter { it.lengthMs > 0 }.ifEmpty {
            listOf(VocalSegment((durationMs * 0.07).toLong(), (durationMs * 0.93).toLong()))
        }
        // +1 por línea: incluso una muy corta ocupa algo de tiempo (respiración, pausa).
        val weights = lines.map { syllables(it.text) + 1.0 }

        // 1) Puntos fijos: inicios de estrofa anclados a inicios de tramo.
        val stanzas = lines.indices.filter { it == 0 || lines[it].startsStanza }
        val anchors = chooseAnchors(stanzas, weights, vocal)
        val fixed = sortedMapOf<Int, Long>()
        stanzas.forEachIndexed { k, line -> anchors[k]?.let { fixed[line] = vocal[it].startMs } }
        if (0 !in fixed) fixed[0] = vocal.first().startMs

        // 2) Entre dos puntos fijos, las líneas se reparten por sílabas sobre el tiempo cantado.
        val starts = LongArray(lines.size)
        val points = fixed.entries.map { it.key to it.value } + (lines.size to vocal.last().endMs)
        for ((a, b) in points.zipWithNext()) {
            val (fromLine, fromTime) = a
            val (toLine, toTime) = b
            val clipped = clip(vocal, fromTime, maxOf(toTime, fromTime + 1))
            val totalVocal = clipped.sumOf { it.lengthMs }.toDouble()
            val totalWeight = (fromLine until toLine).sumOf { weights[it] }
            var cumulative = 0.0
            for (i in fromLine until toLine) {
                starts[i] = if (totalVocal <= 0) fromTime else vocalToReal(cumulative / totalWeight * totalVocal, clipped)
                cumulative += weights[i]
            }
        }
        // Mantener el orden y al menos 300 ms entre líneas.
        for (i in 1 until starts.size) {
            if (starts[i] < starts[i - 1] + 300) starts[i] = starts[i - 1] + 300
        }
        return lines.mapIndexed { i, l -> LyricLine(starts[i], l.text) }
    }

    /**
     * Asigna a cada estrofa un tramo de voz (o ninguno) en orden, por programación dinámica.
     * El costo compara qué fracción de la letra va antes de la estrofa con qué fracción del
     * tiempo cantado va antes del tramo: tras un instrumental, la voz vuelve en una estrofa nueva.
     */
    private fun chooseAnchors(stanzas: List<Int>, weights: List<Double>, vocal: List<VocalSegment>): List<Int?> {
        val totalWeight = weights.sum()
        val totalVocal = vocal.sumOf { it.lengthMs }.toDouble()
        val textFraction = stanzas.map { line -> weights.subList(0, line).sum() / totalWeight }
        val vocalBefore = DoubleArray(vocal.size)
        for (m in 1 until vocal.size) vocalBefore[m] = vocalBefore[m - 1] + vocal[m - 1].lengthMs
        val vocalFraction = vocalBefore.map { it / totalVocal }

        val unanchoredPenalty = 0.12
        val k = stanzas.size
        val m = vocal.size
        // cost[i][last + 1]: costo mínimo tras decidir i estrofas, con el último tramo usado = last.
        val inf = Double.MAX_VALUE / 4
        val cost = Array(k + 1) { DoubleArray(m + 1) { inf } }
        val choice = Array(k + 1) { arrayOfNulls<Pair<Int, Int?>>(m + 1) } // (last previo + 1, tramo elegido)
        cost[0][0] = 0.0
        for (i in 0 until k) {
            for (lastPlus in 0..m) {
                val base = cost[i][lastPlus]
                if (base >= inf) continue
                // Sin ancla
                if (base + unanchoredPenalty < cost[i + 1][lastPlus]) {
                    cost[i + 1][lastPlus] = base + unanchoredPenalty
                    choice[i + 1][lastPlus] = lastPlus to null
                }
                // Anclada a un tramo posterior al último usado
                for (seg in lastPlus until m) {
                    val c = base + kotlin.math.abs(textFraction[i] - vocalFraction[seg])
                    if (c < cost[i + 1][seg + 1]) {
                        cost[i + 1][seg + 1] = c
                        choice[i + 1][seg + 1] = lastPlus to seg
                    }
                }
            }
        }
        var state = cost[k].indices.minByOrNull { cost[k][it] } ?: return List(k) { null }
        val result = arrayOfNulls<Int>(k)
        for (i in k downTo 1) {
            val (previous, seg) = choice[i][state] ?: break
            result[i - 1] = seg
            state = previous
        }
        return result.toList()
    }

    /** Los tramos recortados al intervalo [from, to). */
    private fun clip(segments: List<VocalSegment>, from: Long, to: Long): List<VocalSegment> =
        segments.mapNotNull { s ->
            val a = maxOf(s.startMs, from)
            val b = minOf(s.endMs, to)
            if (b > a) VocalSegment(a, b) else null
        }

    /** Convierte una posición en "tiempo cantado" a tiempo real de la canción. */
    private fun vocalToReal(vocalMs: Double, segments: List<VocalSegment>): Long {
        var remaining = vocalMs
        for (s in segments) {
            if (remaining < s.lengthMs) return s.startMs + remaining.toLong()
            remaining -= s.lengthMs
        }
        return segments.last().endMs - 1
    }
}

/** Escribe letras sincronizadas en formato LRC. */
object LrcWriter {
    fun write(lines: List<LyricLine>, title: String = "", artist: String = ""): String = buildString {
        if (title.isNotBlank()) appendLine("[ti:$title]")
        if (artist.isNotBlank()) appendLine("[ar:$artist]")
        appendLine("[by:ResuenApp]")
        for (line in lines) {
            val total = line.timeMs.coerceAtLeast(0)
            val min = total / 60_000
            val sec = (total % 60_000) / 1_000
            val cs = (total % 1_000) / 10
            appendLine("[%02d:%02d.%02d]%s".format(min, sec, cs, line.text))
        }
    }
}
