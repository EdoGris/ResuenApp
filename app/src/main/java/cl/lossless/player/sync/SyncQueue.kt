package cl.lossless.player.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Cola persistente de búsquedas de carátulas y de sincronización de letras. La procesa
 * [SyncWorker] con WorkManager, que la retoma aunque se cierre la app o se reinicie el
 * teléfono, y solo cuando hay una red permitida por [NetworkMode].
 */
class SyncQueue private constructor(private val context: Context) {

    private val file = File(context.filesDir, "sync_queue.json")
    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<SyncItem>> = _items.asStateFlow()

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)
    /** Cada petición terminada (con éxito o no); la UI actualiza carátulas y letras con esto. */
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    private val _networkMode = MutableStateFlow(readNetworkMode())
    val networkMode: StateFlow<NetworkMode> = _networkMode.asStateFlow()

    fun setNetworkMode(mode: NetworkMode) {
        prefs.edit().putString(KEY_NETWORK, mode.name).apply()
        _networkMode.value = mode
        // Reemplaza el trabajo para que tome las nuevas condiciones de red.
        schedule(ExistingWorkPolicy.REPLACE)
    }

    /**
     * Agrega peticiones. [urgent] las pone al principio (las pidió el usuario a mano);
     * [retryFailed] reenvía al servidor una letra que antes no se pudo alinear.
     */
    fun enqueue(requests: List<SyncRequest>, urgent: Boolean = false, retryFailed: Boolean = false) {
        if (requests.isEmpty()) return
        update { SyncQueueOps.enqueue(it, requests, urgent, retryFailed) }
        schedule(ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun remove(id: String) = update { items -> items.filter { it.id != id || it.running } }

    /** Quita lo pendiente que cumpla [predicate] (lo que está en curso termina). */
    fun clear(predicate: (SyncRequest) -> Boolean = { true }) =
        update { items -> items.filter { it.running || !predicate(it.request) } }

    /** Vuelve a programar el trabajo si quedó algo pendiente (p. ej. al abrir la app). */
    fun resume(policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) = schedule(policy)

    /** true si la red actual es una de las permitidas. */
    fun networkAllowedNow(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        return when (_networkMode.value) {
            NetworkMode.WIFI -> unmetered
            NetworkMode.MOBILE -> !unmetered
            NetworkMode.ANY -> true
        }
    }

    // ---------- Usado por SyncWorker ----------

    /** Marca en curso y devuelve la siguiente petición pendiente que no esté en [skip]. */
    internal fun takeNext(skip: Set<String>): SyncItem? = synchronized(lock) {
        val next = SyncQueueOps.next(_items.value, skip) ?: return null
        val running = next.copy(running = true)
        updateLocked { items -> items.map { if (it.id == next.id) running else it } }
        running
    }

    internal fun setStage(id: String, stage: String?) =
        update { items -> items.map { if (it.id == id) it.copy(stage = stage) else it } }

    internal fun finish(item: SyncItem, outcome: SyncOutcome, message: String? = null) {
        update { items -> items.filter { it.id != item.id } }
        _events.tryEmit(SyncEvent(item.request, outcome, message))
    }

    /** Falló por la red: vuelve a la cola (o se descarta tras varios intentos). */
    internal fun requeue(item: SyncItem) {
        var kept = true
        update { items -> SyncQueueOps.requeue(items, item.id).also { kept = it.second }.first }
        if (!kept) _events.tryEmit(SyncEvent(item.request, SyncOutcome.FAILED, "Sin conexión tras varios intentos"))
    }

    /** Al empezar el trabajo, nada puede estar "en curso" (quedó así si se interrumpió). */
    internal fun resetRunning() =
        update { items -> items.map { if (it.running) it.copy(running = false, stage = null) else it } }

    // ---------- Interno ----------

    private fun schedule(policy: ExistingWorkPolicy) {
        val workManager = WorkManager.getInstance(context)
        if (_items.value.isEmpty()) {
            if (policy == ExistingWorkPolicy.REPLACE) workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val networkType = when (_networkMode.value) {
            NetworkMode.WIFI -> NetworkType.UNMETERED
            NetworkMode.MOBILE -> NetworkType.METERED
            NetworkMode.ANY -> NetworkType.CONNECTED
        }
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private fun update(transform: (List<SyncItem>) -> List<SyncItem>) = synchronized(lock) { updateLocked(transform) }

    private fun updateLocked(transform: (List<SyncItem>) -> List<SyncItem>) {
        val next = transform(_items.value)
        if (next == _items.value) return
        _items.value = next
        save(next)
    }

    private fun load(): List<SyncItem> =
        runCatching { if (file.isFile) SyncQueueOps.fromJson(file.readText()) else emptyList() }.getOrDefault(emptyList())

    private fun save(items: List<SyncItem>) {
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(SyncQueueOps.toJson(items))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    /** Sin elección guardada se respeta el antiguo "Subir audio solo con Wi-Fi" de las letras. */
    private fun readNetworkMode(): NetworkMode {
        prefs.getString(KEY_NETWORK, null)
            ?.let { name -> NetworkMode.entries.firstOrNull { it.name == name } }
            ?.let { return it }
        val lyrics = context.getSharedPreferences("lyrics", Context.MODE_PRIVATE)
        return if (lyrics.getBoolean("align_wifi_only", true)) NetworkMode.WIFI else NetworkMode.ANY
    }

    companion object {
        private const val WORK_NAME = "sync-queue"
        private const val KEY_NETWORK = "network_mode"

        @Volatile private var instance: SyncQueue? = null

        fun get(context: Context): SyncQueue =
            instance ?: synchronized(this) {
                instance ?: SyncQueue(context.applicationContext).also { instance = it }
            }
    }
}
