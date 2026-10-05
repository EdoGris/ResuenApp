package cl.lossless.player.ui.lyrics

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.lossless.player.library.LibraryPrefs
import cl.lossless.player.library.Track
import cl.lossless.player.lyrics.LyricLine
import cl.lossless.player.lyrics.Lyrics
import cl.lossless.player.lyrics.LyricsSource
import cl.lossless.player.lyrics.LyricsPrefs
import cl.lossless.player.lyrics.LyricsRepository
import cl.lossless.player.lyrics.LyricsResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** Retraso de la letra en ms (positivo = aparece más tarde). */
    val offsetMs: Long = 0L,
    val searchOnline: Boolean = true,
    /** Se están estimando los tiempos de una letra sin marcas. */
    val estimating: Boolean = false,
    val autoSync: Boolean = true,
)

class LyricsViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LyricsRepository(app)
    private val prefs = LyricsPrefs(app)
    private val libraryPrefs = LibraryPrefs(app)

    private val _state = MutableStateFlow(LyricsUiState(searchOnline = prefs.searchOnline, autoSync = prefs.autoSync))
    val state = _state.asStateFlow()

    private var loadJob: Job? = null
    private var currentTrack: Track? = null

    /** Carga la letra de [track] si aún no está cargada (o siempre, con [force]). */
    fun loadFor(track: Track?, force: Boolean = false) {
        currentTrack = track
        if (track == null) {
            loadJob?.cancel()
            _state.update { LyricsUiState(searchOnline = it.searchOnline, autoSync = it.autoSync) }
            return
        }
        val key = repository.cacheKey(track)
        val current = _state.value
        if (!force && current.trackKey == key && (current.loading || current.lyrics != null || current.notFound)) return

        loadJob?.cancel()
        _state.update {
            LyricsUiState(
                trackKey = key, loading = true, offsetMs = prefs.offsetFor(key),
                searchOnline = it.searchOnline, autoSync = it.autoSync,
            )
        }
        loadJob = viewModelScope.launch {
            val location = withContext(Dispatchers.IO) { libraryPrefs.location }
            val result = repository.load(track, location, prefs.searchOnline, forceRefresh = force)
            _state.update { s ->
                if (s.trackKey != key) return@update s // cambió la pista mientras tanto
                when (result) {
                    is LyricsResult.Found -> s.copy(
                        loading = false,
                        lyrics = result.lyrics,
                        estimating = needsEstimate(result.lyrics),
                    )
                    is LyricsResult.NotFound -> s.copy(
                        loading = false,
                        notFound = true,
                        onlineDisabled = !result.searchedOnline,
                        offline = result.offline,
                    )
                }
            }
            // Letra sin tiempos: se muestra ya y, en paralelo, se estiman los tiempos.
            val found = (result as? LyricsResult.Found)?.lyrics
            if (found != null && needsEstimate(found)) {
                val estimated = runCatching { repository.estimate(track, found) }.getOrNull()
                _state.update { s ->
                    if (s.trackKey != key) s
                    else s.copy(estimating = false, lyrics = estimated ?: s.lyrics)
                }
            }
        }
    }

    private fun needsEstimate(lyrics: Lyrics) =
        prefs.autoSync && !lyrics.isSynced && !lyrics.plain.isNullOrBlank()

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
            it.copy(lyrics = Lyrics(lines, lines.joinToString("\n") { l -> l.text }, LyricsSource.USER), offsetMs = 0, estimating = false)
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
