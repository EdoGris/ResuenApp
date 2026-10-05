package cl.lossless.player.library

/**
 * Carpetas omitidas de la biblioteca. Cada carpeta se identifica por su ruta en segmentos
 * unida con "/" (ej. "primary/Android/media/com.whatsapp"); omitir una carpeta omite también
 * todas sus subcarpetas.
 */
object FolderExclusions {

    fun key(segments: List<String>): String = segments.joinToString("/")

    fun segments(key: String): List<String> = key.split('/').filter { it.isNotEmpty() }

    /** true si [segments] es la carpeta [ancestorKey] o está dentro de ella. */
    fun isWithin(segments: List<String>, ancestorKey: String): Boolean {
        val ancestor = segments(ancestorKey)
        return segments.size >= ancestor.size && segments.subList(0, ancestor.size) == ancestor
    }

    fun isExcluded(segments: List<String>, excluded: Set<String>): Boolean =
        excluded.any { isWithin(segments, it) }

    /** Agrega [key] quitando las entradas que quedan cubiertas por ella. */
    fun add(excluded: Set<String>, key: String): Set<String> =
        excluded.filterNot { isWithin(segments(it), key) }.toSet() + key

    // ---------- Detección de carpetas de apps de mensajería / con audio propio ----------

    /** Paquetes conocidos → nombre legible. */
    private val knownApps = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "org.telegram.messenger" to "Telegram",
        "org.telegram.plus" to "Telegram Plus",
        "org.thunderdog.challegram" to "Telegram X",
        "org.thoughtcrime.securesms" to "Signal",
        "com.facebook.orca" to "Messenger",
        "com.instagram.android" to "Instagram",
        "com.viber.voip" to "Viber",
        "com.tencent.mm" to "WeChat",
        "jp.naver.line.android" to "LINE",
        "com.discord" to "Discord",
        "com.snapchat.android" to "Snapchat",
        "com.skype.raider" to "Skype",
        "com.microsoft.teams" to "Teams",
        "com.google.android.apps.messaging" to "Mensajes",
        "com.android.soundrecorder" to "Grabadora",
        "com.miui.soundrecorder" to "Grabadora",
        "com.sec.android.app.voicenote" to "Grabadora",
    )

    /** Carpetas de apps fuera de Android/ (versiones antiguas de WhatsApp, Telegram…). */
    private val knownRootFolders = mapOf(
        "whatsapp" to "WhatsApp",
        "whatsapp business" to "WhatsApp Business",
        "telegram" to "Telegram",
        "viber" to "Viber",
        "signal" to "Signal",
        "recordings" to "Grabaciones",
        "callrecordings" to "Grabaciones de llamadas",
        "voice recorder" to "Grabadora",
    )

    /**
     * Carpetas candidatas a omitir:
     * - Android/media/<paquete> y Android/data/<paquete>: archivos internos de cualquier app.
     * - Carpetas raíz conocidas de apps de mensajería y grabadoras.
     */
    fun suggestions(tracks: List<Track>, excluded: Set<String>): List<FolderSuggestion> =
        suggestionsFor(tracks.map { it.folderSegments }, excluded)

    /** Igual que [suggestions], a partir de la ruta de carpeta de cada pista. */
    fun suggestionsFor(trackFolders: List<List<String>>, excluded: Set<String>): List<FolderSuggestion> {
        val found = mutableMapOf<String, Pair<String, Int>>() // key → (nombre, pistas)
        for (s in trackFolders) {
            if (isExcluded(s, excluded)) continue
            val candidate: Pair<List<String>, String>? = when {
                // [volumen, Android, media|data, paquete, …]
                s.size >= 4 && s[1].equals("Android", true) &&
                    (s[2].equals("media", true) || s[2].equals("data", true)) -> {
                    val pkg = s[3]
                    s.subList(0, 4) to (knownApps[pkg] ?: pkg)
                }
                // [volumen, WhatsApp, …]
                s.size >= 2 && s[1].lowercase() in knownRootFolders ->
                    s.subList(0, 2) to knownRootFolders.getValue(s[1].lowercase())
                else -> null
            }
            if (candidate != null) {
                val k = key(candidate.first)
                val (name, count) = found[k] ?: (candidate.second to 0)
                found[k] = name to count + 1
            }
        }
        return found.map { (k, v) -> FolderSuggestion(k, v.first, v.second) }
            .sortedByDescending { it.trackCount }
    }
}

data class FolderSuggestion(val key: String, val appName: String, val trackCount: Int) {
    val displayPath: String get() = folderDisplayPath(FolderExclusions.segments(key))
}

/** Nodo del árbol completo de carpetas con música (para elegir qué omitir). */
data class FolderTreeNode(
    val name: String,
    val segments: List<String>,
    /** Pistas en esta carpeta y todas sus subcarpetas. */
    val trackCount: Int,
    val children: List<FolderTreeNode>,
) {
    val key: String get() = FolderExclusions.key(segments)
}

/** Árbol de carpetas a partir de las pistas; las raíces son los volúmenes. */
fun List<Track>.folderTree(): List<FolderTreeNode> {
    class Mutable(val name: String, val segments: List<String>) {
        var count = 0
        val children = sortedMapOf<String, Mutable>(String.CASE_INSENSITIVE_ORDER)
        fun freeze(): FolderTreeNode = FolderTreeNode(name, segments, count, children.values.map { it.freeze() })
    }
    val roots = sortedMapOf<String, Mutable>(String.CASE_INSENSITIVE_ORDER)
    for (track in this) {
        val s = track.folderSegments
        var level = roots
        for (i in s.indices) {
            val node = level.getOrPut(s[i]) { Mutable(s[i], s.subList(0, i + 1)) }
            node.count++
            level = node.children
        }
    }
    return roots.values.map { it.freeze() }
}
