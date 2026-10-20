package cl.lossless.player.library

/**
 * Un álbum dentro de una carpeta. La carátula se asocia a este grupo.
 * Si la carpeta contiene un solo álbum (lo habitual), la carátula también puede guardarse
 * en la carpeta como cover.jpg.
 */
data class AlbumGroup(
    val key: String,
    val title: String,
    val artist: String,
    val folderName: String,
    val folderDocId: String?,
    val singleAlbumFolder: Boolean,
    val tracks: List<Track>,
)

/** Clave del álbum al que pertenece la pista (coincide con [AlbumGroup.key]). */
val Track.albumKey: String get() = "$folderKey|${album.trim().lowercase()}"

/** [variousArtists] es el texto visible (traducido) para álbumes con más de un artista. */
fun List<Track>.toAlbumGroups(variousArtists: String = "Varios artistas"): List<AlbumGroup> =
    groupBy { it.folderKey }.flatMap { (folderKey, folderTracks) ->
        val byAlbum = folderTracks.groupBy { it.album.trim().lowercase() }
        byAlbum.map { (_, tracks) ->
            val first = tracks.first()
            val artists = tracks.map { it.albumArtist.ifBlank { it.artist } }.distinct()
            AlbumGroup(
                key = first.albumKey,
                title = first.album.ifBlank { first.folderName },
                artist = artists.singleOrNull() ?: variousArtists,
                folderName = first.folderName,
                folderDocId = first.folderDocId,
                singleAlbumFolder = byAlbum.size == 1,
                tracks = tracks.sortedWith(compareBy({ it.trackNumber }, { it.title })),
            )
        }
    }.sortedBy { "${it.artist} ${it.title}".lowercase() }
