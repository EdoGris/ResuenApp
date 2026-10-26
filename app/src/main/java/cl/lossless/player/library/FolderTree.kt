package cl.lossless.player.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cl.lossless.player.R

/** Una carpeta del explorador: subcarpetas y pistas que contiene directamente. */
data class FolderNode(
    val segments: List<String>,
    val subfolders: List<FolderEntry>,
    val tracks: List<Track>,
)

data class FolderEntry(
    val name: String,
    val segments: List<String>,
    val trackCount: Int,
    /** Modificación más reciente entre sus pistas (incluye subcarpetas). */
    val lastModified: Long = 0L,
)

/** Ruta de la carpeta de la pista como segmentos: ["primary", "Music", "Rock"]. */
val Track.folderSegments: List<String>
    get() {
        val id = folderDocId ?: folderKey
        val volume = id.substringBefore(':')
        val path = id.substringAfter(':', "").trim('/')
        return listOf(volume) + path.split('/').filter { it.isNotEmpty() }
    }

/** Contenido de la carpeta [segments] (vacío = raíz con los volúmenes). */
fun List<Track>.folderNode(segments: List<String>): FolderNode {
    val inside = map { it to it.folderSegments }
        .filter { (_, s) -> s.size >= segments.size && s.subList(0, segments.size) == segments }
    val (direct, deeper) = inside.partition { (_, s) -> s.size == segments.size }
    val subfolders = deeper
        .groupBy { (_, s) -> s[segments.size] }
        .map { (name, items) ->
            FolderEntry(name, segments + name, items.size, items.maxOf { it.first.dateModified })
        }
        .sortedBy { it.name.lowercase() }
    return FolderNode(
        segments = segments,
        subfolders = subfolders,
        tracks = direct.map { it.first }.sortedWith(compareBy({ it.trackNumber }, { it.title })),
    )
}

/** "/storage/emulated/0/Music/Rock" a partir de los segmentos. */
fun folderDisplayPath(segments: List<String>): String {
    if (segments.isEmpty()) return "/storage"
    val volume = segments.first()
    val base = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
    return (listOf(base) + segments.drop(1)).joinToString("/")
}

@Composable
fun volumeLabel(volume: String): String =
    if (volume == "primary") stringResource(R.string.settings_volume_internal)
    else stringResource(R.string.settings_volume_external, volume)
