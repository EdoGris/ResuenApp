package cl.lossless.player.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cl.lossless.player.update.AvailableUpdate
import cl.lossless.player.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.system.exitProcess

/** Estado de la búsqueda e instalación de versiones nuevas. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Actualiza la app desde el repositorio público de instaladores (canal `exp`, igual que la
 * build experimental de Android). En Windows descarga el Setup.exe, lo abre y cierra la app para
 * que el instalador reemplace la versión anterior; en macOS abre la .dmg de su arquitectura.
 */
object DesktopUpdater {

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private val checker by lazy { UpdateChecker(userAgent = "ResuenApp/$APP_VERSION (${System.getProperty("os.name")})") }

    private fun pickAsset(name: String): Boolean = when {
        IsMac && System.getProperty("os.arch") in listOf("aarch64", "arm64") -> name.endsWith("macOS-apple-silicon.dmg")
        IsMac -> name.endsWith("macOS-intel.dmg")
        else -> name.endsWith("-Setup.exe")
    }

    /** [quiet]: al abrir la app, un error de red no se muestra (queda en Idle). */
    suspend fun check(quiet: Boolean = false) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        state = withContext(Dispatchers.IO) {
            runCatching { checker.check(APP_VERSION, "exp", ::pickAsset) }.fold(
                onSuccess = { it?.let(UpdateState::Available) ?: UpdateState.UpToDate },
                onFailure = { if (quiet) UpdateState.Idle else UpdateState.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    /** Descarga [update], abre el instalador y cierra la app (detiene el audio antes). */
    suspend fun install(update: AvailableUpdate, player: Player) {
        if (state is UpdateState.Downloading) return
        state = UpdateState.Downloading(update, 0f)
        val dir = File(AppDirs.data, "updates")
        val file = File(dir, update.assetName)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                dir.listFiles()?.forEach { if (it.name != file.name) it.delete() }
                checker.download(update, file) { p -> state = UpdateState.Downloading(update, p) }
                val command = if (IsMac) listOf("open", file.absolutePath) else listOf(file.absolutePath)
                ProcessBuilder(command).directory(dir).start()
            }
        }
        result.onFailure {
            state = UpdateState.Failed(it.message ?: it.javaClass.simpleName)
            return
        }
        player.shutdown()
        exitProcess(0)
    }
}
