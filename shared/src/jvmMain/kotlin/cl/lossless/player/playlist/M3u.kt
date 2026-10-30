package cl.lossless.player.playlist

/**
 * Una lista de reproducción: un nombre y las rutas de sus canciones, en orden. Se guarda como
 * .m3u8 (texto UTF-8, una ruta por línea), que también abren foobar, VLC o Poweramp.
 */
data class Playlist(val id: String, val name: String, val paths: List<String>)

/** Lectura y escritura de .m3u / .m3u8 sin APIs de plataforma, para escritorio, Android y luego iOS. */
object M3u {

    private const val HEADER = "#EXTM3U"
    private const val NAME = "#PLAYLIST:"

    fun write(name: String, paths: List<String>): String = buildString {
        append(HEADER).append('\n')
        append(NAME).append(name.replace('\n', ' ').trim()).append('\n')
        paths.forEach { append(it).append('\n') }
    }

    /**
     * Devuelve el nombre guardado (null si la lista no trae #PLAYLIST) y las rutas. [resolve]
     * convierte una ruta relativa a la carpeta de la lista en absoluta; las absolutas no pasan por él.
     */
    fun parse(text: String, resolve: (String) -> String = { it }): Pair<String?, List<String>> {
        var name: String? = null
        val paths = mutableListOf<String>()
        text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.isEmpty() -> Unit
                line.startsWith(NAME) -> name = line.removePrefix(NAME).trim().ifEmpty { null }
                line.startsWith("#") -> Unit
                else -> paths += fromUri(line).let { if (isAbsolute(it)) it else resolve(it) }
            }
        }
        return name to paths
    }

    /** "C:\…", "\\servidor\…" o "/…". */
    fun isAbsolute(path: String): Boolean =
        path.startsWith("/") || path.startsWith("\\\\") || (path.length > 2 && path[1] == ':' && (path[2] == '\\' || path[2] == '/'))

    /** Algunas apps escriben file:///C:/Música/… con caracteres %XX. */
    private fun fromUri(line: String): String {
        if (!line.startsWith("file:", ignoreCase = true)) return line
        val raw = line.substring(5).trimStart('/')
        val decoded = percentDecode(raw)
        return if (decoded.length > 1 && decoded[1] == ':') decoded else "/$decoded"
    }

    private fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val bytes = ArrayList<Byte>(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 <= s.lastIndex) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) { bytes += v.toByte(); i += 3; continue }
            }
            c.toString().encodeToByteArray().forEach { bytes += it }
            i++
        }
        return bytes.toByteArray().decodeToString()
    }

    /** Nombre de archivo seguro a partir del nombre visible de la lista. */
    fun fileStem(name: String): String =
        name.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trimEnd('.', ' ').take(80).ifEmpty { "lista" }
}
