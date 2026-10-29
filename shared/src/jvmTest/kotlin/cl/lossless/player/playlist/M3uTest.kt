package cl.lossless.player.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uTest {

    @Test
    fun writeThenParseKeepsNameAndOrder() {
        val paths = listOf("C:\\Música\\Sabina\\01.flac", "/Users/edo/Music/b.m4a", "C:\\Música\\Sabina\\01.flac")
        val (name, parsed) = M3u.parse(M3u.write("Para viajar", paths))
        assertEquals("Para viajar", name)
        assertEquals(paths, parsed)
    }

    @Test
    fun parsesForeignPlaylists() {
        val text = "\uFEFF#EXTM3U\r\n#EXTINF:285,Joaquín Sabina - 19 días\r\nsub\\19.flac\r\n\r\nfile:///C:/M%C3%BAsica/a%20b.mp3\r\nfile:///home/edo/c.flac\r\n"
        val (name, parsed) = M3u.parse(text) { "D:\\Listas\\$it" }
        assertNull(name)
        assertEquals(listOf("D:\\Listas\\sub\\19.flac", "C:/Música/a b.mp3", "/home/edo/c.flac"), parsed)
    }

    @Test
    fun fileStemDropsForbiddenCharacters() {
        assertEquals("Rock_ 80_90", M3u.fileStem("Rock: 80/90"))
        assertEquals("lista", M3u.fileStem("  "))
    }
}
