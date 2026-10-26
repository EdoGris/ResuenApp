package cl.lossless.player.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Estado de la búsqueda e instalación de versiones nuevas. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState
    /** Hace falta permitir "Instalar apps desconocidas" para esta app y volver a intentar. */
    data class NeedsPermission(val update: AvailableUpdate) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Actualiza la app desde el repositorio público de instaladores: la build experimental (.exp)
 * sigue las versiones `exp-…` y la estable las `v…`. Descarga el APK y abre el instalador de
 * Android, que lo instala encima porque el CI firma con la misma llave.
 */
object AppUpdater {

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /** El aviso al abrir la app ya se mostró (o se descartó) en esta sesión. */
    var promptDismissed by mutableStateOf(false)

    private fun isExperimental(context: Context) = context.packageName.endsWith(".exp")

    private fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    private fun checker(context: Context) = UpdateChecker(userAgent = "ResuenApp/${currentVersion(context)} (Android)")

    suspend fun check(context: Context) {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        state = UpdateState.Checking
        val exp = isExperimental(context)
        state = withContext(Dispatchers.IO) {
            runCatching {
                checker(context).check(currentVersion(context), if (exp) "exp" else "v") { name ->
                    name.endsWith(".apk") && name.contains("-exp-") == exp
                }
            }.fold(
                onSuccess = { it?.let(UpdateState::Available) ?: UpdateState.UpToDate },
                onFailure = { UpdateState.Failed(it.message ?: it.javaClass.simpleName) },
            )
        }
    }

    suspend fun install(context: Context, update: AvailableUpdate) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            state = UpdateState.NeedsPermission(update)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        state = UpdateState.Downloading(update, 0f)
        val apk = File(File(context.cacheDir, "updates"), update.assetName)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                File(context.cacheDir, "updates").listFiles()?.forEach { if (it.name != apk.name) it.delete() }
                checker(context).download(update, apk) { p -> state = UpdateState.Downloading(update, p) }
            }
        }
        result.onFailure {
            state = UpdateState.Failed(it.message ?: it.javaClass.simpleName)
            return
        }
        state = UpdateState.Available(update)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
