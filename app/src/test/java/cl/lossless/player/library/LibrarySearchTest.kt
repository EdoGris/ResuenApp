package cl.lossless.player.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {

    private data class Dir(val name: String, val tracks: Int, val modified: Long)

    private val dirs = listOf(
        Dir("rock", 12, 300),
        Dir("Ambient", 3, 100),
        Dir("Jazz", 12, 200),
    )

    private fun sorted(sort: FolderSort) =
        dirs.sortedFolders(sort, { it.name }, { it.tracks }, { it.modified }).map { it.name }

    @Test
    fun sortsFoldersEveryWay() {
        assertEquals(listOf("Ambient", "Jazz", "rock"), sorted(FolderSort.NAME_ASC))
        assertEquals(listOf("rock", "Jazz", "Ambient"), sorted(FolderSort.NAME_DESC))
        assertEquals(listOf("rock", "Jazz", "Ambient"), sorted(FolderSort.NEWEST))
        assertEquals(listOf("Ambient", "Jazz", "rock"), sorted(FolderSort.OLDEST))
        // Empate en pistas: se ordena por nombre
        assertEquals(listOf("Jazz", "rock", "Ambient"), sorted(FolderSort.MOST_TRACKS))
    }

    @Test
    fun matchesIgnoringCaseAccentsAndWordOrder() {
        assertTrue(LibrarySearch.matches("cancion", "Canción de cuna"))
        assertTrue(LibrarySearch.matches("BEATLES abbey", "Abbey Road", "The Beatles"))
        assertTrue(LibrarySearch.matches("  ", "lo que sea"))
        assertFalse(LibrarySearch.matches("stones", "Abbey Road", "The Beatles"))
    }

    @Test
    fun findsFoldersAtAnyDepthBelowTheCurrentOne() {
        val tracks = listOf(
            listOf("primary", "Music", "Rock", "Live") to 10L,
            listOf("primary", "Music", "Rock", "Live") to 50L,
            listOf("primary", "Music", "Rock") to 20L,
            listOf("primary", "Music", "Jazz") to 5L,
            listOf("1234-ABCD", "Rock") to 7L,
        )
        val all = LibrarySearch.foldersIn(tracks, emptyList(), "rock")
            .associate { it.segments.joinToString("/") to (it.trackCount to it.lastModified) }
        assertEquals(
            mapOf("primary/Music/Rock" to (3 to 50L), "1234-ABCD/Rock" to (1 to 7L)),
            all,
        )

        val live = LibrarySearch.foldersIn(tracks, listOf("primary", "Music"), "live")
        assertEquals(listOf(listOf("primary", "Music", "Rock", "Live")), live.map { it.segments })
        // La carpeta actual no aparece como resultado de sí misma
        assertTrue(LibrarySearch.foldersIn(tracks, listOf("primary", "Music", "Rock"), "rock").isEmpty())
    }
}
