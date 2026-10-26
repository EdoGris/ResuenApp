package cl.lossless.player.covers

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class CoverCandidate(
    val source: String,
    val title: String,
    val artist: String,
    val year: String?,
    val previewUrl: String,
    val fullUrl: String,
    val score: Double,
)

/**
 * Búsqueda de carátulas en servicios públicos que no requieren clave:
 *  - MusicBrainz + Cover Art Archive (fuente principal, comunitaria).
 *  - Deezer (respaldo, buena cobertura de música comercial; hasta 1000×1000).
 */
class CoverSearch {

    // MusicBrainz exige un User-Agent identificable con un contacto real.
    // Cambia el correo por el tuyo.
    private val userAgent = "LosslessPlayer/1.0 ( ediazf@gmail.com )"
    private var lastMusicBrainzCall = 0L

    fun search(album: String, artist: String): List<CoverCandidate> {
        val results = mutableListOf<CoverCandidate>()
        runCatching { results += searchMusicBrainz(album, artist) }
        runCatching { results += searchDeezer(album, artist) }
        return results.distinctBy { it.fullUrl }.sortedByDescending { it.score }.take(12)
    }

    private fun searchMusicBrainz(album: String, artist: String): List<CoverCandidate> {
        val query = buildString {
            append("releasegroup:\"").append(lucene(album)).append('"')
            if (!TextMatch.isVarious(artist)) append(" AND artist:\"").append(lucene(artist)).append('"')
        }
        val json = JSONObject(
            getText("https://musicbrainz.org/ws/2/release-group/?fmt=json&limit=8&query=${enc(query)}",
                musicBrainz = true)
        )
        val groups = json.optJSONArray("release-groups") ?: return emptyList()
        val found = mutableListOf<CoverCandidate>()
        for (i in 0 until groups.length()) {
            val g = groups.getJSONObject(i)
            val id = g.getString("id")
            val title = g.optString("title")
            val credits = g.optJSONArray("artist-credit")
            val candArtist = buildString {
                if (credits != null) for (k in 0 until credits.length()) {
                    val credit = credits.getJSONObject(k)
                    append(credit.optString("name")).append(credit.optString("joinphrase"))
                }
            }
            val score = TextMatch.matchScore(album, artist, title, candArtist)
            if (score < 0.5) continue
            val base = "https://coverartarchive.org/release-group/$id"
            // No todos los álbumes de MusicBrainz tienen imagen: se verifica antes de ofrecerla.
            if (found.size < 5 && exists("$base/front-250")) {
                found += CoverCandidate(
                    source = "MusicBrainz",
                    title = title,
                    artist = candArtist,
                    year = g.optString("first-release-date").take(4).ifBlank { null },
                    previewUrl = "$base/front-250",
                    fullUrl = "$base/front-1200",
                    score = score,
                )
            }
        }
        return found
    }

    private fun searchDeezer(album: String, artist: String): List<CoverCandidate> {
        val q = if (TextMatch.isVarious(artist)) album else "$artist $album"
        val json = JSONObject(getText("https://api.deezer.com/search/album?limit=8&q=${enc(q)}"))
        val data = json.optJSONArray("data") ?: return emptyList()
        val found = mutableListOf<CoverCandidate>()
        for (i in 0 until data.length()) {
            val a = data.getJSONObject(i)
            val title = a.optString("title")
            val candArtist = a.optJSONObject("artist")?.optString("name").orEmpty()
            val full = a.optString("cover_xl").ifBlank { a.optString("cover_big") }
            val preview = a.optString("cover_medium").ifBlank { full }
            if (full.isBlank()) continue
            val score = TextMatch.matchScore(album, artist, title, candArtist)
            if (score < 0.5) continue
            found += CoverCandidate("Deezer", title, candArtist, null, preview, full, score)
        }
        return found
    }

    /** Descarga la imagen en alta resolución (o la vista previa si la grande falla). */
    fun download(candidate: CoverCandidate): ByteArray =
        runCatching { getBytes(candidate.fullUrl) }.getOrElse { getBytes(candidate.previewUrl) }

    // ---------- HTTP ----------

    private fun open(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
        }

    @Synchronized
    private fun throttleMusicBrainz() {
        // Límite de MusicBrainz: 1 consulta por segundo.
        val wait = 1100 - (System.currentTimeMillis() - lastMusicBrainzCall)
        if (wait > 0) Thread.sleep(wait)
        lastMusicBrainzCall = System.currentTimeMillis()
    }

    private fun getText(url: String, musicBrainz: Boolean = false): String {
        if (musicBrainz) throttleMusicBrainz()
        val c = open(url).apply { setRequestProperty("Accept", "application/json") }
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun getBytes(url: String): ByteArray {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMAGE_BYTES) throw IOException("Imagen demasiado grande")
                    out.write(buf, 0, n)
                }
                return out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }

    private fun exists(url: String): Boolean = runCatching {
        val c = open(url, "HEAD")
        try { c.responseCode in 200..299 } finally { c.disconnect() }
    }.getOrDefault(false)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun lucene(s: String) = s.replace("\\", " ").replace("\"", " ")

    private companion object {
        const val MAX_IMAGE_BYTES = 12 * 1024 * 1024
    }
}
