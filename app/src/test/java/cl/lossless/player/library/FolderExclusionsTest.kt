package cl.lossless.player.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderExclusionsTest {

    private fun path(p: String) = FolderExclusions.segments(p)

    @Test
    fun excludingAFolderExcludesItsSubfoldersOnly() {
        val excluded = setOf("primary/Android/media/com.whatsapp")
        assertTrue(FolderExclusions.isExcluded(path("primary/Android/media/com.whatsapp"), excluded))
        assertTrue(FolderExclusions.isExcluded(path("primary/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"), excluded))
        assertFalse(FolderExclusions.isExcluded(path("primary/Android/media/com.whatsapp.w4b"), excluded))
        assertFalse(FolderExclusions.isExcluded(path("primary/Music/Rock"), excluded))
    }

    @Test
    fun addingAParentReplacesCoveredChildren() {
        val start = setOf("primary/Music/Rock/Live", "primary/Downloads")
        val result = FolderExclusions.add(start, "primary/Music/Rock")
        assertEquals(setOf("primary/Music/Rock", "primary/Downloads"), result)
    }

    @Test
    fun detectsMessagingAndAppInternalFolders() {
        val folders = listOf(
            path("primary/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"),
            path("primary/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"),
            path("primary/Android/media/org.telegram.messenger/Telegram/Telegram Audio"),
            path("primary/Telegram/Telegram Audio"),
            path("primary/Android/data/com.example.game/files/music"),
            path("primary/Music/Rock"),
        )
        val suggestions = FolderExclusions.suggestionsFor(folders, emptySet()).associateBy { it.key }

        assertEquals("WhatsApp", suggestions.getValue("primary/Android/media/com.whatsapp").appName)
        assertEquals(2, suggestions.getValue("primary/Android/media/com.whatsapp").trackCount)
        assertEquals("Telegram", suggestions.getValue("primary/Android/media/org.telegram.messenger").appName)
        assertEquals("Telegram", suggestions.getValue("primary/Telegram").appName)
        // Paquete desconocido: se muestra el nombre del paquete
        assertEquals("com.example.game", suggestions.getValue("primary/Android/data/com.example.game").appName)
        // La música del usuario nunca se sugiere
        assertFalse(suggestions.keys.any { it.startsWith("primary/Music") })
    }

    @Test
    fun alreadyExcludedFoldersAreNotSuggested() {
        val folders = listOf(path("primary/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
        val suggestions = FolderExclusions.suggestionsFor(folders, setOf("primary/Android"))
        assertTrue(suggestions.isEmpty())
    }
}
