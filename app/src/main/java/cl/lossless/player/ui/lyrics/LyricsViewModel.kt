package cl.lossless.player.ui.lyrics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.lossless.player.library.LibraryPrefs
import cl.lossless.player.library.Track
import cl.lossless.player.lyrics.AlignmentClient
import cl.lossless.player.lyrics.LyricLine
import cl.lossless.player.lyrics.Lyrics
import cl.lossless.player.lyrics.LyricsSource
import cl.lossless.player.lyrics.LyricsPrefs
import cl.lossless.player.lyrics.LyricsRepository
import cl.lossless.player.lyrics.LyricsResult
import cl.lossless.player.sync.NetworkMode
import cl.lossless.player.sync.SyncOutcome
import cl.lossless.player.sync.SyncQueue
import cl.lossless.player.sync.SyncRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException

private const val TRACK_CHANGE_DEBOUNCE_MS = 600L

/** Estado de la alineación en el servidor (WhisperX) para la letra en pantalla. */
sealed interface AlignmentUi {
    data class Working(val stage: LyricsRepository.AlignStage) : AlignmentUi
    /** En la cola de sincronización, detrás de otras peticiones. */
    data object Queued : AlignmentUi
    /** En la cola, esperando una red permitida (Ajustes → Sincronización). */
    data class WaitingNetwork(val mode: NetworkMode) : AlignmentUi
    data class Failed(val message: String) : AlignmentUi
}

data class LyricsUiState(
    /** Pista cuya letra se muestra (URI). */
    val trackKey: String? = null,
    val loading: Boolean = false,
    val lyrics: Lyrics? = null,
    /** Se buscó y no hay letra. */
    val notFound: Boolean = false,
    /** No hay letra local y la búsqueda en internet está desactivada. */
    val onlineDisabled: Boolean = false,
    /** No se pudo consultar internet. */
    val offline: Boolean = false,
    /** LRCLIB respondió, pero con un error suyo (saturado o caído): no es la conexión. */
    val serverError: Boolean = false,
    /** Qué falló al consultar LRCLIB (p. ej. "HTTP 503"), para mostrarlo. */
    val errorDetail: String? = null,
    /** Retraso de la letra en ms (positivo = aparece más tarde). */
    val offsetMs: Long = 0L,
    val searchOnline: Boolean = true,
    /** Se están estimando los tiempos de una letra sin marcas. */
    val estimating: Boolean = false,
    val autoSync: Boolean = true,
    /** null = no se usa el servidor (o ya terminó). */
    val alignment: AlignmentUi? = null,
    val alignServerUrl: String = "",
    val alignToken: String = "",
) {
    /** Se puede pedir la sincronización: letra sin tiempos, o con tiempos solo estimados. */
    val canSync: Boolean
        get() = lyrics != null && !lyrics.plain.isNullOrBlank() && (!lyrics.isSynced || lyrics.estimated) &&
            alignment !is AlignmentUi.Working && alignment !is AlignmentUi.Queued && alignment !is AlignmentUi.WaitingNetwork &&
            // Sin servidor solo hay estimación en el teléfono: sirve si aún no se hizo.
            (alignServerUrl.isNotBlank() || (!lyrics.isSynced && !estimating))
}

class LyricsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LyricsRepository(app)
    private val prefs = LyricsPrefs(app)
    private val libraryPrefs = LibraryPrefs(app)
    private val queue = SyncQueue.get(app)

    private val _state = MutableStateFlow(
        LyricsUiState(
            searchOnline = prefs.searchOnline,
            autoSync = prefs.autoSync,
            alignServerUrl = prefs.alignServerUrl,
            alignToken = prefs.alignToken,
        )
    )
    val state = _state.asStateFlow()

    private var loadJob: Job? = null
    /** "Sincronizar ahora" pedido a mano; se cancela al cambiar de pista. */
    private var alignJob: Job? = null
    private var currentTrack: Track? = null
    /** Letra sin tiempos de la pista actual, tal como se encontró (sin estimar ni alinear). */
    private var unsynced: Lyrics? = null

    init {
        // Estado de la pista actual en la cola de sincronización.
        viewModelScope.launch {
            combine(queue.items, queue.networkMode) { items, mode -> items to mode }.collect { (items, mode) ->
                _state.update { s ->
                    val key = s.trackKey ?: return@update s
                    if (s.alignServerUrl.isBlank()) return@update s
                    val item = items.firstOrNull { it.id == lyricsId(key) }
                    val next = when {
                        item == null -> s.alignment.takeIf { it is AlignmentUi.Failed }
                        item.running -> item.stage
                            ?.let { stage -> LyricsRepository.AlignStage.entries.firstOrNull { it.name == stage } }
                            ?.let { AlignmentUi.Working(it) }
                            ?: AlignmentUi.Working(LyricsRepository.AlignStage.HASHING)
                        queue.networkAllowedNow() -> AlignmentUi.Queued
                        else -> AlignmentUi.WaitingNetwork(mode)
                    }
                    // "Usar datos esta vez" corre aparte: su estado manda mientras dura.
                    if (alignJob?.isActive == true) s else s.copy(alignment = next)
                }
            }
        }
        // Al terminar la pista actual en la cola, se muestra el resultado.
        viewModelScope.launch {
            queue.events.collect { event ->
                val request = event.request as? SyncRequest.Lyrics ?: return@collect
                val track = currentTrack ?: return@collect
                if (request.trackUri != repository.cacheKey(track)) return@collect
                val base = unsynced
                val aligned = if (event.outcome == SyncOutcome.DONE && base != null) {
                    repository.cachedAlignment(track, base)
                } else null
                _state.update { s ->
                    when {
                        s.trackKey != request.trackUri -> s
                        s.lyrics?.source == LyricsSource.USER -> s.copy(alignment = null)
                        aligned != null -> s.copy(lyrics = aligned, estimating = false, alignment = null)
                        event.outcome == SyncOutcome.FAILED ->
                            s.copy(alignment = AlignmentUi.Failed(event.message ?: "No se pudo sincronizar"))
                        else -> s.copy(alignment = null)
                    }
                }
            }
        }
    }

    private fun lyricsId(trackKey: String) = SyncRequest.lyricsId(trackKey)

    /** Carga la letra de [track] si aún no está cargada (o siempre, con [force]). */
    fun loadFor(track: Track?, force: Boolean = false) {
        currentTrack = track
        if (track == null) {
            loadJob?.cancel()
            alignJob?.cancel()
            _state.update { it.settingsOnly() }
            return
        }
        val key = repository.cacheKey(track)
        val current = _state.value
        if (!force && current.trackKey == key && (current.loading || current.lyrics != null || current.notFound)) return

        // Al pasar de una canción a otra se espera un instante: si se salta varias seguidas no
        // se consulta LRCLIB por cada una (muchas peticiones seguidas pueden hacer que rechace).
        val debounce = !force && current.trackKey != null && current.trackKey != key
        loadJob?.cancel()
        alignJob?.cancel()
        unsynced = null
        _state.update { it.settingsOnly().copy(trackKey = key, loading = true, offsetMs = prefs.offsetFor(key)) }
        loadJob = viewModelScope.launch {
            if (debounce) delay(TRACK_CHANGE_DEBOUNCE_MS)
            val location = withContext(Dispatchers.IO) { libraryPrefs.location }
            val result = repository.load(track, location, prefs.searchOnline, forceRefresh = force)
            _state.update { s ->
                if (s.trackKey != key) return@update s // cambió la pista mientras tanto
                when (result) {
                    is LyricsResult.Found -> s.copy(loading = false, lyrics = result.lyrics)
                    is LyricsResult.NotFound -> s.copy(
                        loading = false,
                        notFound = true,
                        onlineDisabled = !result.searchedOnline,
                        offline = result.offline,
                        serverError = result.serverError,
                        errorDetail = result.detail,
                    )
                }
            }
            val found = (result as? LyricsResult.Found)?.lyrics
            if (found == null || found.isSynced || found.plain.isNullOrBlank()) return@launch
            unsynced = found

            // Letra sin tiempos: se muestra ya; si el servidor la alineó antes, se usa eso.
            repository.cachedAlignment(track, found)?.let { aligned ->
                _state.update { s -> if (s.trackKey != key) s else s.copy(lyrics = aligned) }
                return@launch
            }
            // Si no, con la sincronización automática activada, en paralelo: el servidor (exacto,
            // por palabra; va por la cola de sincronización, adelante de todo) y la estimación en
            // el teléfono (inmediata, aproximada), que se muestra hasta que llegue lo del servidor.
            // Desactivada, se espera al botón "Sincronizar".
            if (!prefs.autoSync) return@launch
            if (prefs.alignServerUrl.isNotBlank()) {
                queue.enqueue(listOf(SyncRequest.lyrics(track)), urgent = true, retryFailed = force)
            }
            estimate(track, key, found)
        }
    }

    private suspend fun estimate(track: Track, key: String, found: Lyrics) {
        _state.update { s -> if (s.trackKey != key) s else s.copy(estimating = true) }
        val estimated = runCatching { repository.estimate(track, found) }.getOrNull()
        _state.update { s ->
            when {
                s.trackKey != key -> s
                // Lo del servidor (o lo marcado a mano) ya llegó: no se reemplaza por la estimación.
                s.lyrics?.source == LyricsSource.ALIGNED || s.lyrics?.source == LyricsSource.USER -> s.copy(estimating = false)
                else -> s.copy(estimating = false, lyrics = estimated ?: s.lyrics)
            }
        }
    }

    /**
     * Botón "Sincronizar": con servidor, pone la canción primera en la cola (reintentando si
     * antes falló); sin servidor, estima los tiempos en el teléfono.
     */
    fun syncNow() {
        val track = currentTrack ?: return
        val key = _state.value.trackKey ?: return
        val base = unsynced ?: _state.value.lyrics?.takeIf { !it.isSynced && !it.plain.isNullOrBlank() } ?: return
        unsynced = base
        if (prefs.alignServerUrl.isNotBlank()) {
            _state.update { it.copy(alignment = AlignmentUi.Queued) }
            queue.enqueue(listOf(SyncRequest.lyrics(track)), urgent = true, retryFailed = true)
            // Mientras llega lo exacto, la estimación (si aún no se mostró).
            if (_state.value.lyrics?.isSynced != true && !_state.value.estimating) {
                viewModelScope.launch { estimate(track, key, base) }
            }
        } else if (!_state.value.estimating) {
            viewModelScope.launch { estimate(track, key, base) }
        }
    }

    /** "Sincronizar letras" de un álbum o carpeta: todas a la cola, en orden. */
    fun syncAll(tracks: List<Track>) {
        queue.enqueue(tracks.map { SyncRequest.lyrics(it) })
    }

    /** Sube (si hace falta) y espera la alineación del servidor para la letra de [key]. */
    private suspend fun alignOnServer(track: Track, key: String, lyrics: Lyrics, retry: Boolean, forceUpload: Boolean = false) {
        val client = AlignmentClient(prefs.alignServerUrl, prefs.alignToken)
        val allowUpload = forceUpload || queue.networkAllowedNow()
        val outcome = try {
            repository.align(track, lyrics, client, allowUpload, retryFailed = retry || forceUpload) { stage ->
                _state.update { s -> if (s.trackKey != key) s else s.copy(alignment = AlignmentUi.Working(stage)) }
            }
        } catch (e: AlignmentClient.Unauthorized) {
            LyricsRepository.AlignOutcome.Failed("El servidor rechazó el token. Revísalo en Ajustes → Letras.")
        } catch (e: IOException) {
            LyricsRepository.AlignOutcome.Failed("No se pudo conectar con el servidor de sincronización.")
        } catch (e: JSONException) {
            LyricsRepository.AlignOutcome.Failed("El servidor respondió algo inesperado.")
        }
        _state.update { s ->
            if (s.trackKey != key) return@update s
            // El usuario sincronizó a mano mientras tanto: eso manda.
            if (s.lyrics?.source == LyricsSource.USER) return@update s.copy(alignment = null)
            when (outcome) {
                is LyricsRepository.AlignOutcome.Done -> s.copy(lyrics = outcome.lyrics, estimating = false, alignment = null)
                LyricsRepository.AlignOutcome.NeedsUpload -> s.copy(alignment = AlignmentUi.WaitingNetwork(queue.networkMode.value))
                is LyricsRepository.AlignOutcome.Failed -> s.copy(alignment = AlignmentUi.Failed(outcome.message))
            }
        }
    }

    /**
     * "Usar esta red ahora": sube aunque la red actual no sea la permitida (p. ej. con datos
     * móviles y "Solo Wi-Fi") y reintenta si falló. Sale de la cola y se hace en el momento.
     */
    fun alignNow() {
        val track = currentTrack ?: return
        val state = _state.value
        val key = state.trackKey ?: return
        val lyrics = (unsynced ?: state.lyrics)?.takeIf { !it.plain.isNullOrBlank() } ?: return
        if (prefs.alignServerUrl.isBlank() || state.alignment is AlignmentUi.Working) return
        queue.remove(lyricsId(key))
        alignJob = viewModelScope.launch { alignOnServer(track, key, lyrics, retry = true, forceUpload = true) }
    }

    private fun LyricsUiState.settingsOnly() = LyricsUiState(
        searchOnline = searchOnline,
        autoSync = autoSync,
        alignServerUrl = alignServerUrl,
        alignToken = alignToken,
    )

    fun setAlignServer(url: String, token: String) {
        prefs.alignServerUrl = url
        prefs.alignToken = token
        _state.update { it.copy(alignServerUrl = prefs.alignServerUrl, alignToken = prefs.alignToken) }
    }

    /** Prueba la conexión con el servidor; devuelve un mensaje para mostrar. */
    suspend fun testAlignServer(url: String, token: String): String = withContext(Dispatchers.IO) {
        try {
            "Conectado: " + AlignmentClient(url, token).health()
        } catch (e: AlignmentClient.Unauthorized) {
            "El servidor rechazó el token"
        } catch (e: IOException) {
            "No se pudo conectar: ${e.message ?: e.javaClass.simpleName}"
        } catch (e: JSONException) {
            "La dirección no parece ser el servidor de sincronización"
        } catch (e: IllegalArgumentException) {
            "Dirección inválida"
        }
    }

    /**
     * Guarda los tiempos marcados tocando. Devuelve true si se escribió un .lrc junto a la
     * canción (false: quedó guardado solo en la app).
     */
    suspend fun saveUserSync(lines: List<LyricLine>): Boolean {
        val track = currentTrack ?: return false
        val location = withContext(Dispatchers.IO) { libraryPrefs.location }
        val savedTo = repository.saveUserSync(track, location, lines)
        // El ajuste de desfase ya no tiene sentido sobre tiempos marcados a mano.
        prefs.setOffset(repository.cacheKey(track), 0)
        _state.update {
            it.copy(lyrics = Lyrics(lines, lines.joinToString("\n") { l -> l.text }, LyricsSource.USER), offsetMs = 0, estimating = false, alignment = null)
        }
        return savedTo == LyricsRepository.SavedTo.FOLDER
    }

    fun setAutoSync(enabled: Boolean) {
        prefs.autoSync = enabled
        _state.update { it.copy(autoSync = enabled) }
    }

    /** Ajusta la sincronía de esta pista en [deltaMs] (se recuerda por pista). */
    fun adjustOffset(deltaMs: Long) {
        val key = _state.value.trackKey ?: return
        val next = (_state.value.offsetMs + deltaMs).coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        prefs.setOffset(key, next)
        _state.update { it.copy(offsetMs = next) }
    }

    fun resetOffset() = adjustOffset(-_state.value.offsetMs)

    fun setSearchOnline(enabled: Boolean) {
        prefs.searchOnline = enabled
        _state.update { it.copy(searchOnline = enabled) }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) { repository.clearCache() }
    }

    private companion object {
        const val MAX_OFFSET_MS = 10_000L
    }
}
