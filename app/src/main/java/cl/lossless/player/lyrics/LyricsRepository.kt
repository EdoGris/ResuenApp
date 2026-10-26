package cl.lossless.player.lyrics

import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import cl.lossless.player.R
import cl.lossless.player.library.LibraryLocation
import cl.lossless.player.library.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

private const val USER_PREFIX = "user:"
private const val ALIGN_POLL_MS = 3_000L
private const val TAG = "Lyrics"

sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics) : LyricsResult
    /**
     * No hay letra. [offline] = no se pudo consultar internet (conviene reintentar); en ese caso
     * [serverError] distingue "LRCLIB respondió con error" de "no hubo respuesta", y [detail]
     * dice qué pasó (p. ej. "HTTP 503" o "UnknownHostException").
     */
    data class NotFound(
        val searchedOnline: Boolean,
        val offline: Boolean,
        val serverError: Boolean = false,
        val detail: String? = null,
    ) : LyricsResult
}

/**
 * Busca la letra de una pista, de lo más fiel a lo menos:
 *  1. Archivo .lrc junto al audio (requiere carpeta elegida con el selector del sistema).
 *  2. Letra incrustada en las etiquetas del archivo.
 *  3. Caché de descargas anteriores.
 *  4. LRCLIB: búsqueda exacta (con duración) y luego aproximada, prefiriendo letra sincronizada.
 *  5. Lyrics.ovh (solo texto) si LRCLIB no la tiene o no responde.
 *
 * Si lo encontrado no trae tiempos, [align] los pide al servidor de alineación (WhisperX) y
 * [estimate] los estima en el teléfono mientras tanto o si no hay servidor.
 */
