package cl.lossless.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class VocalActivityTest {

    /**
     * 0–5 s: instrumental abierto a los costados (L = −R, nada al centro).
     * 5–15 s: "voz" al centro. 15–20 s: casi silencio. 20–30 s: "voz" al centro otra vez.
     */
    @Test
    fun findsCenteredVoiceSections() {
        val fs = 16_000
        val acc = VocalActivity.Accumulator(fs)
        for (n in 0 until fs * 30) {
            val t = n.toDouble() / fs
            val (l, r) = when {
                t < 5 -> { val s = 0.3 * sin(2 * PI * 900 * t); s to -s }
                t < 15 -> { val v = 0.4 * sin(2 * PI * 440 * t) * (0.6 + 0.4 * sin(2 * PI * 3 * t)); v to v }
                t < 20 -> { val s = 0.001 * sin(2 * PI * 200 * t); s to s }
                else -> { val v = 0.4 * sin(2 * PI * 660 * t); v to v }
            }
            acc.add(l, r)
        }
        val segments = VocalActivity.detect(acc.features())
        assertEquals(2, segments.size)
        assertEquals(5_000.0, segments[0].startMs.toDouble(), 700.0)
        assertEquals(15_000.0, segments[0].endMs.toDouble(), 700.0)
        assertEquals(20_000.0, segments[1].startMs.toDouble(), 700.0)
        assertEquals(30_000.0, segments[1].endMs.toDouble(), 700.0)
    }

    @Test
    fun flatSignalGivesNoSegments() {
        val features = VocalActivity.Features(FloatArray(400) { -20f }, FloatArray(400) { -20f }, 400)
        assertTrue(VocalActivity.detect(features).isEmpty())
    }
}

class LyricsAlignerTest {

    @Test
    fun splitsStanzasAndSkipsLabels() {
        val lines = LyricsAligner.splitLines(
            """
            [Verso 1]
            Primera línea
            Segunda línea

            Coro:
            Tercera (oh, yeah)
            (x2)
            """.trimIndent()
        )
        assertEquals(listOf("Primera línea", "Segunda línea", "Tercera (oh, yeah)"), lines.map { it.text })
        assertEquals(listOf(true, false, true), lines.map { it.startsStanza })
    }

    @Test
    fun countsSyllablesRoughly() {
        assertEquals(5, LyricsAligner.syllables("canción de amor")) // can-ción-de-a-mor
        assertTrue(LyricsAligner.syllables("夜に駆ける") >= 1)
    }

    @Test
    fun placesLinesOnlyInsideVocalSectionsAndSnapsStanzas() {
        val segments = listOf(VocalSegment(10_000, 30_000), VocalSegment(60_000, 80_000))
        val lines = listOf(
            LyricsAligner.TextLine("uno dos tres cuatro", true),
            LyricsAligner.TextLine("cinco seis siete ocho", false),
            LyricsAligner.TextLine("nueve diez once doce", true),
            LyricsAligner.TextLine("trece catorce quince", false),
        )
        val result = LyricsAligner.align(lines, segments, durationMs = 90_000)
        assertEquals(10_000L, result[0].timeMs) // primera estrofa al inicio del primer tramo
        assertEquals(60_000L, result[2].timeMs) // la segunda, tras el instrumental
        result.forEach { line ->
            assertTrue("${line.timeMs} fuera de los tramos", segments.any { line.timeMs in it.startMs until it.endMs })
        }
        assertEquals(result.map { it.timeMs }.sorted(), result.map { it.timeMs })
    }

    @Test
    fun withoutAnalysisUsesTheWholeSongMinusIntroAndOutro() {
        val lines = (1..10).map { LyricsAligner.TextLine("línea número $it", it == 1) }
        val result = LyricsAligner.align(lines, emptyList(), durationMs = 200_000)
        assertTrue(result.first().timeMs >= 13_000)
        assertTrue(result.last().timeMs <= 186_000)
    }
}

class LrcWriterTest {
    @Test
    fun roundTripsThroughTheParser() {
        val lines = listOf(LyricLine(1_230, "Hola"), LyricLine(65_400, "Chao"))
        val lrc = LrcWriter.write(lines, "Canción", "Artista")
        assertTrue(lrc.startsWith("[ti:Canción]"))
        val parsed = LrcParser.parse(lrc)!!
        assertEquals(lines.map { it.text }, parsed.map { it.text })
        parsed.zip(lines).forEach { (a, b) -> assertTrue(abs(a.timeMs - b.timeMs) < 10) }
    }
}
