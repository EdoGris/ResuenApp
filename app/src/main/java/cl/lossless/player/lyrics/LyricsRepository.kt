package cl.lossless.player.lyrics

import android.content.Context
import android.provider.DocumentsContract
import cl.lossless.player.library.LibraryLocation
import cl.lossless.player.library.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics) : LyricsResult
    /** No hay letra. [offline] = no se pudo consultar internet (conviene reintentar). */
    data class NotFound(val searchedOnline: Boolean, val offline: Boolean) : LyricsResult
}

/**
 * Busca la letra de una pista, de lo más fiel a lo menos:
 *  1. Archivo .lrc junto al audio (requiere carpeta elegida con el selector del sistema).
 *  2. Letra incrustada en las etiquetas del archivo.
 *  3. Caché de descargas anteriores.
 *  4. LRCLIB: búsqueda exacta (con duración) y luego aproximada, prefiriendo letra sincronizada.
 */
class LyricsRepository(
    private val context: Context,
    private val client: LrcLibClient = LrcLibClient(),
    private val cache: LyricsCache = LyricsCache(context),
) {

    suspend fun load(
        track: Track,
        location: LibraryLocation?,
        searchOnline: Boolean,
        forceRefresh: Boolean = false,
    ): LyricsResult = withContext(Dispatchers.IO) {
        readSidecar(track, location)?.let { return@withContext LyricsResult.Found(it) }
        readEmbedded(track)?.let { return@withContext LyricsResult.Found(it) }

        val key = cacheKey(track)
        if (forceRefresh) cache.remove(key)
        when (val cached = cache.get(key)) {
            is LyricsCache.Entry.Found -> return@withContext LyricsResult.Found(cached.lyrics)
            LyricsCache.Entry.NotFound -> return@withContext LyricsResult.NotFound(searchedOnline = true, offline = false)
            null -> Unit
        }
        if (!searchOnline) return@withContext LyricsResult.NotFound(searchedOnline = false, offline = false)

        try {
            val found = fetchOnline(track)
            if (found != null) {
                cache.putFound(key, found)
                LyricsResult.Found(found)
            } else {
                cache.putNotFound(key)
                LyricsResult.NotFound(searchedOnline = true, offline = false)
            }
        } catch (e: IOException) {
            LyricsResult.NotFound(searchedOnline = true, offline = true) // no se guarda: se reintentará
        }
    }

    fun clearCache() = cache.clear()

    /** La caché se asocia a la pista (archivo), no al texto de búsqueda. */
    fun cacheKey(track: Track) = track.uri.toString()

    // ---------- Internet ----------

    private fun fetchOnline(track: Track): Lyrics? {
        val (title, artist) = TrackMetadataCleaner.clean(track.title, track.artist)
        if (title.isBlank()) return null
        val durationSec = track.durationMs / 1000

        // 1) Exacta: LRCLIB exige la duración con ±2 s de tolerancia.
        if (artist.isNotBlank() && durationSec > 0) {
            client.get(title, artist, durationSec)?.toLyrics()?.let { return it }
        }
        // 2) Aproximada, eligiendo la mejor coincidencia.
        val results = client.search(title, artist)
        return LrcLibClient.pickBest(results, title, artist, durationSec)?.toLyrics()
    }

    private fun LrcLibClient.Record.toLyrics(): Lyrics? {
        LrcParser.parse(syncedLyrics)?.let { lines ->
            return Lyrics(lines, plainLyrics ?: lines.joinToString("\n") { it.text }, LyricsSource.LRCLIB, instrumental)
        }
        if (!plainLyrics.isNullOrBlank()) return Lyrics(null, plainLyrics.trim(), LyricsSource.LRCLIB, instrumental)
        return if (instrumental) Lyrics(null, null, LyricsSource.LRCLIB, instrumental = true) else null
    }

    // ---------- Archivos locales ----------

    private fun readEmbedded(track: Track): Lyrics? = runCatching {
        context.contentResolver.openFileDescriptor(track.uri, "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                EmbeddedLyricsReader.read(ChannelSource(channel))
            }
        }
    }.getOrNull()

    /** "01 Canción.flac" → busca "01 Canción.lrc" en la misma carpeta. */
    private fun readSidecar(track: Track, location: LibraryLocation?): Lyrics? {
        val loc = location ?: return null
        val folderId = track.folderDocId?.takeIf(loc::contains) ?: return null
        val base = track.fileName.substringBeforeLast('.').takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val docId = context.contentResolver.query(
                loc.childrenUri(folderId),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null,
            )?.use { c ->
                var match: String? = null
                while (c.moveToNext()) {
                    if (c.getString(1).equals("$base.lrc", ignoreCase = true)) { match = c.getString(0); break }
                }
                match
            } ?: return null
            val uri = DocumentsContract.buildDocumentUriUsingTree(loc.treeUri, docId)
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            LrcParser.toLyrics(decodeText(bytes), LyricsSource.SIDECAR)
        }.getOrNull()
    }

    /** Los .lrc suelen ser UTF-8; los antiguos de Windows, Latin-1/CP1252. */
    private fun decodeText(bytes: ByteArray): String =
        try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                .removePrefix("﻿")
        } catch (e: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
}
