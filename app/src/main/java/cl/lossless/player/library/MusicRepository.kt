package cl.lossless.player.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val mimeType: String,
) {
    /** Etiqueta según el contenedor. Un .m4a puede ser ALAC (sin pérdida) o AAC (con pérdida);
     *  el códec real se detecta al reproducir (ver AudioProbe). */
    val containerLabel: String
        get() = when (mimeType) {
            "audio/mpeg" -> "MP3"
            "audio/flac", "audio/x-flac" -> "FLAC"
            else -> "M4A"
        }

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uri.toString())   // el servicio reconstruye la URI desde aquí
        .setUri(uri)
        .setMimeType(mimeType)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}

class MusicRepository(private val context: Context) {

    private val supportedMimes = arrayOf(
        "audio/mpeg",                       // MP3
        "audio/flac", "audio/x-flac",       // FLAC
        "audio/mp4", "audio/x-m4a", "audio/alac", // ALAC (contenedor MP4/M4A)
    )

    suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        val placeholders = supportedMimes.joinToString(",") { "?" }
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.MIME_TYPE} IN ($placeholders)"
        val sort = "${MediaStore.Audio.Media.ARTIST} ASC, ${MediaStore.Audio.Media.ALBUM} ASC, " +
            "${MediaStore.Audio.Media.TRACK} ASC"

        val result = mutableListOf<Track>()
        context.contentResolver.query(collection, projection, selection, supportedMimes, sort)
            ?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    result += Track(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        title = c.getString(titleCol) ?: "Sin título",
                        artist = c.getString(artistCol) ?: "Desconocido",
                        album = c.getString(albumCol) ?: "",
                        durationMs = c.getLong(durCol),
                        mimeType = c.getString(mimeCol) ?: "",
                    )
                }
            }
        result
    }
}
