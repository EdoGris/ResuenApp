package cl.lossless.player.library

/**
 * Un álbum, casi siempre dentro de una carpeta (puede abarcar varias si sus pistas tienen el
 * mismo álbum y artista). La carátula se asocia a este grupo.
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

/**
 * Carpeta + álbum de la pista. Es la clave de los álbumes que caben en una carpeta (la gran
 * mayoría), y por eso de las carátulas guardadas; la de un álbum repartido en varias carpetas la
 * da cl.lossless.player.ui.LibraryUiState.albumKeys.
 */
val Track.albumKey: String get() = "$folderKey|${album.trim().lowercase()}"

private val Track.groupingFields get() = AlbumGrouping.Fields(folderKey, album, albumArtist, artist)

/** [variousArtists] es el texto visible (traducido) para álbumes con más de un artista. */
fun List<Track>.toAlbumGroups(variousArtists: String = "Varios artistas"): List<AlbumGroup> {
    val albumsPerFolder = groupBy { it.folderKey }.mapValues { (_, list) -> list.map { it.album.trim().lowercase() }.distinct().size }
    return AlbumGrouping.group(this) { it.groupingFields }.map { (groupKey, tracks) ->
        val first = tracks.first()
        val oneFolder = tracks.all { it.folderKey == first.folderKey }
        val artists = tracks.map { it.albumArtist.ifBlank { it.artist }.trim() }.distinctBy { it.lowercase() }
        AlbumGroup(
            // Un álbum de una sola carpeta conserva la clave de siempre (y su carátula guardada).
            key = if (oneFolder) first.albumKey else groupKey,
            title = first.album.ifBlank { first.folderName },
            artist = artists.singleOrNull() ?: variousArtists,
            folderName = first.folderName,
            folderDocId = first.folderDocId,
            // cover.jpg solo en una carpeta que tenga este álbum y nada más.
            singleAlbumFolder = oneFolder && albumsPerFolder[first.folderKey] == 1,
            tracks = tracks.sortedWith(compareBy({ it.trackNumber }, { it.title })),
        )
    }.sortedBy { "${it.artist} ${it.title}".lowercase() }
}
