package cl.lossless.player.covers

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import cl.lossless.player.library.AlbumGroup
import cl.lossless.player.library.LibraryLocation
import java.io.ByteArrayOutputStream

class CoverManager(
    private val context: Context,
    private val store: CoverStore = CoverStore(context),
    private val search: CoverSearch = CoverSearch(),
) {
    /** Nombres habituales de carátula en carpetas de música, en orden de preferencia. */
    private val folderCoverNames = listOf("cover", "folder", "front", "albumart", "album")
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Busca una carátula sin usar internet, en este orden:
     * caché de la app → imagen en la carpeta del álbum → imagen incrustada en el primer archivo.
     */
    fun findLocal(album: AlbumGroup, location: LibraryLocation?): CoverRef? {
        store.get(album.key)?.let { return it }
        readFolderImage(album, location)?.let { return store.save(album.key, it) }
        readEmbedded(album)?.let { return store.save(album.key, it) }
        return null
    }

    fun searchOnline(album: AlbumGroup): List<CoverCandidate> =
        search.search(album.title, album.artist)

    /** cover.jpg solo se escribe si la carpeta es accesible y contiene un único álbum. */
    fun canWriteToFolder(album: AlbumGroup, location: LibraryLocation?): Boolean =
        location != null && album.singleAlbumFolder &&
            album.folderDocId?.let(location::contains) == true

    /** Descarga y asocia la carátula elegida. Devuelve la referencia y si se escribió cover.jpg. */
    fun apply(
        album: AlbumGroup,
        candidate: CoverCandidate,
        location: LibraryLocation?,
        writeToFolder: Boolean,
    ): Pair<CoverRef, Boolean> {
        val bytes = search.download(candidate)
        return associate(album, bytes, location, writeToFolder)
    }

    /**
     * Asocia una imagen elegida por el usuario (galería o archivos) al álbum.
     * Devuelve la referencia y si se escribió cover.jpg.
     */
    fun applyLocal(
        album: AlbumGroup,
        image: Uri,
        location: LibraryLocation?,
        writeToFolder: Boolean,
    ): Pair<CoverRef, Boolean> {
        val bytes = readLocalImage(image) ?: error("No se pudo leer la imagen")
        return associate(album, bytes, location, writeToFolder)
    }

    /** Guarda en la caché de la app y, si se puede, como cover.jpg en la carpeta del álbum. */
    private fun associate(
        album: AlbumGroup,
        bytes: ByteArray,
        location: LibraryLocation?,
        writeToFolder: Boolean,
    ): Pair<CoverRef, Boolean> {
        val ref = store.save(album.key, bytes)
        val written = writeToFolder && canWriteToFolder(album, location) &&
            runCatching { writeFolderCover(album, location!!, bytes) }.getOrDefault(false)
        return ref to written
    }

    /** Lee la imagen; si es muy grande o no es JPEG la reduce/convierte para no inflar cover.jpg. */
    private fun readLocalImage(uri: Uri): ByteArray? {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null   // no es una imagen
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        val isJpeg = bounds.outMimeType == "image/jpeg"
        if (isJpeg && longest <= MAX_COVER_PX) return raw

        var sample = 1
        while (longest / (sample * 2) >= MAX_COVER_PX) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null
        val scale = MAX_COVER_PX.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true
            ).also { if (it !== decoded) decoded.recycle() }
        } else decoded
        return ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
            bitmap.recycle()
        }.toByteArray()
    }

    /** Coincidencia suficientemente segura para asignarla sin preguntar. */
    fun bestAutomatic(candidates: List<CoverCandidate>): CoverCandidate? =
        candidates.firstOrNull()?.takeIf { it.score >= AUTO_ACCEPT_SCORE }

    // ---------- Lectura local ----------

    private fun readFolderImage(album: AlbumGroup, location: LibraryLocation?): ByteArray? {
        val loc = location ?: return null
        val folderId = album.folderDocId?.takeIf(loc::contains) ?: return null
        val images = listChildren(loc, folderId).filter { (_, name) ->
            name.substringAfterLast('.', "").lowercase() in imageExtensions
        }
        val chosen = folderCoverNames.firstNotNullOfOrNull { wanted ->
            images.firstOrNull { (_, name) -> name.substringBeforeLast('.').equals(wanted, true) }
        } ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(loc.treeUri, chosen.first)
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
    }

    private fun readEmbedded(album: AlbumGroup): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, album.tracks.first().uri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun listChildren(loc: LibraryLocation, folderId: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        runCatching {
            context.contentResolver.query(
                loc.childrenUri(folderId),
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                ),
                null, null, null
            )?.use { c -> while (c.moveToNext()) out += c.getString(0) to (c.getString(1) ?: "") }
        }
        return out
    }

    // ---------- Escritura de cover.jpg ----------

    private fun writeFolderCover(album: AlbumGroup, loc: LibraryLocation, bytes: ByteArray): Boolean {
        val folderId = album.folderDocId ?: return false
        val folderUri = loc.documentUri(folderId) ?: return false
        val resolver = context.contentResolver
        val existing = listChildren(loc, folderId)
            .firstOrNull { (_, name) -> name.equals(FOLDER_COVER_NAME, ignoreCase = true) }
        val target = existing?.let { DocumentsContract.buildDocumentUriUsingTree(loc.treeUri, it.first) }
            ?: DocumentsContract.createDocument(resolver, folderUri, "image/jpeg", FOLDER_COVER_NAME)
            ?: return false
        resolver.openOutputStream(target, "wt")?.use { it.write(asJpeg(bytes)) } ?: return false
        return true
    }

    /** cover.jpg debe ser JPEG; si la imagen viene en PNG/WebP se convierte. */
    private fun asJpeg(bytes: ByteArray): ByteArray {
        val isJpeg = bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        if (isJpeg) return bytes
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        return ByteArrayOutputStream().also {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)
            bitmap.recycle()
        }.toByteArray()
    }

    private companion object {
        const val FOLDER_COVER_NAME = "cover.jpg"
        const val AUTO_ACCEPT_SCORE = 0.9
        const val MAX_COVER_PX = 1600
    }
}
