package cl.lossless.player.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cl.lossless.player.playlist.M3u
import cl.lossless.player.playlist.Playlist
import java.io.File

/**
 * Listas de reproducción: un .m3u8 por lista en la carpeta de datos de la app
 * (%APPDATA%\ResuenApp\playlists o ~/Library/Application Support/ResuenApp/playlists).
 * El id es el nombre del archivo, así renombrar una lista no cambia su archivo.
 */
object DesktopPlaylists {

    private val dir: File by lazy { File(AppDirs.data, "playlists").apply { mkdirs() } }

    var all by mutableStateOf(load())
        private set

    operator fun get(id: String): Playlist? = all.firstOrNull { it.id == id }

    private fun load(): List<Playlist> =
        (dir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("m3u8", "m3u") } ?: emptyArray())
            .mapNotNull { file ->
                runCatching {
                    val (name, paths) = M3u.parse(file.readText(Charsets.UTF_8)) { File(file.parentFile, it).path }
                    Playlist(file.name, name ?: file.nameWithoutExtension, paths)
                }.getOrNull()
            }
            .sortedBy { it.name.lowercase() }

    fun create(name: String, paths: List<String> = emptyList()): Playlist {
        val stem = M3u.fileStem(name)
        var file = File(dir, "$stem.m3u8")
        var n = 2
        while (file.exists()) file = File(dir, "$stem ($n).m3u8").also { n++ }
        val list = Playlist(file.name, name.trim(), paths.distinct())
        save(list)
        return list
    }

    fun rename(id: String, name: String) = update(id) { it.copy(name = name.trim()) }

    /** Añade al final las que aún no están en la lista. */
    fun add(id: String, paths: List<String>) = update(id) { list -> list.copy(paths = list.paths + paths.filter { it !in list.paths }.distinct()) }

    fun remove(id: String, index: Int) = update(id) { list -> list.copy(paths = list.paths.filterIndexed { i, _ -> i != index }) }

    fun move(id: String, from: Int, to: Int) = update(id) { list ->
        if (from !in list.paths.indices) return@update list
        val paths = list.paths.toMutableList()
        val item = paths.removeAt(from)
        paths.add(to.coerceIn(0, paths.size), item)
        list.copy(paths = paths)
    }

    fun delete(id: String) {
        File(dir, id).delete()
        all = all.filterNot { it.id == id }
    }

    private fun update(id: String, change: (Playlist) -> Playlist) {
        val list = get(id) ?: return
        save(change(list))
    }

    private fun save(list: Playlist) {
        runCatching { File(dir, list.id).writeText(M3u.write(list.name, list.paths), Charsets.UTF_8) }
        all = (all.filterNot { it.id == list.id } + list).sortedBy { it.name.lowercase() }
    }
}
