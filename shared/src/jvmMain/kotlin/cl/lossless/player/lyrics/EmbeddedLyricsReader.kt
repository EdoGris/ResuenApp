package cl.lossless.player.lyrics

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/** Lectura por posición: un archivo real (FileChannel) o bytes en memoria (tests). */
interface SeekableSource {
    val size: Long
    /** Lee hasta [length] bytes desde [position] (menos si se acaba el archivo). */
    fun read(position: Long, length: Int): ByteArray
}

class ByteArraySource(private val bytes: ByteArray) : SeekableSource {
    override val size: Long get() = bytes.size.toLong()
    override fun read(position: Long, length: Int): ByteArray {
        if (position >= bytes.size) return ByteArray(0)
        val end = minOf(bytes.size.toLong(), position + length).toInt()
        return bytes.copyOfRange(position.toInt(), end)
    }
}

class ChannelSource(private val channel: FileChannel) : SeekableSource {
    override val size: Long get() = channel.size()
    override fun read(position: Long, length: Int): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        var pos = position
        while (buffer.hasRemaining()) {
            val n = channel.read(buffer, pos)
            if (n <= 0) break
            pos += n
        }
        return buffer.array().copyOf(buffer.position())
    }
}

/**
 * Letras guardadas dentro del archivo de audio:
 *  - MP3: ID3v2.3/2.4, marcos USLT (texto), SYLT (sincronizado) y TXXX "LYRICS".
 *  - FLAC: comentarios Vorbis LYRICS / UNSYNCEDLYRICS / SYNCEDLYRICS.
 *  - M4A/ALAC: átomo ©lyr (moov/udta/meta/ilst).
 * Si el texto viene en formato LRC, se interpreta como letra sincronizada.
 */
object EmbeddedLyricsReader {

    private const val MAX_TAG_BYTES = 32 * 1024 * 1024

    fun read(source: SeekableSource): Lyrics? = runCatching {
        val head = source.read(0, 12)
        when {
            head.startsWith("ID3") -> readId3(source)
            head.startsWith("fLaC") -> readFlac(source)
            head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> readMp4(source)
            else -> null
        }
    }.getOrNull()

    // ---------------------------------------------------------------- ID3v2

    private fun readId3(source: SeekableSource): Lyrics? {
        val header = source.read(0, 10)
        val major = header[3].toInt()
        if (major != 3 && major != 4) return null
        val flags = header[5].toInt()
        val tagSize = syncsafe(header, 6)
        if (tagSize <= 0 || tagSize > MAX_TAG_BYTES) return null
        var tag = source.read(10, tagSize)
        if (major == 3 && flags and 0x80 != 0) tag = unsynchronise(tag)

        var pos = 0
        if (flags and 0x40 != 0) { // cabecera extendida
            pos = if (major == 4) syncsafe(tag, 0) else 4 + int32(tag, 0)
        }
        var unsynced: String? = null
        var synced: List<LyricLine>? = null
        while (pos + 10 <= tag.size) {
            val id = String(tag, pos, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break // relleno
            val frameSize = if (major == 4) syncsafe(tag, pos + 4) else int32(tag, pos + 4)
            val formatFlags = tag[pos + 9].toInt()
            val start = pos + 10
            if (frameSize <= 0 || start + frameSize > tag.size) break
            // v2.3: marcos comprimidos (0x80) o cifrados (0x40) no se interpretan.
            if (major == 3 && formatFlags and 0xC0 != 0) { pos = start + frameSize; continue }
            var data = tag.copyOfRange(start, start + frameSize)
            if (major == 4) {
                if (formatFlags and 0x01 != 0) data = data.copyOfRange(4, data.size) // indicador de longitud
                if (formatFlags and 0x02 != 0) data = unsynchronise(data)
            }
            when (id) {
                "USLT" -> if (unsynced == null) unsynced = parseUslt(data)
                "SYLT" -> if (synced == null) synced = parseSylt(data)
                "TXXX" -> if (unsynced == null) unsynced = parseTxxxLyrics(data)
            }
            pos = start + frameSize
        }
        if (!synced.isNullOrEmpty()) {
            return Lyrics(synced, synced.joinToString("\n") { it.text }, LyricsSource.EMBEDDED)
        }
        return unsynced?.let { LrcParser.toLyrics(it, LyricsSource.EMBEDDED) }
    }

    /** USLT: codificación(1) idioma(3) descriptor(terminado) texto. */
    private fun parseUslt(data: ByteArray): String? {
        if (data.size < 5) return null
        val enc = data[0].toInt()
        val descEnd = terminator(data, 4, enc)
        return decode(data, descEnd + terminatorLength(enc), data.size, enc).trim().ifEmpty { null }
    }

    /** SYLT: codificación(1) idioma(3) formato(1) tipo(1) descriptor, luego [texto, tiempo(4)]… */
    private fun parseSylt(data: ByteArray): List<LyricLine>? {
        if (data.size < 7) return null
        val enc = data[0].toInt()
        if (data[4].toInt() != 2) return null // solo marcas en milisegundos (no en cuadros MPEG)
        var pos = terminator(data, 6, enc) + terminatorLength(enc)
        val lines = mutableListOf<LyricLine>()
        while (pos < data.size) {
            val end = terminator(data, pos, enc)
            val text = decode(data, pos, end, enc).trim()
            pos = end + terminatorLength(enc)
            if (pos + 4 > data.size) break
            val time = int32(data, pos).toLong() and 0xFFFFFFFFL
            pos += 4
            if (text.isNotEmpty()) lines += LyricLine(time, text)
        }
        return lines.sortedBy { it.timeMs }.ifEmpty { null }
    }

    /** TXXX con descripción LYRICS / UNSYNCEDLYRICS (lo usan algunos etiquetadores). */
    private fun parseTxxxLyrics(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val enc = data[0].toInt()
        val descEnd = terminator(data, 1, enc)
        val description = decode(data, 1, descEnd, enc).trim().uppercase()
        if (description != "LYRICS" && description != "UNSYNCEDLYRICS") return null
        return decode(data, descEnd + terminatorLength(enc), data.size, enc).trim().ifEmpty { null }
    }

    private fun terminatorLength(enc: Int) = if (enc == 1 || enc == 2) 2 else 1

    /** Posición del terminador de cadena desde [from] (o el final de los datos). */
    private fun terminator(data: ByteArray, from: Int, enc: Int): Int {
        if (enc == 1 || enc == 2) {
            var i = from
            while (i + 1 < data.size) {
                if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) return i
                i += 2
            }
            return data.size
        }
        for (i in from until data.size) if (data[i].toInt() == 0) return i
        return data.size
    }

