package cl.lossless.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class LyricsOvhClientTest {

    private fun client(code: Int, body: String?, urls: MutableList<String> = mutableListOf()) =
        LyricsOvhClient(transport = { url, _ -> urls += url; LrcLibClient.Response(code, body) })

    @Test
    fun encodesSpacesAndAccentsInPath() {
        val urls = mutableListOf<String>()
        client(404, null, urls).get("Soda Stereo", "De Música Ligera")
        assertEquals("https://api.lyrics.ovh/v1/Soda%20Stereo/De%20M%C3%BAsica%20Ligera", urls.single())
    }

    @Test
    fun returnsCleanedLyrics() {
        val body = """{"lyrics":"Paroles de la chanson Yellow par Coldplay\r\nLook at the stars\r\n\r\n\r\n\r\nLook how they shine  \r\n"}"""
        assertEquals("Look at the stars\n\nLook how they shine", client(200, body).get("Coldplay", "Yellow"))
    }

    @Test
    fun noLyricsMeansNull() {
        assertNull(client(404, """{"error":"No lyrics found"}""").get("a", "b"))
        assertNull(client(200, """{"lyrics":"  "}""").get("a", "b"))
        assertNull(client(200, "<html>").get("a", "b"))
    }

    @Test
    fun needsArtistAndTitle() {
        val urls = mutableListOf<String>()
        assertNull(client(200, """{"lyrics":"x"}""", urls).get("", "Yellow"))
        assertEquals(0, urls.size)
    }

    @Test
    fun serverErrorThrows() {
        try {
            client(503, null).get("a", "b")
            fail("debió lanzar ServerError")
        } catch (e: LyricsOvhClient.ServerError) {
            assertEquals(503, e.code)
        }
    }
}
