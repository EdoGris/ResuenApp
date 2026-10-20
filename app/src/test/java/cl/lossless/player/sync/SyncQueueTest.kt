package cl.lossless.player.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncQueueTest {

    private fun cover(key: String) = SyncRequest.Cover(key, "Álbum $key", "Artista", "primary:Music/$key", true)

    private fun lyrics(uri: String) = SyncRequest.Lyrics(
        trackUri = uri, title = "Canción", artist = "Artista", album = "Álbum", durationMs = 180_000,
        mimeType = "audio/flac", fileName = "01 Canción.flac", folderKey = "f", folderName = "Álbum", folderDocId = null,
    )

    private fun ids(items: List<SyncItem>) = items.map { it.id }

    @Test
    fun enqueueAppendsWithoutDuplicates() {
        var items = SyncQueueOps.enqueue(emptyList(), listOf(cover("a"), cover("b")))
        items = SyncQueueOps.enqueue(items, listOf(cover("b"), cover("c")))
        assertEquals(listOf("cover:a", "cover:b", "cover:c"), ids(items))
    }

    @Test
    fun urgentGoesFirstButAfterTheRunningOne() {
        var items = SyncQueueOps.enqueue(emptyList(), listOf(cover("a"), cover("b"), lyrics("x")))
        items = items.map { if (it.id == "cover:a") it.copy(running = true) else it }
        items = SyncQueueOps.enqueue(items, listOf(lyrics("x")), urgent = true, retryFailed = true)
        assertEquals(listOf("cover:a", "lyrics:x", "cover:b"), ids(items))
        assertTrue(items[1].retryFailed)
    }

    @Test
    fun nextSkipsRunningAndSkipped() {
        val items = SyncQueueOps.enqueue(emptyList(), listOf(cover("a"), cover("b"), cover("c")))
            .map { if (it.id == "cover:a") it.copy(running = true) else it }
        assertEquals("cover:b", SyncQueueOps.next(items)?.id)
        assertEquals("cover:c", SyncQueueOps.next(items, skip = setOf("cover:b"))?.id)
        assertNull(SyncQueueOps.next(items, skip = setOf("cover:b", "cover:c")))
    }

    @Test
    fun requeueMovesToTheEndAndGivesUpAfterMaxAttempts() {
        var items = SyncQueueOps.enqueue(emptyList(), listOf(cover("a"), cover("b")))
        val (moved, kept) = SyncQueueOps.requeue(items, "cover:a")
        assertTrue(kept)
        assertEquals(listOf("cover:b", "cover:a"), ids(moved))
        assertEquals(1, moved.last().attempts)

        items = moved
        repeat(SyncQueueOps.MAX_ATTEMPTS - 2) { items = SyncQueueOps.requeue(items, "cover:a").first }
        val (last, stillKept) = SyncQueueOps.requeue(items, "cover:a")
        assertFalse(stillKept)
        assertEquals(listOf("cover:b"), ids(last))
    }

    @Test
    fun jsonRoundTripKeepsRequestsAndResetsRunning() {
        val items = SyncQueueOps.enqueue(emptyList(), listOf(cover("a"), lyrics("content://x/1")), retryFailed = true)
            .map { if (it.id == "cover:a") it.copy(running = true, stage = "UPLOADING", attempts = 2) else it }
        val restored = SyncQueueOps.fromJson(SyncQueueOps.toJson(items))
        assertEquals(items.map { it.request }, restored.map { it.request })
        assertFalse(restored.any { it.running })
        assertEquals(2, restored.first().attempts)
        assertTrue(restored.all { it.retryFailed })
        assertNull((restored.last().request as SyncRequest.Lyrics).folderDocId)
    }
}
