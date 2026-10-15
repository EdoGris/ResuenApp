package cl.lossless.player.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.lossless.player.covers.CoverCandidate
import cl.lossless.player.covers.CoverManager
import cl.lossless.player.covers.CoverRef
import cl.lossless.player.covers.CoverStore
import cl.lossless.player.playback.PlaybackPrefs
import cl.lossless.player.sync.NetworkMode
import cl.lossless.player.sync.SyncItem
import cl.lossless.player.sync.SyncOutcome
import cl.lossless.player.sync.SyncQueue
import cl.lossless.player.sync.SyncRequest
import cl.lossless.player.library.AlbumGroup
import cl.lossless.player.library.FolderExclusions
import cl.lossless.player.library.folderSegments
import cl.lossless.player.library.LibraryLocation
import cl.lossless.player.library.LibraryPrefs
import cl.lossless.player.library.MusicRepository
import cl.lossless.player.library.Track
import cl.lossless.player.library.albumKey
import cl.lossless.player.library.toAlbumGroups
import cl.lossless.player.ui.theme.AccentColor
import cl.lossless.player.ui.theme.AppFont
import cl.lossless.player.ui.theme.TextSize
import cl.lossless.player.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CoverDialogState(
    val album: AlbumGroup,
    val searching: Boolean = true,
    val saving: Boolean = false,
    val candidates: List<CoverCandidate> = emptyList(),
    val message: String? = null,
)

/** Resumen de la cola de sincronización para Ajustes. */
data class SyncQueueSummary(
    val covers: Int = 0,
    val lyrics: Int = 0,
    /** Lo que se está procesando ahora, si algo. */
    val current: String? = null,
    /** Hay pendientes pero la red actual no es una de las permitidas. */
    val waitingNetwork: Boolean = false,
) {
    val isEmpty get() = covers == 0 && lyrics == 0
}

