package cl.lossless.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlignmentClientTest {

    @Test
    fun jobIdMatchesServer() {
        // Mismo vector que aligner/tests/test_text.py: si cambia uno, cambia el otro.
        val audio = "a".repeat(64)
        assertEquals(
            "1119a76a42d85f4a9d682b1a3cb7f11178dc87119f24f0afb453129c2e128957",
            AlignmentClient.jobId(audio, "Uno\ndos"),
        )
        assertEquals(AlignmentClient.jobId(audio, "﻿Uno\r\n\r\n  dos  "), AlignmentClient.jobId(audio.uppercase(), "Uno\ndos"))
    }

    @Test
    fun hashesStreams() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            AlignmentClient.sha256("".byteInputStream()),
        )
    }
}

class AlignmentJsonTest {

    @Test
    fun parsesFinishedJob() {
        val job = AlignmentJson.parseJob(
            """
            {"id":"abc","status":"done","result":{"version":1,"language":"es","durationMs":200000,
             "model":"m","confidence":0.87,"lines":[
               {"startMs":15000,"endMs":16000,"text":"Segunda","estimated":true,"words":[]},
               {"startMs":12340,"endMs":14000,"text":"Hola mundo","words":[
                 {"startMs":12340,"endMs":12800,"text":"Hola","score":0.9},
                 {"startMs":12900,"endMs":14000,"text":"mundo"}]}
             ]}}
            """.trimIndent()
        )
        assertEquals(AlignmentJob.Status.DONE, job.status)
        assertFalse(job.pending)
        assertEquals("es", job.language)
        assertEquals(0.87, job.confidence, 1e-9)
        val lines = job.lines!!
        assertEquals(listOf(12_340L, 15_000L), lines.map { it.timeMs }) // ordenadas
        assertEquals(listOf("Hola", "mundo"), lines[0].words!!.map { it.text })
        assertNull(lines[1].words)

        val lyrics = AlignmentJson.toLyrics(job, "Hola mundo\nSegunda")!!
        assertEquals(LyricsSource.ALIGNED, lyrics.source)
        assertTrue(lyrics.isSynced)
        assertEquals("Hola mundo\nSegunda", lyrics.plain)
    }

    @Test
    fun parsesPendingAndFailedJobs() {
        val queued = AlignmentJson.parseJob("""{"id":"x","status":"queued"}""")
        assertTrue(queued.pending)
        assertNull(AlignmentJson.toLyrics(queued, "a"))

        val failed = AlignmentJson.parseJob("""{"id":"x","status":"failed","error":"sin voz"}""")
        assertEquals(AlignmentJob.Status.FAILED, failed.status)
        assertEquals("sin voz", failed.error)
    }
}

class WordTimingTest {

    @Test
    fun enhancedLrcKeepsWordTimes() {
        val lines = LrcParser.parse("[00:10.00]<00:10.00>Hola <00:10.50>mundo\n[00:12.00]Fin")!!
        val words = lines[0].words!!
        assertEquals(listOf("Hola", "mundo"), words.map { it.text })
        assertEquals(listOf(10_000L, 10_500L), words.map { it.startMs })
        assertEquals(10_500L, words[0].endMs)
        // La última palabra termina, a lo más, al empezar la línea siguiente.
        assertEquals(12_000L, words[1].endMs)
        assertEquals("Hola mundo", lines[0].text)
        assertNull(lines[1].words)
    }

    @Test
    fun wordTimesFollowOffsetAndAreDroppedOnRepeatedLines() {
        val shifted = LrcParser.parse("[offset:+500]\n[00:10.00]<00:10.00>Hola <00:10.50>mundo")!!
        assertEquals(listOf(9_500L, 10_000L), shifted.single().words!!.map { it.startMs })

        val repeated = LrcParser.parse("[00:10.00][00:50.00]<00:10.00>Coro <00:10.50>coro")!!
        assertTrue(repeated.all { it.words == null })
    }

    @Test
    fun wordIndexAtFindsCurrentWord() {
        val line = LyricLine(
            1_000, "a b c",
            listOf(LyricWord(1_000, 1_200, "a"), LyricWord(1_300, 1_500, "b"), LyricWord(1_600, 2_000, "c")),
        )
        assertEquals(-1, line.wordIndexAt(900))
        assertEquals(0, line.wordIndexAt(1_250))
        assertEquals(1, line.wordIndexAt(1_300))
        assertEquals(2, line.wordIndexAt(5_000))
        assertEquals(-1, LyricLine(0, "x").wordIndexAt(100))
    }
}
