package cl.lossless.player.lyrics

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/**
 * Cliente del servidor de alineación forzada (carpeta aligner/ del repositorio).
 *
 * El id de cada trabajo se calcula igual en la app y en el servidor (audio + letra), así que
 * primero se pregunta por él sin subir nada: si la canción ya se procesó, no se vuelve a enviar.
 */
class AlignmentClient(baseUrl: String, private val token: String) {

    private val base = baseUrl.trim().trimEnd('/')

    /** El servidor rechazó el token. */
    class Unauthorized : IOException("El servidor rechazó el token")

    /** El servidor respondió con un error HTTP; [detail] es el texto que envió (si vino). */
    class HttpError(val code: Int, val detail: String?) :
        IOException("El servidor respondió HTTP $code" + (detail?.let { ": $it" } ?: ""))

    /** Datos del servidor según /v1/health. */
    data class Health(val model: String, val device: String, val queued: Int)

    /** Estado del trabajo, o null si el servidor no lo conoce. */
    fun get(jobId: String): AlignmentJob? {
        val connection = open("/v1/alignments/$jobId", "GET")
        return try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> AlignmentJson.parseJob(connection.readBody())
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw failure(code, connection)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Sube el audio y la letra (multipart, en partes, sin cargar el archivo en memoria). */
    fun submit(
        audio: () -> InputStream,
        fileName: String,
        mimeType: String,
        lyrics: String,
        title: String,
        artist: String,
        durationMs: Long,
    ): AlignmentJob {
        val boundary = "----ResuenApp" + UUID.randomUUID().toString().replace("-", "")
        val connection = open("/v1/alignments", "POST").apply {
            doOutput = true
            readTimeout = 120_000 // el servidor calcula el hash del archivo antes de responder
            setChunkedStreamingMode(64 * 1024)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }
        return try {
            connection.outputStream.buffered().use { out ->
                fun field(name: String, value: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.write("\r\n".toByteArray())
                }
                field("lyrics", lyrics)
                field("title", title)
                field("artist", artist)
                if (durationMs > 0) field("durationMs", durationMs.toString())
                val safeName = fileName.ifBlank { "audio" }.replace("\"", "'").replace("\r", "").replace("\n", "")
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"$safeName\"\r\n" +
                        "Content-Type: ${mimeType.ifBlank { "application/octet-stream" }}\r\n\r\n").toByteArray(Charsets.UTF_8)
                )
                audio().use { it.copyTo(out, 64 * 1024) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_ACCEPTED -> AlignmentJson.parseJob(connection.readBody())
                else -> throw failure(code, connection)
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Comprueba la conexión; devuelve los datos del servidor. */
    fun health(): Health {
        val connection = open("/v1/health", "GET")
        return try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val json = org.json.JSONObject(connection.readBody())
                    Health(json.optString("whisperModel"), json.optString("device").uppercase(), json.optInt("queued"))
                }
                else -> throw failure(code, connection)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, method: String): HttpURLConnection =
        (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", LrcLibClient.USER_AGENT)
            if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
        }

    private fun HttpURLConnection.readBody(): String = inputStream.bufferedReader().use { it.readText() }

    private fun failure(code: Int, connection: HttpURLConnection): IOException {
        if (code == HttpURLConnection.HTTP_UNAUTHORIZED) return Unauthorized()
        val detail = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
                ?.let { org.json.JSONObject(it).optString("detail") }
        }.getOrNull()
        return HttpError(code, detail?.takeIf { it.isNotBlank() })
    }

    companion object {
        /** Igual que normalize_lyrics del servidor: líneas recortadas, sin vacías, unidas por "\n". */
        fun normalizeLyrics(text: String): String =
            text.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
                .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

        /** sha256(<sha256 del audio> + "\n" + letra normalizada), igual que job_id del servidor. */
        fun jobId(audioSha256: String, lyrics: String): String =
            sha256Hex((audioSha256.lowercase() + "\n" + normalizeLyrics(lyrics)).toByteArray(Charsets.UTF_8))

        fun sha256(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            input.use { while (true) { val n = it.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            return digest.digest().toHex()
        }

        private fun sha256Hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

        private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    }
}