class LyricsRepository(
    private val context: Context,
    private val client: LrcLibClient = LrcLibClient(),
    private val fallback: LyricsOvhClient = LyricsOvhClient(),
    private val cache: LyricsCache = LyricsCache(context),
) {

    suspend fun load(
        track: Track,
        location: LibraryLocation?,
        searchOnline: Boolean,
        forceRefresh: Boolean = false,
    ): LyricsResult = withContext(Dispatchers.IO) {
        readSidecar(track, location)?.let { return@withContext LyricsResult.Found(it) }
        // Lo sincronizado por el usuario manda sobre la letra del archivo o de internet.
        (cache.get(userKey(track)) as? LyricsCache.Entry.Found)?.let { return@withContext LyricsResult.Found(it.lyrics) }
        readEmbedded(track)?.let { return@withContext LyricsResult.Found(it) }

        val key = cacheKey(track)
        if (forceRefresh) cache.remove(key)
        when (val cached = cache.get(key)) {
            is LyricsCache.Entry.Found -> return@withContext LyricsResult.Found(cached.lyrics)
            LyricsCache.Entry.NotFound -> return@withContext LyricsResult.NotFound(searchedOnline = true, offline = false)
            null -> Unit
        }
        if (!searchOnline) return@withContext LyricsResult.NotFound(searchedOnline = false, offline = false)

        val (title, artist) = TrackMetadataCleaner.clean(track.title, track.artist)
        if (title.isBlank()) return@withContext LyricsResult.NotFound(searchedOnline = true, offline = false)

        // LRCLIB primero (puede traer tiempos); si no tiene nada o falla, Lyrics.ovh.
        var lrclibError: LyricsResult.NotFound? = null
        val fromLrcLib = try {
            fetchLrcLib(title, artist, track.durationMs / 1000)
        } catch (e: LrcLibClient.ServerError) {
            Log.w(TAG, "LRCLIB falló para ${track.uri}", e)
            lrclibError = LyricsResult.NotFound(searchedOnline = true, offline = true, serverError = true, detail = "HTTP ${e.code}")
            null
        } catch (e: IOException) {
            Log.w(TAG, "Sin respuesta de LRCLIB para ${track.uri}", e)
            lrclibError = LyricsResult.NotFound(searchedOnline = true, offline = true, detail = describe(e))
            null
        }
        if (fromLrcLib != null) {
            cache.putFound(key, fromLrcLib)
            return@withContext LyricsResult.Found(fromLrcLib)
        }

        var fallbackFailed = false
        val fromOvh = try {
            fallback.get(artist, title)?.let { Lyrics(null, it, LyricsSource.LYRICS_OVH) }
        } catch (e: IOException) {
            Log.w(TAG, "Lyrics.ovh falló para ${track.uri}", e)
            fallbackFailed = true
            null
        }
        when {
            fromOvh != null -> {
                cache.putFound(key, fromOvh)
                LyricsResult.Found(fromOvh)
            }
            // Algo no respondió: no se guarda "sin letra" para reintentar la próxima vez.
            lrclibError != null -> lrclibError
            fallbackFailed -> LyricsResult.NotFound(searchedOnline = true, offline = false)
            else -> {
                cache.putNotFound(key)
                LyricsResult.NotFound(searchedOnline = true, offline = false)
            }
        }
    }

    /** Borra las letras descargadas y las estimaciones (no las sincronizadas por el usuario). */
    fun clearCache() = cache.clear(keep = { it.startsWith(USER_PREFIX) })

    private fun userKey(track: Track) = USER_PREFIX + track.uri

    // ---------- Sincronización estimada ----------

    /**
     * Tiempos estimados para una letra sin marcas, analizando dónde se canta. El resultado se
     * guarda por pista y por texto (si la letra cambia, se vuelve a estimar).
     */
    suspend fun estimate(track: Track, lyrics: Lyrics): Lyrics? {
        val plain = lyrics.plain ?: return null
        val lines = LyricsAligner.splitLines(plain)
        if (lines.isEmpty()) return null
        val key = "estimate:${track.uri}#${plain.hashCode()}"
        (withContext(Dispatchers.IO) { cache.get(key) } as? LyricsCache.Entry.Found)?.let { return it.lyrics }

        val segments = withContext(Dispatchers.Default) { VocalAnalyzer.analyze(context, track.uri) }.orEmpty()
        val synced = LyricsAligner.align(lines, segments, track.durationMs)
        val result = lyrics.copy(synced = synced, estimated = true)
        withContext(Dispatchers.IO) { cache.putFound(key, result) }
        return result
    }

    // ---------- Alineación en el servidor (WhisperX) ----------

    /** Etapa en curso de una alineación en el servidor. */
    enum class AlignStage { HASHING, UPLOADING, QUEUED, PROCESSING }

    sealed interface AlignOutcome {
        data class Done(val lyrics: Lyrics) : AlignOutcome
        /** Hay que subir el audio y no se permite ahora (datos móviles con "solo Wi-Fi"). */
        data object NeedsUpload : AlignOutcome
        data class Failed(val message: String) : AlignOutcome
    }

    private fun alignedKey(track: Track, plain: String) =
        "aligned:${track.uri}#${AlignmentClient.normalizeLyrics(plain).hashCode()}"

    /** Alineación ya descargada para esta pista y esta letra. */
    suspend fun cachedAlignment(track: Track, lyrics: Lyrics): Lyrics? {
        val plain = lyrics.plain ?: return null
        return (withContext(Dispatchers.IO) { cache.get(alignedKey(track, plain)) } as? LyricsCache.Entry.Found)?.lyrics
    }

    /**
     * Pide al servidor los tiempos exactos (por palabra) de una letra sin marcas. Primero
     * pregunta por el trabajo sin subir nada; si no existe y [allowUpload], sube el audio.
     * Un trabajo que falló solo se reenvía con [retryFailed]. Luego espera a que termine.
     * Lanza IOException si no hay conexión.
     */
    suspend fun align(
        track: Track,
        lyrics: Lyrics,
        client: AlignmentClient,
        allowUpload: Boolean,
        retryFailed: Boolean,
        onStage: (AlignStage) -> Unit,
    ): AlignOutcome = withContext(Dispatchers.IO) {
        val plain = lyrics.plain?.takeIf { it.isNotBlank() } ?: return@withContext AlignOutcome.Failed(context.getString(R.string.lyrics_align_error_empty))
        onStage(AlignStage.HASHING)
        val audioSha = context.contentResolver.openInputStream(track.uri)?.let(AlignmentClient::sha256)
            ?: return@withContext AlignOutcome.Failed(context.getString(R.string.lyrics_align_error_read_audio))
        val id = AlignmentClient.jobId(audioSha, plain)

        val existing = client.get(id)
        if (existing?.status == AlignmentJob.Status.FAILED && !retryFailed) {
            return@withContext AlignOutcome.Failed(existing.error ?: context.getString(R.string.lyrics_align_error_server_failed))
        }
        var job: AlignmentJob = if (existing != null && existing.status != AlignmentJob.Status.FAILED) {
            existing
        } else {
            if (!allowUpload) return@withContext AlignOutcome.NeedsUpload
            onStage(AlignStage.UPLOADING)
            val (title, artist) = TrackMetadataCleaner.clean(track.title, track.artist)
            client.submit(
                audio = { context.contentResolver.openInputStream(track.uri) ?: throw IOException("No se pudo leer el audio") },
                fileName = track.fileName,
                mimeType = track.mimeType,
                lyrics = plain,
                title = title,
                artist = artist,
                durationMs = track.durationMs,
            )
        }
        while (job.pending) {
            onStage(if (job.status == AlignmentJob.Status.QUEUED) AlignStage.QUEUED else AlignStage.PROCESSING)
            delay(ALIGN_POLL_MS)
            job = client.get(id) ?: return@withContext AlignOutcome.Failed(context.getString(R.string.lyrics_align_error_job_deleted))
        }
        val aligned = AlignmentJson.toLyrics(job, plain)
            ?: return@withContext AlignOutcome.Failed(job.error ?: context.getString(R.string.lyrics_align_error_server_failed))
        cache.putFound(alignedKey(track, plain), aligned)
        AlignOutcome.Done(aligned)
    }

    // ---------- Sincronización hecha por el usuario ----------

    /** Dónde quedó guardada la sincronización. */
    enum class SavedTo { FOLDER, APP }

    /**
     * Guarda los tiempos marcados por el usuario. Si la carpeta de música está elegida y se
     * puede escribir, crea/actualiza "<canción>.lrc" junto al audio (lo leerán también otros
     * reproductores); siempre queda además una copia en la app.
     */
    suspend fun saveUserSync(track: Track, location: LibraryLocation?, lines: List<LyricLine>): SavedTo =
        withContext(Dispatchers.IO) {
            val lyrics = Lyrics(lines, lines.joinToString("\n") { it.text }, LyricsSource.USER)
            cache.putFound(userKey(track), lyrics)
            val (title, artist) = TrackMetadataCleaner.clean(track.title, track.artist)
            val written = writeSidecar(track, location, LrcWriter.write(lines, title, artist))
            if (written) SavedTo.FOLDER else SavedTo.APP
        }

    private fun writeSidecar(track: Track, location: LibraryLocation?, lrc: String): Boolean {
        val loc = location ?: return false
        val folderId = track.folderDocId?.takeIf(loc::contains) ?: return false
        val folderUri = loc.documentUri(folderId) ?: return false
        val base = track.fileName.substringBeforeLast('.').takeIf { it.isNotEmpty() } ?: return false
        return runCatching {
            val resolver = context.contentResolver
            val target = findChild(loc, folderId, "$base.lrc")
                ?.let { DocumentsContract.buildDocumentUriUsingTree(loc.treeUri, it) }
                // octet-stream: así el proveedor no agrega ".txt" al nombre
                ?: DocumentsContract.createDocument(resolver, folderUri, "application/octet-stream", "$base.lrc")
                ?: return false
            resolver.openOutputStream(target, "wt")?.use { it.write(lrc.toByteArray(Charsets.UTF_8)) } ?: return false
            true
        }.getOrDefault(false)
    }

    private fun findChild(loc: LibraryLocation, folderId: String, name: String): String? =
        context.contentResolver.query(
            loc.childrenUri(folderId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            var match: String? = null
            while (match == null && c.moveToNext()) {
                if (c.getString(1).equals(name, ignoreCase = true)) match = c.getString(0)
            }
            match
        }

    /** La caché se asocia a la pista (archivo), no al texto de búsqueda. */
    fun cacheKey(track: Track) = track.uri.toString()

    // ---------- Internet ----------

    private fun fetchLrcLib(title: String, artist: String, durationSec: Long): Lyrics? {
        // 1) Exacta: LRCLIB exige la duración con ±2 s de tolerancia.
        // Si falla por un error de LRCLIB se sigue con la búsqueda aproximada, que puede responder.
        if (artist.isNotBlank() && durationSec > 0) {
            try {
                client.get(title, artist, durationSec)?.toLyrics()?.let { return it }
            } catch (e: LrcLibClient.ServerError) {
                // sigue con la búsqueda aproximada
            }
        }
        // 2) Aproximada, eligiendo la mejor coincidencia.
        val results = client.search(title, artist)
        return LrcLibClient.pickBest(results, title, artist, durationSec)?.toLyrics()
    }

    /** "UnknownHostException: lrclib.net" → para mostrar qué falló sin abrir el registro. */
    private fun describe(e: IOException): String =
        e.javaClass.simpleName + (e.message?.takeIf { it.isNotBlank() }?.let { ": ${it.take(80)}" } ?: "")

    private fun LrcLibClient.Record.toLyrics(): Lyrics? {
        LrcParser.parse(syncedLyrics)?.let { lines ->
            return Lyrics(lines, plainLyrics ?: lines.joinToString("\n") { it.text }, LyricsSource.LRCLIB, instrumental)
        }
        val plain = plainLyrics
        if (!plain.isNullOrBlank()) return Lyrics(null, plain.trim(), LyricsSource.LRCLIB, instrumental)
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
            val docId = findChild(loc, folderId, "$base.lrc") ?: return null
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
