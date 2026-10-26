package cl.lossless.player.library

/**
 * Agrupa pistas en álbumes según sus etiquetas, aunque estén en carpetas distintas.
 *
 * Primero se agrupan por carpeta y nombre de álbum (como siempre). Después, los grupos de
 * carpetas distintas con el mismo álbum y el mismo artista (el del álbum si lo tiene, o el
 * único artista de sus pistas) se juntan en uno. Así, al asignar el mismo álbum y artista a
 * canciones sueltas, aparecen como un solo álbum. Los recopilatorios sin artista del álbum
 * (varios artistas) siguen separados por carpeta, para no mezclar dos "Grandes éxitos" distintos.
 */
object AlbumGrouping {

    /** Una pista vista solo por lo que importa para agruparla. */
    class Fields(val folder: String, val album: String, val albumArtist: String, val artist: String)

    /**
     * Devuelve la clave del álbum de cada pista, en el mismo orden que [items]. Dos pistas
     * con la misma clave son del mismo álbum.
     */
    fun <T> keys(items: List<T>, fields: (T) -> Fields): List<String> {
        val all = items.map(fields)
        val byFolder = all.indices.groupBy { i -> folderKey(all[i]) }
        val keys = arrayOfNulls<String>(items.size)
        for ((folderKey, indices) in byFolder) {
            val album = norm(all[indices.first()].album)
            val identity = if (album.isEmpty()) null else identity(indices.map { all[it] })
            val key = if (identity != null) "name:$album|$identity" else folderKey
            indices.forEach { keys[it] = key }
        }
        return keys.map { it!! }
    }

    /** Agrupa [items] por álbum, manteniendo el orden en que aparece cada álbum por primera vez. */
    fun <T> group(items: List<T>, fields: (T) -> Fields): List<Pair<String, List<T>>> {
        val keys = keys(items, fields)
        val groups = LinkedHashMap<String, MutableList<T>>()
        items.forEachIndexed { i, item -> groups.getOrPut(keys[i]) { mutableListOf() } += item }
        return groups.map { (k, v) -> k to v }
    }

    private fun folderKey(f: Fields) = "dir:${f.folder}|${norm(f.album)}"

    /** Artista que identifica al grupo, o null si tiene varios (recopilatorio). */
    private fun identity(tracks: List<Fields>): String? {
        val albumArtists = tracks.map { norm(it.albumArtist) }.filter { it.isNotEmpty() }.distinct()
        if (albumArtists.size == 1) return albumArtists.single()
        if (albumArtists.size > 1) return null
        val artists = tracks.map { norm(it.artist) }.filter { it.isNotEmpty() }.distinct()
        return artists.singleOrNull()
    }

    private fun norm(s: String) = s.trim().lowercase()
}
