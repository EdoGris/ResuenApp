package cl.lossless.player.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LrcParserTest {

    @Test
    fun parsesTimestampsAndSortsLines() {
        val lines = LrcParser.parse(
            """
            [ar:Artista]
            [00:12.50]Segunda
            [00:01.2]Primera
            [01:02]Tercera
            [00:20.00]
            """.trimIndent()
        )!!
        assertEquals(listOf(1_200L, 12_500L, 62_000L), lines.map { it.timeMs })
        assertEquals(listOf("Primera", "Segunda", "Tercera"), lines.map { it.text })
    }

    @Test
    fun repeatedTimestampsOnOneLineProduceOneEntryEach() {
        val lines = LrcParser.parse("[00:10.00][00:50.00]Estribillo")!!
        assertEquals(listOf(10_000L, 50_000L), lines.map { it.timeMs })
    }

    @Test
    fun appliesOffsetTagAndStripsWordTimestamps() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00]<00:10.00>Hola <00:10.50>mundo")!!
        assertEquals(9_500L, lines.single().timeMs)
        assertEquals("Hola mundo", lines.single().text)
    }

    @Test
    fun dropsADuplicatedSecondPassButKeepsRealRepeats() {
        val restart = listOf(
            LyricLine(1_000, "a"), LyricLine(90_000, "b"), LyricLine(180_000, "c"),
            LyricLine(500, "a romaji"), LyricLine(90_500, "b romaji"),
        )
        assertEquals(3, LrcParser.dropDuplicatedSecondPass(restart).size)

        val chorus = (0 until 40).map { LyricLine(it * 5_000L, "Around the world") }
        assertEquals(40, LrcParser.dropDuplicatedSecondPass(chorus).size)
    }

    @Test
    fun plainTextIsNotSynced() {
        assertNull(LrcParser.parse("Solo texto\nsin marcas"))
        val lyrics = LrcParser.toLyrics("Solo texto\nsin marcas", LyricsSource.EMBEDDED)!!
        assertFalse(lyrics.isSynced)
        assertEquals("Solo texto\nsin marcas", lyrics.plain)
    }

    @Test
    fun lineIndexAtFindsTheCurrentLine() {
        val lyrics = Lyrics(listOf(LyricLine(1_000, "a"), LyricLine(5_000, "b"), LyricLine(9_000, "c")), null, LyricsSource.LRCLIB)
        assertEquals(-1, lyrics.lineIndexAt(500))
        assertEquals(0, lyrics.lineIndexAt(1_000))
        assertEquals(1, lyrics.lineIndexAt(8_999))
        assertEquals(2, lyrics.lineIndexAt(60_000))
    }
}

class TrackMetadataCleanerTest {

    @Test
    fun splitsArtistFromTitleWhenArtistIsAPlaceholder() {
        // Caso real de la biblioteca: título "Artista - Canción" y artista "Desconocido".
        val c = TrackMetadataCleaner.clean("Rage Against The Machine - Bombtrack", "Desconocido")
        assertEquals("Bombtrack", c.title)
        assertEquals("Rage Against The Machine", c.artist)
    }

    @Test
    fun keepsATrustedArtistAndTitlesWithColons() {
        val c = TrackMetadataCleaner.clean("THE HERO !!: Ikareru Kobushi ni Hi o Tsukero", "JAM Project")
        assertEquals("THE HERO !!: Ikareru Kobushi ni Hi o Tsukero", c.title)
        assertEquals("JAM Project", c.artist)
    }

    @Test
    fun doesNotSplitHyphenatedWords() {
        assertNull(TrackMetadataCleaner.extractArtistFromTitle("Bling-Bang-Bang-Born"))
    }

    @Test
    fun stripsJunkAnnotationsButNotRealParentheticals() {
        assertEquals("Curao manejo mejor!", TrackMetadataCleaner.clean("Los Mox: Curao manejo mejor! (letra)", "neohex").title)
        assertEquals("Los Mox", TrackMetadataCleaner.clean("Los Mox: Curao manejo mejor! (letra)", "neohex").artist)
        assertEquals("Song (Live Aid)", TrackMetadataCleaner.stripJunkAnnotations("Song (Live Aid)"))
        assertEquals("Song", TrackMetadataCleaner.stripJunkAnnotations("Song (Official Video) [HD]"))
    }

    @Test
    fun removesLeadingTrackNumbersFromFileNameTitles() {
        assertEquals("Intro", TrackMetadataCleaner.clean("01. Intro", "Band").title)
        assertEquals("99 Luftballons", TrackMetadataCleaner.clean("99 Luftballons", "Nena").title)
    }

    @Test
    fun placeholderArtistIsNotSentAsArtist() {
        assertEquals("", TrackMetadataCleaner.clean("Bombtrack", "Desconocido").artist)
        assertTrue(TrackMetadataCleaner.isUntrustedArtist("neohex"))
        assertFalse(TrackMetadataCleaner.isUntrustedArtist("Los Mox"))
    }
}

class LrcLibPickTest {

    private fun record(track: String, artist: String, duration: Double, synced: Boolean, plain: Boolean = true) =
        LrcLibClient.Record(track, artist, "", duration, false, if (plain) "texto" else null, if (synced) "[00:01.00]x" else null)

    @Test
    fun prefersMatchingSyncedResult() {
        val best = LrcLibClient.pickBest(
            listOf(
                record("Bombtrack", "Rage Against the Machine", 244.0, synced = false),
                record("Bombtrack", "Rage Against the Machine", 244.0, synced = true),
                record("Bulls on Parade", "Rage Against the Machine", 230.0, synced = true),
            ),
            "Bombtrack", "Rage Against The Machine", 245,
        )
        assertNotNull(best)
        assertEquals("[00:01.00]x", best!!.syncedLyrics)
    }

