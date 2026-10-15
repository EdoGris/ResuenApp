package cl.lossless.player.sync

import cl.lossless.player.library.AlbumGroup
import cl.lossless.player.library.Track
import org.json.JSONArray
import org.json.JSONObject

/** Por qué redes puede trabajar la cola de sincronización. */
enum class NetworkMode(val label: String) {
    WIFI("Solo Wi-Fi"),
    MOBILE("Solo datos"),
    ANY("Wi-Fi y datos"),
}

/** Algo que hay que buscar o sincronizar en internet. */
sealed interface SyncRequest {
    /** Identifica la petición: pedir dos veces lo mismo no duplica la cola. */
    val id: String
    /** Nombre para mostrar. */
    val label: String

    /** Buscar la carátula de un álbum que no tiene. */
    data class Cover(
        val albumKey: String,
        val title: String,
        val artist: String,
        val folderDocId: String?,
        val singleAlbumFolder: Boolean,
    ) : SyncRequest {
        override val id get() = "cover:$albumKey"
        override val label get() = title
    }

    /** Buscar la letra de una pista y, si no trae tiempos, sincronizarla. */
    data class Lyrics(
        val trackUri: String,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val mimeType: String,
        val fileName: String,
        val folderKey: String,
        val folderName: String,
        val folderDocId: String?,
    ) : SyncRequest {
        override val id get() = lyricsId(trackUri)
        override val label get() = title
    }

    companion object {
        fun lyricsId(trackUri: String) = "lyrics:$trackUri"

        fun cover(album: AlbumGroup) =
            Cover(album.key, album.title, album.artist, album.folderDocId, album.singleAlbumFolder)

        fun lyrics(track: Track) = Lyrics(
            trackUri = track.uri.toString(),
            title = track.title,
            artist = track.artist,
            album = track.album,
            durationMs = track.durationMs,
            mimeType = track.mimeType,
            fileName = track.fileName,
            folderKey = track.folderKey,
            folderName = track.folderName,
            folderDocId = track.folderDocId,
        )
    }
}

data class SyncItem(
    val request: SyncRequest,
    val running: Boolean = false,
    /** Reenviar al servidor aunque antes haya fallado (lo pidió el usuario). */
    val retryFailed: Boolean = false,
    /** Veces que falló por la red; al llegar a [SyncQueueOps.MAX_ATTEMPTS] se descarta. */
    val attempts: Int = 0,
    /** Etapa en curso (p. ej. el nombre de [cl.lossless.player.lyrics.LyricsRepository.AlignStage]). */
    val stage: String? = null,
) {
    val id get() = request.id
}

/** Cómo terminó una petición. */
enum class SyncOutcome { DONE, NOT_NEEDED, NO_MATCH, FAILED }

data class SyncEvent(val request: SyncRequest, val outcome: SyncOutcome, val message: String? = null)

/** Operaciones sobre la lista de la cola (sin Android, para poder probarlas). */
object SyncQueueOps {
    const val MAX_ATTEMPTS = 5

    /**
     * Agrega [requests] sin duplicar. Con [urgent] van al principio (las que ya estaban se
     * adelantan), si no, al final en el orden dado.
     */
    fun enqueue(
        items: List<SyncItem>,
        requests: List<SyncRequest>,
        urgent: Boolean = false,
        retryFailed: Boolean = false,
    ): List<SyncItem> {
        val ids = requests.map { it.id }.toSet()
        val existing = items.associateBy { it.id }
        val added = requests.distinctBy { it.id }.map { request ->
            existing[request.id]?.let { it.copy(retryFailed = it.retryFailed || retryFailed) }
                ?: SyncItem(request, retryFailed = retryFailed)
        }
        return if (urgent) {
            // Lo que está en curso sigue primero.
            val running = items.filter { it.running && it.id !in ids }
            running + added + items.filter { !it.running && it.id !in ids }
        } else {
            val newOnes = added.filter { it.id !in existing }
            items.map { item -> added.firstOrNull { it.id == item.id } ?: item } + newOnes
        }
    }

    /** Primera petición pendiente que no esté en [skip]. */
    fun next(items: List<SyncItem>, skip: Set<String> = emptySet()): SyncItem? =
        items.firstOrNull { !it.running && it.id !in skip }

    /** Falló por la red: vuelve al final; tras [MAX_ATTEMPTS] intentos se descarta (devuelve false). */
    fun requeue(items: List<SyncItem>, id: String): Pair<List<SyncItem>, Boolean> {
        val item = items.firstOrNull { it.id == id } ?: return items to false
        val rest = items.filter { it.id != id }
        val attempts = item.attempts + 1
        if (attempts >= MAX_ATTEMPTS) return rest to false
        return rest + item.copy(running = false, stage = null, attempts = attempts) to true
    }

    fun toJson(items: List<SyncItem>): String = JSONArray().apply {
        items.forEach { item ->
            val json = JSONObject()
                .put("retryFailed", item.retryFailed)
                .put("attempts", item.attempts)
            when (val r = item.request) {
                is SyncRequest.Cover -> json.put("type", "cover")
                    .put("albumKey", r.albumKey)
                    .put("title", r.title)
                    .put("artist", r.artist)
                    .put("folderDocId", r.folderDocId ?: JSONObject.NULL)
                    .put("singleAlbumFolder", r.singleAlbumFolder)
                is SyncRequest.Lyrics -> json.put("type", "lyrics")
                    .put("trackUri", r.trackUri)
                    .put("title", r.title)
                    .put("artist", r.artist)
                    .put("album", r.album)
                    .put("durationMs", r.durationMs)
                    .put("mimeType", r.mimeType)
                    .put("fileName", r.fileName)
                    .put("folderKey", r.folderKey)
                    .put("folderName", r.folderName)
                    .put("folderDocId", r.folderDocId ?: JSONObject.NULL)
            }
            put(json)
        }
    }.toString()

    /** Lo que estaba "en curso" al guardarse vuelve a quedar pendiente. */
    fun fromJson(text: String): List<SyncItem> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { i ->
            val json = array.getJSONObject(i)
            fun str(key: String) = json.optString(key, "")
            fun nullable(key: String) = if (json.isNull(key)) null else json.optString(key)
            val request = when (json.optString("type")) {
                "cover" -> SyncRequest.Cover(
                    albumKey = str("albumKey"),
                    title = str("title"),
                    artist = str("artist"),
                    folderDocId = nullable("folderDocId"),
                    singleAlbumFolder = json.optBoolean("singleAlbumFolder"),
                )
                "lyrics" -> SyncRequest.Lyrics(
                    trackUri = str("trackUri"),
                    title = str("title"),
                    artist = str("artist"),
                    album = str("album"),
                    durationMs = json.optLong("durationMs"),
                    mimeType = str("mimeType"),
                    fileName = str("fileName"),
                    folderKey = str("folderKey"),
                    folderName = str("folderName"),
                    folderDocId = nullable("folderDocId"),
                )
                else -> null
            } ?: return@mapNotNull null
            SyncItem(request, retryFailed = json.optBoolean("retryFailed"), attempts = json.optInt("attempts"))
        }
    }
}
