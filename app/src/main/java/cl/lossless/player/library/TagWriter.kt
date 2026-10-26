package cl.lossless.player.library

import android.app.PendingIntent
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import cl.lossless.player.tags.TagField
import cl.lossless.player.tags.TagFiles
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jaudiotagger.tag.TagOptionSingleton
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Escribe etiquetas (álbum, artista…) dentro de los archivos de música del teléfono.
 *
 * jaudiotagger necesita un archivo normal, así que cada pista se copia a la caché, se edita ahí
 * y se vuelve a escribir encima del original. Si algo falla antes de esa última copia, el
 * original queda intacto.
 *
 * Los archivos dentro de la carpeta elegida se escriben con su permiso; los demás (biblioteca
 * de todo el teléfono) piden antes la autorización del sistema con [consentRequest].
 */
class TagWriter(private val context: Context) {

    init {
        // Carátulas con las clases de Android en lugar de java.awt (que no existe en Android).
        TagOptionSingleton.getInstance().setAndroid(true)
    }

    /** Dónde escribir cada pista: su documento en la carpeta concedida o su entrada de MediaStore. */
    fun targetFor(track: Track, location: LibraryLocation?): Uri {
        if (DocumentsContract.isDocumentUri(context, track.uri)) return track.uri
        val folder = track.folderDocId
        if (location != null && folder != null && track.fileName.isNotBlank() && location.canWrite(context)) {
            val docId = if (folder.endsWith(":")) "$folder${track.fileName}" else "$folder/${track.fileName}"
            location.documentUri(docId)?.let { return it }
        }
        return track.uri
    }

    /** true si escribir en [uri] necesita la autorización del sistema. */
    fun needsConsent(uri: Uri): Boolean = uri.authority == MediaStore.AUTHORITY

    /** Pide al sistema permiso para modificar varias canciones de MediaStore (un solo diálogo). */
    fun consentRequest(uris: List<Uri>): PendingIntent =
        MediaStore.createWriteRequest(context.contentResolver, uris)

    /** Escribe [changes] en la pista. Bloqueante. */
    fun write(track: Track, target: Uri, changes: Map<TagField, String>) {
        val resolver = context.contentResolver
        val dir = File(context.cacheDir, "tag-edit").apply { mkdirs() }
        val tmp = File(dir, "edit.${extensionOf(track)}")
        try {
            resolver.openInputStream(target)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                ?: throw IOException("no se pudo leer ${track.fileName}")
            TagFiles.write(tmp, changes)
            val written = resolver.openOutputStream(target, "wt")?.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("no se pudo escribir ${track.fileName}")
            if (written != tmp.length()) throw IOException("escritura incompleta: ${track.fileName}")
        } finally {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    /** Pide a MediaStore releer los archivos para que la biblioteca vea las etiquetas nuevas. */
    suspend fun rescan(tracks: List<Track>) {
        val paths = tracks.mapNotNull(::absolutePath).distinct()
        if (paths.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            val pending = AtomicInteger(paths.size)
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null) { _, _ ->
                if (pending.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }

    /** "/storage/emulated/0/Music/Álbum/01.flac", a partir de la carpeta SAF y el nombre. */
    private fun absolutePath(track: Track): String? {
        val folder = track.folderDocId ?: return null
        if (track.fileName.isBlank()) return null
        val volume = folder.substringBefore(':')
        val path = folder.substringAfter(':', "").trim('/')
        val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        return listOf(base, path, track.fileName).filter { it.isNotEmpty() }.joinToString("/")
    }

    private fun extensionOf(track: Track): String =
        track.fileName.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }
            ?: when (track.mimeType) {
                "audio/mpeg" -> "mp3"
                "audio/flac", "audio/x-flac" -> "flac"
                "audio/mp4", "audio/x-m4a", "audio/alac" -> "m4a"
                else -> "wav"
            }

    private fun LibraryLocation.canWrite(context: Context): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == treeUri && it.isWritePermission }
}
