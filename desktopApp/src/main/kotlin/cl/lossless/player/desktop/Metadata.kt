package cl.lossless.player.desktop

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/** Campos que se pueden editar a mano, en el orden en que aparecen en el editor. */
enum class TagField(val key: FieldKey, val perTrack: Boolean = false, val numeric: Boolean = false) {
    TITLE(FieldKey.TITLE, perTrack = true),
    ARTIST(FieldKey.ARTIST),
    ALBUM(FieldKey.ALBUM),
    ALBUM_ARTIST(FieldKey.ALBUM_ARTIST),
    YEAR(FieldKey.YEAR, numeric = true),
    GENRE(FieldKey.GENRE),
    TRACK(FieldKey.TRACK, perTrack = true, numeric = true),
    DISC(FieldKey.DISC_NO, numeric = true),
}

/** Lee y escribe las etiquetas de los archivos con jaudiotagger (FLAC, MP3, M4A/ALAC, WAV, OGG). */
object TagEditor {

    /** Valor de cada campo en cada archivo. Bloqueante. */
    fun read(track: Track): Map<TagField, String> {
        val tag = runCatching { AudioFileIO.read(track.file).tag }.getOrNull()
        return TagField.entries.associateWith { f ->
            tag?.let { runCatching { it.getFirst(f.key) }.getOrNull() }?.trim().orEmpty()
        }
    }

    /**
     * Escribe [changes] en cada archivo (un valor vacío borra el campo) y devuelve las pistas
     * releídas. Bloqueante; el archivo no puede estar abierto (ver [Player.withFilesReleased]).
     */
    fun write(tracks: List<Track>, changes: Map<TagField, String>): List<Track> = tracks.map { track ->
        val audio = AudioFileIO.read(track.file)
        val tag = audio.tagOrCreateAndSetDefault
        for ((field, value) in changes) {
            val v = value.trim()
            if (v.isEmpty()) runCatching { tag.deleteField(field.key) } else tag.setField(field.key, v)
        }
        audio.commit()
        LibraryScanner.readTrack(track.file.toPath())
    }

    /** Incrusta la carátula [bytes] (JPEG o PNG) en cada archivo, en lugar de la que tuviera. */
    fun writeArtwork(tracks: List<Track>, bytes: ByteArray): List<Track> = tracks.map { track ->
        val audio = AudioFileIO.read(track.file)
        val tag = audio.tagOrCreateAndSetDefault
        val art = ArtworkFactory.getNew().apply {
            binaryData = bytes
            mimeType = if (bytes.size > 3 && bytes[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
            pictureType = 3 // portada
            runCatching { setImageFromData() } // ancho y alto, que FLAC guarda junto a la imagen
        }
        runCatching { tag.deleteArtworkField() }
        tag.setField(art)
        audio.commit()
        LibraryScanner.readTrack(track.file.toPath())
    }
}

/** Una carátula encontrada en internet. */
data class CoverCandidate(val source: String, val album: String, val artist: String, val thumbUrl: String, val fullUrl: String)

/** Busca carátulas en iTunes y Deezer (ambos sin clave) y las descarga. */
object CoverSearch {

    private const val USER_AGENT = "ResuenApp/1.2 (desktop lossless music player)"

    /** Bloqueante. Lanza IOException si no hay conexión con ninguno de los dos. */
    fun search(artist: String, album: String): List<CoverCandidate> {
        val term = listOf(artist, album).filter { it.isNotBlank() }.joinToString(" ")
        if (term.isBlank()) return emptyList()
        val q = URLEncoder.encode(term, Charsets.UTF_8)
        var failures = 0
        val itunes = try { itunes(q) } catch (e: IOException) { failures++; emptyList() }
        val deezer = try { deezer(q) } catch (e: IOException) { failures++; emptyList() }
        if (failures == 2) throw IOException("sin conexión")
        // Intercaladas: así las primeras de cada fuente quedan arriba.
        val merged = mutableListOf<CoverCandidate>()
        for (i in 0 until maxOf(itunes.size, deezer.size)) {
            itunes.getOrNull(i)?.let(merged::add)
            deezer.getOrNull(i)?.let(merged::add)
        }
        return merged.distinctBy { it.fullUrl }
    }

    private fun itunes(q: String): List<CoverCandidate> {
        val json = JSONObject(get("https://itunes.apple.com/search?term=$q&media=music&entity=album&limit=12").decodeToString())
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.getJSONObject(i)
            val thumb = o.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CoverCandidate(
                source = "iTunes",
                album = o.optString("collectionName"),
                artist = o.optString("artistName"),
                thumbUrl = thumb.replace("100x100bb", "300x300bb"),
                fullUrl = thumb.replace("100x100bb", "1200x1200bb"),
            )
        }
    }

    private fun deezer(q: String): List<CoverCandidate> {
        val json = JSONObject(get("https://api.deezer.com/search/album?q=$q&limit=12").decodeToString())
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val o = data.getJSONObject(i)
            val full = o.optString("cover_xl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CoverCandidate(
                source = "Deezer",
                album = o.optString("title"),
                artist = o.optJSONObject("artist")?.optString("name").orEmpty(),
                thumbUrl = o.optString("cover_medium").ifBlank { full },
                fullUrl = full,
            )
        }
    }

    /** Bloqueante. */
    fun download(url: String): ByteArray = get(url)

    private fun get(url: String): ByteArray {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IOException("HTTP $code")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
