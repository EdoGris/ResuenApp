package cl.lossless.player.covers

import java.text.Normalizer
import kotlin.math.max

/** Comparación tolerante de títulos y artistas para decidir si una carátula corresponde. */
object TextMatch {

    private val editionNoise = Regex(
        "[(\\[][^)\\]]*(remaster|deluxe|edition|edición|version|versión|bonus|expanded|" +
            "anniversary|aniversario|explicit|hi-?res|24.?bit|flac)[^)\\]]*[)\\]]",
        RegexOption.IGNORE_CASE
    )
    private val diacritics = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^\\p{L}\\p{N}]+")
    private val various = setOf(
        "various artists", "various", "varios artistas", "va", "desconocido", "unknown", ""
    )

    fun normalize(s: String): String {
        var t = s.replace(editionNoise, " ").lowercase()
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replace(diacritics, "")
        t = t.replace("&", " and ").replace(" y ", " and ")
        return t.replace(nonAlnum, " ").trim().replace(Regex("\\s+"), " ")
            .removePrefix("the ")
    }

    fun isVarious(artist: String) = normalize(artist) in various

    /** 0.0 = nada que ver, 1.0 = idéntico (tras normalizar). */
    fun similarity(a: String, b: String): Double {
        val x = normalize(a)
        val y = normalize(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val edit = 1.0 - levenshtein(x, y).toDouble() / max(x.length, y.length)
        val tx = x.split(' ').toSet()
        val ty = y.split(' ').toSet()
        val tokens = (tx intersect ty).size.toDouble() / (tx union ty).size
        return max(edit, tokens)
    }

    fun matchScore(album: String, artist: String, candAlbum: String, candArtist: String): Double =
        if (isVarious(artist)) similarity(album, candAlbum)
        else 0.65 * similarity(album, candAlbum) + 0.35 * similarity(artist, candArtist)

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }
}
