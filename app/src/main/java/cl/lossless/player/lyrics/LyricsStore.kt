package cl.lossless.player.lyrics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Caché en disco de las letras descargadas (un archivo por pista), para no volver a consultar
 * internet. También recuerda "no encontrada" durante [NOT_FOUND_TTL_MS] para no insistir.
 */
class LyricsCache(context: Context) {

    sealed interface Entry {
        data class Found(val lyrics: Lyrics) : Entry
        data object NotFound : Entry
    }

    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    private fun fileFor(key: String) = File(dir, sha1(key) + ".json")

    fun get(key: String): Entry? = runCatching {
        val file = fileFor(key)
        if (!file.isFile) return null
        val json = JSONObject(file.readText())
        if (json.optBoolean("notFound")) {
            val fresh = System.currentTimeMillis() - json.optLong("savedAt") < NOT_FOUND_TTL_MS
            return if (fresh) Entry.NotFound else null
        }
        val synced = json.optJSONArray("synced")?.let { array ->
            (0 until array.length()).map { i ->
                val line = array.getJSONObject(i)
                LyricLine(line.getLong("t"), line.getString("x"))
            }
        }
        Entry.Found(
            Lyrics(
                synced = synced,
                plain = json.optString("plain").takeIf { it.isNotEmpty() },
                source = LyricsSource.valueOf(json.getString("source")),
                instrumental = json.optBoolean("instrumental"),
            )
        )
    }.getOrNull()

    fun putFound(key: String, lyrics: Lyrics) {
        val json = JSONObject()
            .put("source", lyrics.source.name)
            .put("plain", lyrics.plain ?: "")
            .put("instrumental", lyrics.instrumental)
            .put("savedAt", System.currentTimeMillis())
        lyrics.synced?.let { lines ->
            json.put("synced", JSONArray().apply { lines.forEach { put(JSONObject().put("t", it.timeMs).put("x", it.text)) } })
        }
        write(key, json)
    }

    fun putNotFound(key: String) =
        write(key, JSONObject().put("notFound", true).put("savedAt", System.currentTimeMillis()))

    fun remove(key: String) { fileFor(key).delete() }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }

    private fun write(key: String, json: JSONObject) = runCatching {
        val target = fileFor(key)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val NOT_FOUND_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}

/** Preferencias de letras: búsqueda en internet y desfase de sincronía por pista. */
class LyricsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("lyrics", Context.MODE_PRIVATE)

    var searchOnline: Boolean
        get() = prefs.getBoolean(KEY_ONLINE, true)
        set(value) = prefs.edit().putBoolean(KEY_ONLINE, value).apply()

    /** Retraso de la letra respecto del audio, en ms (positivo = la letra aparece más tarde). */
    fun offsetFor(trackKey: String): Long = prefs.getLong(KEY_OFFSET + trackKey, 0L)

    fun setOffset(trackKey: String, offsetMs: Long) {
        prefs.edit().apply {
            if (offsetMs == 0L) remove(KEY_OFFSET + trackKey) else putLong(KEY_OFFSET + trackKey, offsetMs)
        }.apply()
    }

    private companion object {
        const val KEY_ONLINE = "search_online"
        const val KEY_OFFSET = "offset:"
    }
}
