package cl.lossless.player.lyrics

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente de Lyrics.ovh (https://lyrics.ovh): API comunitaria y gratuita, sin clave, que solo
 * entrega texto plano. Se usa como respaldo cuando LRCLIB no tiene la canción; la app luego
 * estima o alinea los tiempos como con cualquier letra sin marcas.
 *
 * Es un servicio de buena voluntad y a veces está lento o caído, así que no reintenta: ante
 * cualquier error se rinde y LyricsRepository sigue sin letra.
 */
class LyricsOvhClient(
    private val userAgent: String = LrcLibClient.USER_AGENT,
    private val transport: LrcLibClient.Transport = HttpTransport,
) {

    /** Lyrics.ovh respondió con un error suyo (distinto de "no hay letra"). */
    class ServerError(val code: Int) : IOException("Lyrics.ovh respondió HTTP $code")

    /**
     * Letra en texto plano, o null si no la tiene. Necesita artista y título: la API no busca
     * solo por título. Lanza IOException si no hubo respuesta o [ServerError] si falló.
     */
    fun get(artist: String, title: String): String? {
        if (artist.isBlank() || title.isBlank()) return null
        val url = "$BASE/${encodePath(artist)}/${encodePath(title)}"
        val response = transport.get(url, userAgent)
        return when (response.code) {
            HttpURLConnection.HTTP_OK -> response.body?.let(::parse)
            HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_BAD_REQUEST -> null
            else -> throw ServerError(response.code)
        }
    }

    /** Conexión real; tiempos cortos porque es solo un respaldo. */
    private object HttpTransport : LrcLibClient.Transport {
        override fun get(url: String, userAgent: String): LrcLibClient.Response {
            val connection = URL(url).openConnection() as HttpURLConnection
            return try {
                connection.connectTimeout = 6_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("User-Agent", userAgent)
                connection.setRequestProperty("Accept", "application/json")
                val code = connection.responseCode
                val body = if (code == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else null
                LrcLibClient.Response(code, body)
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        private const val BASE = "https://api.lyrics.ovh/v1"
        /** Encabezado que Lyrics.ovh antepone a algunas letras (viene de su fuente francesa). */
        private val HEADER = Regex("""^\s*Paroles de la chanson .*? par .*?(\r?\n|$)""")

        /** Segmento de ruta: URLEncoder pone "+" en los espacios, que en una ruta no valen. */
        fun encodePath(s: String): String =
            URLEncoder.encode(s.trim(), Charsets.UTF_8.name()).replace("+", "%20")

        /** {"lyrics": "..."} → texto limpio; null si viene vacío o con {"error": ...}. */
        fun parse(body: String): String? {
            val json = try { JSONObject(body) } catch (e: JSONException) { return null }
            if (json.isNull("lyrics")) return null
            return clean(json.optString("lyrics"))
        }

        /** Quita el encabezado, unifica saltos de línea y deja como mucho una línea en blanco. */
        fun clean(raw: String): String? =
            raw.replace("\r\n", "\n").replace('\r', '\n')
                .replace(HEADER, "")
                .lines().joinToString("\n") { it.trimEnd() }
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
                .takeIf { it.isNotEmpty() }
    }
}