    @Test
    fun rejectsUnrelatedResults() {
        val best = LrcLibClient.pickBest(listOf(record("Otra cosa", "Otro", 200.0, synced = true)), "Bombtrack", "Rage Against The Machine", 245)
        assertNull(best)
    }
}

class EmbeddedLyricsReaderTest {

    // ---------- ID3v2.4 ----------

    private fun syncsafe(n: Int) = byteArrayOf(((n shr 21) and 0x7F).toByte(), ((n shr 14) and 0x7F).toByte(), ((n shr 7) and 0x7F).toByte(), (n and 0x7F).toByte())

    private fun id3Frame(id: String, data: ByteArray) = id.toByteArray(Charsets.ISO_8859_1) + syncsafe(data.size) + byteArrayOf(0, 0) + data

    private fun id3(vararg frames: ByteArray): ByteArray {
        val body = frames.fold(ByteArray(0)) { acc, f -> acc + f } + ByteArray(32) // relleno
        return "ID3".toByteArray() + byteArrayOf(4, 0, 0) + syncsafe(body.size) + body + ByteArray(64) // + "audio"
    }

    @Test
    fun readsId3UnsyncedLyricsInUtf8() {
        val uslt = byteArrayOf(3) + "spa".toByteArray() + byteArrayOf(0) + "Línea uno\nLínea dos".toByteArray(Charsets.UTF_8)
        val lyrics = EmbeddedLyricsReader.read(ByteArraySource(id3(id3Frame("TIT2", byteArrayOf(3, 65)), id3Frame("USLT", uslt))))!!
        assertFalse(lyrics.isSynced)
        assertEquals("Línea uno\nLínea dos", lyrics.plain)
        assertEquals(LyricsSource.EMBEDDED, lyrics.source)
    }

    @Test
    fun readsId3LrcFormattedLyricsAsSynced() {
        val uslt = byteArrayOf(3) + "eng".toByteArray() + byteArrayOf(0) + "[00:01.00]Hola\n[00:03.50]Chao".toByteArray()
        val lyrics = EmbeddedLyricsReader.read(ByteArraySource(id3(id3Frame("USLT", uslt))))!!
        assertTrue(lyrics.isSynced)
        assertEquals(listOf(1_000L, 3_500L), lyrics.synced!!.map { it.timeMs })
    }

    @Test
    fun readsId3SyltInUtf16() {
        fun utf16(s: String) = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + s.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        fun ms(n: Int) = ByteBuffer.allocate(4).putInt(n).array()
        val sylt = byteArrayOf(1) + "spa".toByteArray() + byteArrayOf(2, 1) + utf16("") +
            utf16("Uno") + ms(1_000) + utf16("Dos") + ms(2_500)
        val lyrics = EmbeddedLyricsReader.read(ByteArraySource(id3(id3Frame("SYLT", sylt))))!!
        assertEquals(listOf(LyricLine(1_000, "Uno"), LyricLine(2_500, "Dos")), lyrics.synced)
    }

    // ---------- FLAC ----------

    @Test
    fun readsFlacVorbisLyrics() {
        fun le(n: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array()
        fun comment(s: String) = s.toByteArray(Charsets.UTF_8).let { le(it.size) + it }
        val vendor = "test".toByteArray()
        val vorbis = le(vendor.size) + vendor + le(2) + comment("TITLE=Canción") + comment("LYRICS=[00:02.00]Hola")
        fun block(type: Int, last: Boolean, data: ByteArray) =
            byteArrayOf(((if (last) 0x80 else 0) or type).toByte(), (data.size shr 16).toByte(), (data.size shr 8).toByte(), data.size.toByte()) + data
        val flac = "fLaC".toByteArray() + block(0, false, ByteArray(34)) + block(6, false, ByteArray(500)) + block(4, true, vorbis)
        val lyrics = EmbeddedLyricsReader.read(ByteArraySource(flac))!!
        assertEquals(listOf(LyricLine(2_000, "Hola")), lyrics.synced)
    }

    // ---------- MP4 ----------

    private fun atom(type: String, vararg children: ByteArray): ByteArray {
        val body = children.fold(ByteArray(0)) { acc, c -> acc + c }
        return ByteBuffer.allocate(4).putInt(8 + body.size).array() + type.toByteArray(Charsets.ISO_8859_1) + body
    }

    @Test
    fun readsMp4LyricsAtomEvenAfterMediaData() {
        val data = atom("data", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 0) + "Letra simple".toByteArray())
        val ilst = atom("ilst", atom("©nam", atom("data", ByteArray(8) + "T".toByteArray())), atom("©lyr", data))
        val meta = atom("meta", ByteArray(4), atom("hdlr", ByteArray(25)), ilst)
        val file = ByteArrayOutputStream().apply {
            write(atom("ftyp", "M4A ".toByteArray(), ByteArray(4)))
            write(atom("mdat", ByteArray(1_000)))
            write(atom("moov", atom("mvhd", ByteArray(100)), atom("udta", meta)))
        }.toByteArray()
        val lyrics = EmbeddedLyricsReader.read(ByteArraySource(file))!!
        assertEquals("Letra simple", lyrics.plain)
    }

    @Test
    fun unknownFormatReturnsNull() {
        assertNull(EmbeddedLyricsReader.read(ByteArraySource(ByteArray(100))))
    }
}