    private fun decode(data: ByteArray, from: Int, to: Int, enc: Int): String {
        if (from >= to) return ""
        val charset: Charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16 // con BOM
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(data, from, to - from, charset).trimEnd('\u0000')
    }

    /** Deshace la "desincronización" de ID3: FF 00 → FF. */
    private fun unsynchronise(data: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(data.size)
        var i = 0
        while (i < data.size) {
            out.write(data[i].toInt())
            if (data[i] == 0xFF.toByte() && i + 1 < data.size && data[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- FLAC

    private fun readFlac(source: SeekableSource): Lyrics? {
        var pos = 4L
        while (pos + 4 <= source.size) {
            val header = source.read(pos, 4)
            if (header.size < 4) return null
            val last = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
            pos += 4
            if (type == 4 && length in 1..MAX_TAG_BYTES) return parseVorbisComments(source.read(pos, length))
            if (last) return null
            pos += length
        }
        return null
    }

    private fun parseVorbisComments(block: ByteArray): Lyrics? {
        var pos = 0
        val vendorLength = int32le(block, pos); pos += 4 + vendorLength
        val count = int32le(block, pos); pos += 4
        val values = mutableMapOf<String, String>()
        repeat(count) {
            if (pos + 4 > block.size) return@repeat
            val len = int32le(block, pos); pos += 4
            if (len < 0 || pos + len > block.size) return@repeat
            val comment = String(block, pos, len, Charsets.UTF_8); pos += len
            val eq = comment.indexOf('=')
            if (eq > 0) values.putIfAbsent(comment.substring(0, eq).uppercase(), comment.substring(eq + 1))
        }
        val text = values["SYNCEDLYRICS"] ?: values["LYRICS"] ?: values["UNSYNCEDLYRICS"] ?: return null
        return LrcParser.toLyrics(text, LyricsSource.EMBEDDED)
    }

    // ---------------------------------------------------------------- MP4 / M4A

    private fun readMp4(source: SeekableSource): Lyrics? {
        val moov = findAtom(source, 0, source.size, "moov") ?: return null
        val udta = findAtom(source, moov.first, moov.second, "udta") ?: return null
        val meta = findAtom(source, udta.first, udta.second, "meta") ?: return null
        // "meta" es una caja completa: 4 bytes de versión/flags antes de sus hijos.
        val ilst = findAtom(source, meta.first + 4, meta.second, "ilst") ?: return null
        val lyr = findAtom(source, ilst.first, ilst.second, "©lyr") ?: return null
        val data = findAtom(source, lyr.first, lyr.second, "data") ?: return null
        val length = (data.second - data.first - 8).toInt() // tipo(4) + configuración regional(4)
        if (length !in 1..MAX_TAG_BYTES) return null
        val text = String(source.read(data.first + 8, length), Charsets.UTF_8)
        return LrcParser.toLyrics(text, LyricsSource.EMBEDDED)
    }

    /** Busca un átomo hijo entre [start, end); devuelve el rango de su contenido. */
    private fun findAtom(source: SeekableSource, start: Long, end: Long, type: String): Pair<Long, Long>? {
        var pos = start
        while (pos + 8 <= end) {
            val header = source.read(pos, 16)
            if (header.size < 8) return null
            var size = int32(header, 0).toLong() and 0xFFFFFFFFL
            val name = String(header, 4, 4, Charsets.ISO_8859_1)
            var headerSize = 8
            if (size == 1L && header.size >= 16) { // tamaño de 64 bits
                size = ByteBuffer.wrap(header, 8, 8).long
                headerSize = 16
            } else if (size == 0L) {
                size = end - pos // hasta el final
            }
            if (size < headerSize) return null
            if (name == type) return (pos + headerSize) to minOf(pos + size, end)
            pos += size
        }
        return null
    }

    // ---------------------------------------------------------------- utilidades

    private fun ByteArray.startsWith(prefix: String) =
        size >= prefix.length && String(this, 0, prefix.length, Charsets.ISO_8859_1) == prefix

    private fun syncsafe(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0x7F) shl 21) or ((b[at + 1].toInt() and 0x7F) shl 14) or
            ((b[at + 2].toInt() and 0x7F) shl 7) or (b[at + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun int32le(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}
