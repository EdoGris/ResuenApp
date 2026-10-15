package cl.lossless.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class LrcLibClientTest {

    private val record = """[{"trackName":"Yesterday","artistName":"The Beatles","albumName":"Help!",""" +
        """"duration":125,"instrumental":false,"plainLyrics":"Yesterday","syncedLyrics":null}]"""

    /** Responde en orden lo que se le da y anota cuánto se esperó. */
    private class Fake(vararg responses: LrcLibClient.Response) : LrcLibClient.Transport {
        val queue = ArrayDeque(responses.toList())
        var calls = 0
        override fun get(url: String, userAgent: String): LrcLibClient.Response {
            calls++
            return queue.removeFirst()
        }
    }

    @Test
    fun retriesWhenOverloadedAndHonorsRetryAfter() {
        val fake = Fake(LrcLibClient.Response(503, null, retryAfterSec = 1), LrcLibClient.Response(200, record))
        val waits = mutableListOf<Long>()
        val results = LrcLibClient(transport = fake, sleep = { waits += it }).search("Yesterday", "The Beatles")
        assertEquals(1, results.size)
        assertEquals(2, fake.calls)
        assertEquals(listOf(1_000L), waits)
    }

    @Test
    fun giveUpAfterRetriesWithServerError() {
        val fake = Fake(*Array(3) { LrcLibClient.Response(503, null) })
        try {
            LrcLibClient(transport = fake, sleep = {}).search("Yesterday", "")
            fail("debió lanzar ServerError")
        } catch (e: LrcLibClient.ServerError) {
            assertEquals(503, e.code)
            assertEquals(3, fake.calls)
        }
    }

    @Test
    fun badRequestAndNotFoundMeanNoResult() {
        assertNull(LrcLibClient(transport = Fake(LrcLibClient.Response(400, null)), sleep = {}).get("a", "b", 4000))
        assertNull(LrcLibClient(transport = Fake(LrcLibClient.Response(404, null)), sleep = {}).get("a", "b", 100))
        assertTrue(LrcLibClient(transport = Fake(LrcLibClient.Response(400, null)), sleep = {}).search("a", "b").isEmpty())
    }

    @Test
    fun networkFailureIsNotAServerError() {
        val client = LrcLibClient(transport = { _, _ -> throw UnknownHostException("lrclib.net") }, sleep = {})
        try {
            client.search("a", "b")
            fail("debió lanzar IOException")
        } catch (e: IOException) {
            assertTrue(e !is LrcLibClient.ServerError)
        }
    }
}