data class LibraryUiState(
    val location: LibraryLocation? = null,
    val loading: Boolean = false,
    val albums: List<AlbumGroup> = emptyList(),
    /** Pistas visibles (sin las carpetas omitidas), en el orden de los álbumes. */
    val tracks: List<Track> = emptyList(),
    /** Todas las pistas encontradas, incluidas las de carpetas omitidas. */
    val allTracks: List<Track> = emptyList(),
    val excludedFolders: Set<String> = emptySet(),
    val tracksById: Map<String, Track> = emptyMap(),
    val covers: Map<String, CoverRef> = emptyMap(),
    val saveCoversInFolder: Boolean = true,
    val coverTaskStatus: String? = null,
    val coverTaskRunning: Boolean = false,
    val dialog: CoverDialogState? = null,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val accent: AccentColor = AccentColor.BLUE,
    val tintedText: Boolean = false,
    val font: AppFont = AppFont.SYSTEM,
    val textSize: TextSize = TextSize.NORMAL,
    val swipeToSkip: Boolean = true,
    val mediaKeysSkip: Boolean = true,
    val networkMode: NetworkMode = NetworkMode.WIFI,
    val syncQueue: SyncQueueSummary = SyncQueueSummary(),
    /** Aviso puntual para mostrar al usuario (se consume al mostrarse). */
    val userMessage: String? = null,
) {
    fun coverFor(track: Track): CoverRef? = covers[track.albumKey]
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = LibraryPrefs(app)
    private val repository = MusicRepository(app)
    private val coverManager = CoverManager(app)
    private val coverStore = CoverStore(app)
    private val playbackPrefs = PlaybackPrefs(app)
    private val queue = SyncQueue.get(app)

    /** Resultado de la búsqueda de carátulas faltantes en curso. */
    private var coverCounts = CoverCounts()

    private data class CoverCounts(val assigned: Int = 0, val doubtful: Int = 0, val failed: Int = 0) {
        override fun toString() = buildString {
            append("Asignadas: $assigned")
            if (doubtful > 0) append(" · Sin coincidencia segura: $doubtful (toca el álbum para elegir)")
            if (failed > 0) append(" · Con error: $failed")
        }
    }

    private val _ui = MutableStateFlow(
        LibraryUiState(
            saveCoversInFolder = prefs.saveCoversInFolder,
            excludedFolders = prefs.excludedFolders,
            themeMode = ThemeMode.entries.firstOrNull { it.name == prefs.themeMode } ?: ThemeMode.DARK,
            dynamicColor = prefs.dynamicColor,
            accent = AccentColor.entries.firstOrNull { it.name == prefs.accentColor } ?: AccentColor.BLUE,
            tintedText = prefs.tintedText,
            font = AppFont.entries.firstOrNull { it.name == prefs.appFont } ?: AppFont.SYSTEM,
            textSize = TextSize.entries.firstOrNull { it.name == prefs.textSize } ?: TextSize.NORMAL,
            swipeToSkip = prefs.swipeToSkip,
            mediaKeysSkip = playbackPrefs.mediaKeysSkip,
            networkMode = queue.networkMode.value,
        )
    )
    val ui = _ui.asStateFlow()

    init {
        // Lo que quedó en la cola de una sesión anterior sigue.
        queue.resume()
        viewModelScope.launch {
            queue.items.collect { items -> onQueueChanged(items) }
        }
        viewModelScope.launch {
            queue.networkMode.collect { mode ->
                _ui.update { it.copy(networkMode = mode) }
                onQueueChanged(queue.items.value)
            }
        }
        viewModelScope.launch {
            queue.events.collect { event ->
                val request = event.request as? SyncRequest.Cover ?: return@collect
                val ref = if (event.outcome == SyncOutcome.DONE) {
                    withContext(Dispatchers.IO) { coverStore.get(request.albumKey) }
                } else null
                coverCounts = when (event.outcome) {
                    SyncOutcome.DONE -> coverCounts.copy(assigned = coverCounts.assigned + 1)
                    SyncOutcome.NO_MATCH -> coverCounts.copy(doubtful = coverCounts.doubtful + 1)
                    SyncOutcome.FAILED -> coverCounts.copy(failed = coverCounts.failed + 1)
                    SyncOutcome.NOT_NEEDED -> coverCounts
                }
                _ui.update { s ->
                    s.copy(covers = if (ref != null) s.covers + (request.albumKey to ref) else s.covers)
                }
                onQueueChanged(queue.items.value)
            }
        }
    }

    private fun onQueueChanged(items: List<SyncItem>) {
        val covers = items.count { it.request is SyncRequest.Cover }
        val summary = SyncQueueSummary(
            covers = covers,
            lyrics = items.count { it.request is SyncRequest.Lyrics },
            current = items.firstOrNull { it.running }?.let { item ->
                when (item.request) {
                    is SyncRequest.Cover -> "Carátula: ${item.request.label}"
                    is SyncRequest.Lyrics -> "Letra: ${item.request.label}"
                }
            },
            waitingNetwork = items.isNotEmpty() && items.none { it.running } && !queue.networkAllowedNow(),
        )
        _ui.update { s ->
            s.copy(
                syncQueue = summary,
                coverTaskRunning = covers > 0,
                coverTaskStatus = when {
                    covers > 0 && summary.waitingNetwork ->
                        "$covers en cola · esperando " + networkName(s.networkMode) + " · $coverCounts"
                    covers > 0 -> "$covers en cola · $coverCounts"
                    s.coverTaskRunning -> coverCounts.toString() // recién terminó
                    else -> s.coverTaskStatus
                },
            )
        }
    }

    private fun networkName(mode: NetworkMode) = when (mode) {
        NetworkMode.WIFI -> "Wi-Fi"
        NetworkMode.MOBILE -> "datos móviles"
        NetworkMode.ANY -> "conexión"
    }

    fun setNetworkMode(mode: NetworkMode) = queue.setNetworkMode(mode)

    /** Vacía lo pendiente de la cola (lo que está en curso termina). */
    fun clearSyncQueue() = queue.clear()

    fun setMediaKeysSkip(value: Boolean) {
        playbackPrefs.mediaKeysSkip = value
        _ui.update { it.copy(mediaKeysSkip = value) }
    }

    private var localCoversJob: Job? = null
    private var exclusionsJob: Job? = null

    // ---------- Biblioteca y carpeta ----------

    fun reload() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val location = withContext(Dispatchers.IO) { prefs.location }
            val all = repository.loadTracks(location)
            _ui.update { it.copy(location = location, allTracks = all) }
            applyExclusions()
        }
    }

    /** Recalcula la biblioteca visible sin volver a escanear. */
    private fun applyExclusions() {
        exclusionsJob?.cancel()
        exclusionsJob = viewModelScope.launch {
            val state = _ui.value
            val albums = withContext(Dispatchers.Default) {
                state.allTracks
                    .filterNot { FolderExclusions.isExcluded(it.folderSegments, state.excludedFolders) }
                    .toAlbumGroups()
            }
            val tracks = albums.flatMap { it.tracks }
            _ui.update {
                it.copy(
                    albums = albums,
                    tracks = tracks,
                    tracksById = tracks.associateBy { t -> t.uri.toString() },
                    loading = false,
                )
            }
            resolveLocalCovers(albums, state.location)
        }
    }

    // ---------- Carpetas omitidas ----------

    fun setFolderExcluded(key: String, excluded: Boolean) {
        val current = _ui.value.excludedFolders
        val next = if (excluded) FolderExclusions.add(current, key) else current - key
        updateExclusions(next)
    }

    fun excludeFolders(keys: Collection<String>) =
        updateExclusions(keys.fold(_ui.value.excludedFolders, FolderExclusions::add))

    fun clearExclusions() = updateExclusions(emptySet())

    private fun updateExclusions(next: Set<String>) {
        if (next == _ui.value.excludedFolders) return
        prefs.excludedFolders = next
        _ui.update { it.copy(excludedFolders = next) }
        applyExclusions()
    }

    fun selectFolder(treeUri: Uri) {
        prefs.location?.let { prefs.release(it.treeUri) }
        prefs.grant(treeUri)
        prefs.location = LibraryLocation(treeUri)
        reload()
    }

    fun showWholeLibrary() {
        prefs.location?.let { prefs.release(it.treeUri) }
        prefs.location = null
        reload()
    }

    fun setSaveCoversInFolder(value: Boolean) {
        prefs.saveCoversInFolder = value
        _ui.update { it.copy(saveCoversInFolder = value) }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode.name
        _ui.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(value: Boolean) {
        prefs.dynamicColor = value
        _ui.update { it.copy(dynamicColor = value) }
    }

    /** Elegir un color propio desactiva Material You (si no, el color elegido no se vería). */
    fun setAccent(accent: AccentColor) {
        prefs.accentColor = accent.name
        prefs.dynamicColor = false
        _ui.update { it.copy(accent = accent, dynamicColor = false) }
    }

    fun setTintedText(value: Boolean) {
        prefs.tintedText = value
        _ui.update { it.copy(tintedText = value) }
    }

    fun setFont(font: AppFont) {
        prefs.appFont = font.name
        _ui.update { it.copy(font = font) }
    }

    fun setTextSize(size: TextSize) {
        prefs.textSize = size.name
        _ui.update { it.copy(textSize = size) }
    }

    fun setSwipeToSkip(value: Boolean) {
        prefs.swipeToSkip = value
        _ui.update { it.copy(swipeToSkip = value) }
    }

    /** Carátulas locales (caché, carpeta, incrustadas): se resuelven en segundo plano por lotes. */
    private fun resolveLocalCovers(albums: List<AlbumGroup>, location: LibraryLocation?) {
        localCoversJob?.cancel()
        localCoversJob = viewModelScope.launch(Dispatchers.IO) {
            val batch = mutableMapOf<String, CoverRef>()
            for ((i, album) in albums.withIndex()) {
                if (!isActive) return@launch
                coverManager.findLocal(album, location)?.let { batch[album.key] = it }
                if (batch.size >= 20 || i == albums.lastIndex) {
                    val found = batch.toMap()
                    batch.clear()
                    _ui.update { it.copy(covers = it.covers + found) }
                }
            }
        }
    }

    // ---------- Búsqueda automática de carátulas faltantes ----------

    /** Pone en la cola de sincronización los álbumes sin carátula. */
    fun fetchMissingCovers() {
        val state = _ui.value
        val missing = state.albums.filter { it.key !in state.covers }
        if (missing.isEmpty()) {
            _ui.update { it.copy(coverTaskStatus = "Todos los álbumes ya tienen carátula") }
            return
        }
        if (state.syncQueue.covers == 0) coverCounts = CoverCounts()
        queue.enqueue(missing.map { SyncRequest.cover(it) })
    }

    fun cancelFetch() {
        queue.clear { it is SyncRequest.Cover }
        _ui.update { it.copy(coverTaskRunning = false, coverTaskStatus = "Búsqueda detenida · $coverCounts") }
    }

    // ---------- Elección manual ----------

    fun openCoverDialog(album: AlbumGroup) {
        _ui.update { it.copy(dialog = CoverDialogState(album)) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { coverManager.searchOnline(album) }
            _ui.update { s ->
                if (s.dialog?.album?.key != album.key) return@update s
                s.copy(
                    dialog = s.dialog.copy(
                        searching = false,
                        candidates = result.getOrDefault(emptyList()),
                        message = when {
                            result.isFailure -> "No se pudo buscar. Revisa tu conexión a internet."
                            result.getOrNull().isNullOrEmpty() -> "No se encontraron carátulas para este álbum."
                            else -> null
                        }
                    )
                )
            }
        }
    }

    fun chooseCover(candidate: CoverCandidate) {
        val dialog = _ui.value.dialog ?: return
        _ui.update { it.copy(dialog = dialog.copy(saving = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                coverManager.apply(
                    dialog.album, candidate, _ui.value.location, _ui.value.saveCoversInFolder
                )
            }
            result.onSuccess { (ref, _) ->
                _ui.update { it.copy(covers = it.covers + (dialog.album.key to ref), dialog = null) }
            }.onFailure {
                _ui.update {
                    it.copy(dialog = dialog.copy(saving = false, message = "No se pudo descargar la imagen."))
                }
            }
        }
    }

    /** Carátula elegida desde la galería o los archivos del teléfono. */
    fun chooseLocalCover(image: Uri) {
        val dialog = _ui.value.dialog ?: return
        _ui.update { it.copy(dialog = dialog.copy(saving = true)) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                coverManager.applyLocal(
                    dialog.album, image, _ui.value.location, _ui.value.saveCoversInFolder
                )
            }
            result.onSuccess { (ref, written) ->
                _ui.update {
                    it.copy(
                        covers = it.covers + (dialog.album.key to ref),
                        dialog = null,
                        userMessage = if (written) "Carátula guardada también como cover.jpg en la carpeta"
                        else "Carátula asignada a «${dialog.album.title}»",
                    )
                }
            }.onFailure {
                _ui.update {
                    it.copy(dialog = dialog.copy(saving = false, message = "No se pudo leer esa imagen."))
                }
            }
        }
    }

    fun consumeUserMessage() = _ui.update { it.copy(userMessage = null) }

    fun closeDialog() = _ui.update { it.copy(dialog = null) }

    fun canWriteToFolder(album: AlbumGroup) =
        coverManager.canWriteToFolder(album, _ui.value.location)
}
