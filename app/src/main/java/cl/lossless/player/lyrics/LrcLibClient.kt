package cl.lossless.player.lyrics

import cl.lossless.player.covers.TextMatch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.min

/**
 * Cliente de LRCLIB (https://lrclib.net): base de datos comunitaria y gratuita de letras
 * sincronizadas, sin clave de API. Idea y estrategia tomadas de Lyricslay (MIT).
 */
class LrcLibClient(
    private val userAgent: String = USER_AGENT,
    private val transport: Transport = HttpTransport,
    private val sleep: (Long) -> Unit = Thread::sleep,
) {

    /** Respuesta HTTP mínima: código, cuerpo y "Retry-After" en segundos (si vino). */
    data class Response(val code: Int, val body: String?, val retryAfterSec: Long? = null)

    fun interface Transport {
        /** Lanza IOException si no hubo respuesta (sin red, DNS, TLS, tiempo agotado). */
        fun get(url: String, userAgent: String): Response
    }

    /**
     * LRCLIB respondió, pero con un error suyo (saturado, caído o rechazando la petición).
     * Es una IOException para que quien no distinga lo trate como un fallo reintentable.
     */
    class ServerError(val code: Int) : IOException("LRCLIB respondió HTTP $code")

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

    /**
     * Cuerpo de la respuesta; null si no hay resultado (404) o la petición no sirve (400, p. ej.
     * una duración fuera de rango). Ante "saturado" (503, 429) o un 5xx pasajero reintenta
     * respetando Retry-After. Lanza [ServerError] si LRCLIB sigue fallando e IOException si no
     * hay red.
     */
    private fun request(endpoint: String, params: Map<String, String>): String? {
        val query = params.entries.joinToString("&") { (k, v) ->
            "$k=" + URLEncoder.encode(v, Charsets.UTF_8.name())
        }
        val url = "$BASE/$endpoint?$query"
        var attempt = 0
        while (true) {
            val response = transport.get(url, userAgent)
            when (val code = response.code) {
                HttpURLConnection.HTTP_OK -> return response.body
                HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_BAD_REQUEST -> return null
                in RETRYABLE -> {
                    if (attempt++ >= MAX_RETRIES) throw ServerError(code)
                    val waitSec = min(response.retryAfterSec ?: attempt.toLong(), MAX_WAIT_SEC)
                    sleep(waitSec.coerceAtLeast(1) * 1000)
                }
                else -> throw ServerError(code)
            }
        }
    }

    /** Conexión real con HttpURLConnection. */
    private object HttpTransport : Transport {
        override fun get(url: String, userAgent: String): Response {
            val connection = URL(url).openConnection() as HttpURLConnection
            return try {
                connection.connectTimeout = 8_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                val body = if (code == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else null
                Response(code, body, connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull())
            } finally {
                connection.disconnect()
            }
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
        private const val MAX_RETRIES = 2
        private const val MAX_WAIT_SEC = 3L
        /** Saturado o caído momentáneamente: vale la pena reintentar. */
        private val RETRYABLE = setOf(429, 500, 502, 503, 504)
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
