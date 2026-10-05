/*
 * Basado en src/trackMetadata.js de Lyricslay — Copyright (c) 2026 Arzatar — Licencia MIT
 * (https://github.com/Arzatar/Lyricslay). Ver THIRD_PARTY_NOTICES.md.
 */
package cl.lossless.player.lyrics

/**
 * Limpia título y artista antes de buscar la letra. Para metadatos ya limpios no cambia nada.
 *
 * Casos típicos de bibliotecas locales:
 *  - "Rage Against The Machine - Bombtrack" con artista "Desconocido" → artista y título separados.
 *  - "Song (Official Video)", "Canción (letra)" → se quitan los añadidos al final.
 *  - "01. Intro" (título tomado del nombre de archivo) → "Intro".
 */
object TrackMetadataCleaner {

    data class Cleaned(val title: String, val artist: String)

    /** Se quita un "(...)"/"[...]" final solo si TODO su contenido es uno de estos. */
    private val junkAnnotation = Regex(
        "^(letra|lyrics?|con\\s*letra|official\\s*(video|audio|music\\s*video)?|video\\s*oficial|" +
            "audio\\s*oficial|en\\s*vivo|live|hd|4k|remaster(ed)?|visualizer|full\\s*song|video|audio)$",
        RegexOption.IGNORE_CASE,
    )
    private val trailingGroup = Regex("""[(\[]([^()\[\]]*)[)\]]\s*$""")

    /**
     * "Artista: Canción" o "Artista - Canción". El guion exige espacios a ambos lados para no
     * partir títulos con guiones ("Bling-Bang-Bang-Born").
     */
    private val artistSongSplit = Regex("""^(.{1,40}?)(?:\s*:\s+|\s+[-–—]\s+)(.+)$""")

    /** Número de pista al inicio de un título tomado del nombre de archivo: "01. ", "3 - ". */
    private val leadingTrackNumber = Regex("""^\d{1,3}\s*[.\-_]\s+""")

    /** Valores que la app usa cuando falta la etiqueta: no son un artista real. */
    private val placeholderArtists = setOf(
        "desconocido", "unknown", "<unknown>", "unknown artist", "artista desconocido",
        "various artists", "varios artistas", "va",
    )

    fun stripJunkAnnotations(title: String): String {
        var cleaned = title.trim()
        while (true) {
            val match = trailingGroup.find(cleaned) ?: break
            if (!junkAnnotation.matches(match.groupValues[1].trim())) break
            cleaned = cleaned.substring(0, match.range.first).trim()
        }
        return cleaned
    }

    fun extractArtistFromTitle(title: String): Cleaned? {
        val m = artistSongSplit.find(title) ?: return null
        val artist = m.groupValues[1].trim()
        val song = m.groupValues[2].trim()
        return if (artist.isEmpty() || song.isEmpty()) null else Cleaned(song, artist)
    }

    /**
     * Un artista real suele tener espacios o mayúsculas; un alias de canal ("neohex") es una
     * sola palabra en minúsculas. Vacío o un marcador como "Desconocido" tampoco es confiable.
     */
    fun isUntrustedArtist(artist: String): Boolean {
        val a = artist.trim()
        if (a.isEmpty() || a.lowercase() in placeholderArtists) return true
        return !a.contains(Regex("\\s")) && a == a.lowercase()
    }

    fun clean(rawTitle: String, rawArtist: String): Cleaned {
        val title = stripJunkAnnotations(rawTitle).replace(leadingTrackNumber, "")
        val extracted = extractArtistFromTitle(title)
        // Solo se reemplaza el artista si el que hay no es confiable: "THE HERO !!: Ikareru…"
        // es un título con dos puntos, no "Artista: Canción".
        if (extracted != null && isUntrustedArtist(rawArtist)) {
            return Cleaned(stripJunkAnnotations(extracted.title), extracted.artist)
        }
        val artist = rawArtist.trim().takeUnless { it.lowercase() in placeholderArtists }.orEmpty()
        return Cleaned(title, artist)
    }
}
