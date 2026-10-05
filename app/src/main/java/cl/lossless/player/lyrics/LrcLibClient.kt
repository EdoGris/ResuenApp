package cl.lossless.player.lyrics

import cl.lossless.player.covers.TextMatch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Cliente de LRCLIB (https://lrclib.net): base de datos comunitaria y gratuita de letras
 * sincronizadas, sin clave de API. Idea y estrategia tomadas de Lyricslay (MIT).
 */
class LrcLibClient(private val userAgent: String = USER_AGENT) {

    data class Record(
        val trackName: String,
        val artistName: String,
        val albumName: String,
        val durationSec: Double,
        val instrumental: Boolean,
        val plainLyrics: String?,
        val syncedLyrics: String?,
    ) {
        val hasLyrics: Boolean get() = !syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank()
    }

    /** Búsqueda exacta (título, artista y duración ±2 s). null si no existe. */
    fun get(title: String, artist: String, durationSec: Long): Record? {
        // Sin álbum a propósito: LRCLIB lo usa para filtrar y los nombres de recopilatorios
        // o ediciones ("… Box Set") harían fallar la búsqueda exacta.
        val params = mapOf(
            "track_name" to title,
            "artist_name" to artist,
            "duration" to durationSec.toString(),
        )
        val body = request("get", params) ?: return null
        return JSONObject(body).toRecord()
    }

    /** Búsqueda aproximada; puede devolver varias versiones de la misma canción. */
    fun search(title: String, artist: String): List<Record> {
        val params = buildMap {
            put("track_name", title)
            if (artist.isNotBlank()) put("artist_name", artist)
        }
        val body = request("search", params) ?: return emptyList()
        val array = JSONArray(body)
        return (0 until array.length()).map { array.getJSONObject(it).toRecord() }
    }

    /** Cuerpo de la respuesta, null si es 404. Lanza IOException ante errores de red. */
    private fun request(endpoint: String, params: Map<String, String>): String? {
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=" + URLEncoder.encode(v, Charsets.UTF_8.name())
        }
        val connection = URL("$BASE/$endpoint?$query").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException("LRCLIB respondió HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toRecord() = Record(
        trackName = optString("trackName"),
        artistName = optString("artistName"),
        albumName = optString("albumName"),
        durationSec = optDouble("duration", 0.0),
        instrumental = optBoolean("instrumental", false),
        plainLyrics = optStringOrNull("plainLyrics"),
        syncedLyrics = optStringOrNull("syncedLyrics"),
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        private const val BASE = "https://lrclib.net/api"
        /** LRCLIB pide identificar la app en el User-Agent. */
        const val USER_AGENT = "ResuenApp/1.0 (Android lossless music player)"

        /**
         * Elige el resultado que mejor coincide: título (y artista si es confiable), con un
         * pequeño premio a las letras sincronizadas y castigo si la duración no cuadra.
         * null si nada se parece lo suficiente.
         */
        fun pickBest(records: List<Record>, title: String, artist: String, durationSec: Long): Record? =
            records
                .filter { it.hasLyrics || it.instrumental }
                .map { r ->
                    val titleScore = TextMatch.similarity(title, r.trackName)
                    val artistScore = if (artist.isBlank()) titleScore else TextMatch.similarity(artist, r.artistName)
                    var score = if (artist.isBlank()) titleScore else 0.6 * titleScore + 0.4 * artistScore
                    if (!r.syncedLyrics.isNullOrBlank()) score += 0.05
                    if (durationSec > 0 && r.durationSec > 0 && abs(r.durationSec - durationSec) > 5) score -= 0.15
                    Triple(r, score, titleScore)
                }
                .filter { (_, score, titleScore) -> titleScore >= 0.6 && score >= 0.6 }
                .maxByOrNull { it.second }
                ?.first
    }
}
