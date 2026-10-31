package cl.lossless.player.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AlbumGroupingTest {

    private data class T(val folder: String, val album: String, val artist: String, val albumArtist: String = "")

    private fun keys(vararg items: T) =
        AlbumGrouping.keys(items.toList()) { AlbumGrouping.Fields(it.folder, it.album, it.albumArtist, it.artist) }

    @Test
    fun sameAlbumAndArtistInDifferentFoldersIsOneAlbum() {
        val k = keys(T("/a", "Mix", "Foo"), T("/b", "mix ", "FOO"), T("/c", "Mix", "Foo"))
        assertEquals(1, k.distinct().size)
    }

    @Test
    fun albumArtistWinsOverTrackArtists() {
        val k = keys(
            T("/a", "Duetos", "Foo", albumArtist = "Varios"),
            T("/b", "Duetos", "Bar", albumArtist = "Varios"),
        )
        assertEquals(k[0], k[1])
    }

    @Test
    fun sameAlbumNameByDifferentArtistsStaysApart() {
        val k = keys(T("/a", "Greatest Hits", "Queen"), T("/b", "Greatest Hits", "ABBA"))
        assertNotEquals(k[0], k[1])
    }

    @Test
    fun compilationWithoutAlbumArtistStaysInItsFolder() {
        val k = keys(
            T("/a", "Hits 90", "Foo"), T("/a", "Hits 90", "Bar"),
            T("/b", "Hits 90", "Baz"), T("/b", "Hits 90", "Qux"),
        )
        assertEquals(k[0], k[1])
        assertEquals(k[2], k[3])
        assertNotEquals(k[0], k[2])
    }

    @Test
    fun tracksWithoutAlbumAreGroupedByFolder() {
        val k = keys(T("/a", "", "Foo"), T("/a", "", "Foo"), T("/b", "", "Foo"))
        assertEquals(k[0], k[1])
        assertNotEquals(k[0], k[2])
    }

    @Test
    fun groupKeepsFirstAppearanceOrder() {
        val items = listOf(T("/b", "B", "x"), T("/a", "A", "y"), T("/c", "B", "x"))
        val groups = AlbumGrouping.group(items) { AlbumGrouping.Fields(it.folder, it.album, it.albumArtist, it.artist) }
        assertEquals(listOf(2, 1), groups.map { it.second.size })
    }
}
