package cl.lossless.player.lyrics

import org.json.JSONObject

/** Trabajo de alineación tal como lo informa el servidor (ver aligner/README.md). */
data class AlignmentJob(
    val id: String,
    val status: Status,
    val error: String? = null,
    /** Solo con [Status.DONE]. */
    val lines: List<LyricLine>? = null,
    val language: String? = null,
    /** Puntaje medio de las palabras (0–1). */
    val confidence: Double = 0.0,
) {
    enum class Status { QUEUED, PROCESSING, DONE, FAILED }

    val pending: Boolean get() = status == Status.QUEUED || status == Status.PROCESSING
}

/** Lectura de las respuestas JSON del servidor de alineación. */
object AlignmentJson {

    fun parseJob(body: String): AlignmentJob {
        val json = JSONObject(body)
        val status = when (json.getString("status")) {
            "queued" -> AlignmentJob.Status.QUEUED
            "processing" -> AlignmentJob.Status.PROCESSING
            "done" -> AlignmentJob.Status.DONE
            else -> AlignmentJob.Status.FAILED
        }
        val result = json.optJSONObject("result")
        val lines = result?.optJSONArray("lines")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val line = array.getJSONObject(i)
                val text = line.optString("text").trim()
                if (text.isEmpty()) return@mapNotNull null
                val words = line.optJSONArray("words")?.let { w ->
                    (0 until w.length()).map { k ->
                        val word = w.getJSONObject(k)
                        LyricWord(word.getLong("startMs"), word.getLong("endMs"), word.getString("text"))
                    }
                }?.takeIf { it.isNotEmpty() }
                LyricLine(line.getLong("startMs"), text, words)
            }.sortedBy { it.timeMs }
        }
        return AlignmentJob(
            id = json.getString("id"),
            status = status,
            error = json.optString("error").takeIf { !json.isNull("error") && it.isNotBlank() },
            lines = lines?.takeIf { it.isNotEmpty() },
            language = result?.optString("language")?.takeIf { it.isNotBlank() },
            confidence = result?.optDouble("confidence", 0.0) ?: 0.0,
        )
    }

    /** Letra sincronizada a partir de un trabajo terminado; conserva el texto original como [Lyrics.plain]. */
    fun toLyrics(job: AlignmentJob, plain: String?): Lyrics? {
        val lines = job.lines ?: return null
        return Lyrics(lines, plain ?: lines.joinToString("\n") { it.text }, LyricsSource.ALIGNED)
    }
}
