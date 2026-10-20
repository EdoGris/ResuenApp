package cl.lossless.player.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore

/**
 * Carpeta elegida por el usuario con el selector del sistema (Storage Access Framework).
 * Sirve para memoria interna, tarjeta SD y, en la mayoría de los equipos, pendrives USB-OTG.
 * Con ella además se puede escribir cover.jpg dentro de las carpetas de los álbumes.
 */
data class LibraryLocation(val treeUri: Uri) {

    /** Ej.: "primary:Music/Rock" o "1A2B-3C4D:Musica". */
    val rootDocId: String = DocumentsContract.getTreeDocumentId(treeUri)

    private val isExternalStorage get() = treeUri.authority == EXTERNAL_STORAGE_AUTHORITY
    private val volumeId get() = rootDocId.substringBefore(':')
    private val path get() = rootDocId.substringAfter(':', "").trim('/')

    /** Volumen equivalente en MediaStore, o null si la carpeta no es del almacenamiento local. */
    val mediaStoreVolume: String?
        get() = when {
            !isExternalStorage -> null
            volumeId == "primary" -> MediaStore.VOLUME_EXTERNAL_PRIMARY
            else -> volumeId.lowercase()
        }

    /** Ruta relativa en el formato de MediaStore (termina en "/"), o "" si es la raíz. */
    val relativePath: String get() = if (path.isEmpty()) "" else "$path/"

    val label: String
        get() {
            val volume = if (volumeId == "primary") "Interno" else volumeId
            return if (path.isEmpty()) "$volume (todo)" else "$volume › $path"
        }

    /** Ruta tipo sistema de archivos, ej. "/storage/emulated/0/Music". */
    val displayPath: String
        get() {
            val base = if (volumeId == "primary") "/storage/emulated/0" else "/storage/$volumeId"
            return if (path.isEmpty()) base else "$base/$path"
        }

    /** true si el documento está dentro de la carpeta concedida (y por lo tanto es accesible). */
    fun contains(docId: String): Boolean =
        docId == rootDocId ||
            docId.startsWith(if (rootDocId.endsWith(":")) rootDocId else "$rootDocId/")

    fun documentUri(docId: String): Uri? =
        if (contains(docId)) DocumentsContract.buildDocumentUriUsingTree(treeUri, docId) else null

    fun childrenUri(docId: String): Uri =
        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

    companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        /** Id de documento SAF de una carpeta indexada por MediaStore. */
        fun docIdFor(volumeName: String, relativePath: String): String {
            val vol = if (volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY) "primary"
            else volumeName.uppercase()
            return "$vol:${relativePath.trimEnd('/')}"
        }
    }
}

/** Preferencias persistentes de la biblioteca. */
class LibraryPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    /** Carpeta guardada, solo si el permiso persistente sigue vigente. */
    var location: LibraryLocation?
        get() {
            val uri = prefs.getString(KEY_TREE, null)?.let(Uri::parse) ?: return null
            val granted = context.contentResolver.persistedUriPermissions
                .any { it.uri == uri && it.isReadPermission }
            return if (granted) LibraryLocation(uri) else null
        }
        set(value) {
            prefs.edit().putString(KEY_TREE, value?.treeUri?.toString()).apply()
        }

    /** Nombre de [cl.lossless.player.ui.theme.ThemeMode]. */
    var themeMode: String?
        get() = prefs.getString(KEY_THEME, null)
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Nombre de [cl.lossless.player.ui.theme.AccentColor]. */
    var accentColor: String?
        get() = prefs.getString(KEY_ACCENT, null)
        set(value) = prefs.edit().putString(KEY_ACCENT, value).apply()

    var tintedText: Boolean
        get() = prefs.getBoolean(KEY_TINTED_TEXT, false)
        set(value) = prefs.edit().putBoolean(KEY_TINTED_TEXT, value).apply()

    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    /** Carpetas omitidas (ver [FolderExclusions]). */
    var excludedFolders: Set<String>
        get() = prefs.getStringSet(KEY_EXCLUDED, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_EXCLUDED, value).apply()

    /** Nombre de [cl.lossless.player.ui.theme.AppFont]. */
    var appFont: String?
        get() = prefs.getString(KEY_FONT, null)
        set(value) = prefs.edit().putString(KEY_FONT, value).apply()

    /** Nombre de [cl.lossless.player.ui.theme.TextSize]. */
    var textSize: String?
        get() = prefs.getString(KEY_TEXT_SIZE, null)
        set(value) = prefs.edit().putString(KEY_TEXT_SIZE, value).apply()

    /** Deslizar la carátula o el mini reproductor para cambiar de canción. */
    var swipeToSkip: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_SKIP, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_SKIP, value).apply()

    var saveCoversInFolder: Boolean
        get() = prefs.getBoolean(KEY_COVERS_IN_FOLDER, true)
        set(value) = prefs.edit().putBoolean(KEY_COVERS_IN_FOLDER, value).apply()

    /** Toma el permiso persistente (lectura y, si se puede, escritura) sobre la carpeta. */
    fun grant(uri: Uri) {
        val resolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun release(uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    private companion object {
        const val KEY_TREE = "tree_uri"
        const val KEY_COVERS_IN_FOLDER = "covers_in_folder"
        const val KEY_THEME = "theme_mode"
        const val KEY_EXCLUDED = "excluded_folders"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_ACCENT = "accent_color"
        const val KEY_TINTED_TEXT = "tinted_text"
        const val KEY_FONT = "app_font"
        const val KEY_TEXT_SIZE = "text_size"
        const val KEY_SWIPE_SKIP = "swipe_to_skip"
    }
}
