package cl.lossless.player.library

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Lista de reproducción local; guarda las URIs de las pistas. */
data class Playlist(val id: String, val name: String, val trackUris: List<String>) {
    val isFavorites: Boolean get() = id == FAVORITES_ID

    companion object {
        const val FAVORITES_ID = "favorites"
    }
}

/** Persistencia simple en SharedPreferences (JSON). "Favoritos" siempre existe y va primero. */
class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): List<Playlist> {
        val stored = runCatching {
            val array = JSONArray(prefs.getString(KEY, "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                val uris = o.getJSONArray("tracks")
                Playlist(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    trackUris = (0 until uris.length()).map(uris::getString),
                )
            }
        }.getOrDefault(emptyList())
        val favorites = stored.firstOrNull { it.isFavorites }
            ?: Playlist(Playlist.FAVORITES_ID, "Favoritos", emptyList())
        return listOf(favorites) + stored.filterNot { it.isFavorites }
    }

    fun save(playlists: List<Playlist>) {
        val array = JSONArray()
        playlists.forEach { p ->
            array.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("tracks", JSONArray(p.trackUris))
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "playlists"
    }
}
