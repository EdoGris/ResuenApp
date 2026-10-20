package cl.lossless.player.sync

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import cl.lossless.player.R
import cl.lossless.player.covers.CoverManager
import cl.lossless.player.covers.CoverStore
import cl.lossless.player.library.AlbumGroup
import cl.lossless.player.library.LibraryPrefs
import cl.lossless.player.library.Track
import cl.lossless.player.lyrics.AlignmentClient
import cl.lossless.player.lyrics.LyricsPrefs
import cl.lossless.player.lyrics.LyricsRepository
import cl.lossless.player.lyrics.LyricsResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

/**
 * Procesa la [SyncQueue] de a una petición. WorkManager lo lanza solo con una red permitida
 * y lo detiene si esa red se pierde; lo que estaba en curso vuelve a la cola.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val queue = SyncQueue.get(applicationContext)
        queue.resetRunning()
        val runner = SyncRunner(applicationContext)
        val started = SystemClock.elapsedRealtime()
        // Peticiones que fallaron por la red en esta pasada: no se reintentan hasta la próxima.
        val networkFailed = mutableSetOf<String>()
        while (true) {
            // Android corta los trabajos a los 10 minutos: se sigue en uno nuevo antes.
            if (SystemClock.elapsedRealtime() - started > MAX_RUN_MS) {
                queue.resume(ExistingWorkPolicy.APPEND_OR_REPLACE)
                return Result.success()
            }
            val item = queue.takeNext(networkFailed)
                ?: return if (networkFailed.isEmpty()) Result.success() else Result.retry()
            val result = try {
                runner.run(item) { stage -> queue.setStage(item.id, stage) }
            } catch (e: CancellationException) {
                queue.resetRunning()
                throw e
            }
            when (result) {
                is SyncRunner.Result.Finished -> queue.finish(item, result.outcome, result.message)
                SyncRunner.Result.NetworkError -> {
                    networkFailed += item.id
                    queue.requeue(item)
                }
            }
        }
    }

    private companion object {
        const val MAX_RUN_MS = 8 * 60 * 1000L
    }
}

/** Ejecuta una petición de la cola. */
internal class SyncRunner(private val context: Context) {

    sealed interface Result {
        data class Finished(val outcome: SyncOutcome, val message: String? = null) : Result
        /** Sin conexión o el servidor no responde: se reintenta más tarde. */
        data object NetworkError : Result
    }

    private val coverManager = CoverManager(context)
    private val coverStore = CoverStore(context)
    private val libraryPrefs = LibraryPrefs(context)
    private val lyricsRepository = LyricsRepository(context)
    private val lyricsPrefs = LyricsPrefs(context)

    suspend fun run(item: SyncItem, onStage: (String?) -> Unit): Result = when (val request = item.request) {
        is SyncRequest.Cover -> cover(request)
        is SyncRequest.Lyrics -> lyrics(request, item.retryFailed, onStage)
    }

    private suspend fun cover(request: SyncRequest.Cover): Result = withContext(Dispatchers.IO) {
        // Se asignó a mano (o ya se encontró) mientras esperaba en la cola.
        if (coverStore.get(request.albumKey) != null) return@withContext Result.Finished(SyncOutcome.NOT_NEEDED)
        val album = AlbumGroup(
            key = request.albumKey,
            title = request.title,
            artist = request.artist,
            folderName = "",
            folderDocId = request.folderDocId,
            singleAlbumFolder = request.singleAlbumFolder,
            tracks = emptyList(),
        )
        try {
            val best = coverManager.bestAutomatic(coverManager.searchOnline(album))
                ?: return@withContext Result.Finished(SyncOutcome.NO_MATCH, context.getString(R.string.settings_sync_error_no_cover_match))
            coverManager.apply(album, best, libraryPrefs.location, libraryPrefs.saveCoversInFolder)
            Result.Finished(SyncOutcome.DONE)
        } catch (e: IOException) {
            Result.NetworkError
        } catch (e: Exception) {
            Result.Finished(SyncOutcome.FAILED, e.message)
        }
    }

    private suspend fun lyrics(request: SyncRequest.Lyrics, retryFailed: Boolean, onStage: (String?) -> Unit): Result {
        val track = Track(
            id = 0,
            uri = Uri.parse(request.trackUri),
            title = request.title,
            artist = request.artist,
            album = request.album,
            albumArtist = "",
            trackNumber = 0,
            durationMs = request.durationMs,
            mimeType = request.mimeType,
            folderKey = request.folderKey,
            folderName = request.folderName,
            folderDocId = request.folderDocId,
            fileName = request.fileName,
        )
        val location = withContext(Dispatchers.IO) { libraryPrefs.location }
        val lyrics = when (val result = lyricsRepository.load(track, location, lyricsPrefs.searchOnline)) {
            is LyricsResult.Found -> result.lyrics
            is LyricsResult.NotFound ->
                return if (result.offline) Result.NetworkError else Result.Finished(SyncOutcome.NO_MATCH, context.getString(R.string.settings_sync_error_no_lyrics))
        }
        if (lyrics.isSynced || lyrics.plain.isNullOrBlank()) return Result.Finished(SyncOutcome.NOT_NEEDED)

        val serverUrl = lyricsPrefs.alignServerUrl
        if (serverUrl.isBlank()) {
            // Sin servidor: tiempos estimados en el teléfono (quedan guardados para cuando se abra).
            onStage(STAGE_ESTIMATING)
            return if (lyricsRepository.estimate(track, lyrics) != null) Result.Finished(SyncOutcome.DONE)
            else Result.Finished(SyncOutcome.FAILED, context.getString(R.string.settings_sync_error_estimate_failed))
        }
        if (lyricsRepository.cachedAlignment(track, lyrics) != null) return Result.Finished(SyncOutcome.NOT_NEEDED)
        return try {
            val client = AlignmentClient(serverUrl, lyricsPrefs.alignToken)
            // La red ya es una de las permitidas (WorkManager no corre el trabajo si no).
            when (val outcome = lyricsRepository.align(track, lyrics, client, allowUpload = true, retryFailed = retryFailed) {
                onStage(it.name)
            }) {
                is LyricsRepository.AlignOutcome.Done -> Result.Finished(SyncOutcome.DONE)
                is LyricsRepository.AlignOutcome.Failed -> Result.Finished(SyncOutcome.FAILED, outcome.message)
                LyricsRepository.AlignOutcome.NeedsUpload -> Result.NetworkError
            }
        } catch (e: AlignmentClient.Unauthorized) {
            Result.Finished(SyncOutcome.FAILED, context.getString(R.string.settings_sync_error_token_rejected))
        } catch (e: IOException) {
            Result.NetworkError
        } catch (e: JSONException) {
            Result.Finished(SyncOutcome.FAILED, context.getString(R.string.settings_sync_error_unexpected_response))
        } catch (e: IllegalArgumentException) {
            Result.Finished(SyncOutcome.FAILED, context.getString(R.string.settings_sync_error_invalid_address))
        }
    }

    companion object {
        /** Etapa: estimando tiempos en el teléfono. */
        const val STAGE_ESTIMATING = "ESTIMATING"
    }
}
