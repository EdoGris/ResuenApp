package cl.lossless.player.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import cl.lossless.player.desktop.ui.App
import cl.lossless.player.desktop.ui.AppIcon
import cl.lossless.player.lyrics.LrcParser
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin
import javax.swing.UIManager
import kotlin.system.exitProcess

const val APP_VERSION = "1.1.0"

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--self-test") {
        exitProcess(selfTest(args.getOrNull(1)?.let(::File)))
    }
    // Diálogos de Swing (elegir carpeta) con el aspecto nativo del sistema.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    val player = Player()
    application {
        val searchFocus = remember { FocusRequester() }
        Window(
            onCloseRequest = {
                player.shutdown()
                exitApplication()
            },
            title = "ResuenApp",
            icon = BitmapPainter(AppIcon),
            state = rememberWindowState(width = 1360.dp, height = 860.dp),
            // Ctrl+F (⌘F en macOS) lleva a la búsqueda, como en la maqueta.
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && (if (IsMac) e.isMetaPressed else e.isCtrlPressed) && e.key == Key.F) {
                    runCatching { searchFocus.requestFocus() }
                    true
                } else {
                    false
                }
            },
        ) {
            App(player, searchFocus)
        }
    }
}

/**
 * Prueba rápida que corre CI sobre el ejecutable ya empaquetado: comprueba que FFmpeg y las
 * etiquetas funcionan dentro del instalador (sin abrir la salida de audio, que el servidor no
 * tiene). Escribe "OK" o el error en [report].
 */
private fun selfTest(report: File?): Int {
    val log = StringBuilder()
    val code = try {
        val wav = File.createTempFile("resuenapp-selftest", ".wav")
        writeSineWav(wav, rate = 96_000, bits = 24, seconds = 1)
        val track = LibraryScanner.readTrack(wav.toPath())
        log.appendLine("tags: ${track.formatLabel}, ${track.durationMs} ms")
        check(track.sampleRate == 96_000) { "frecuencia leída: ${track.sampleRate}" }

        val grabber = Decoder.open(wav)
        var frames = 0L
        try {
            while (true) {
                val frame = grabber.grabSamples() ?: break
                val samples = Decoder.toInt32Interleaved(frame, grabber.audioChannels) ?: continue
                frames += samples.size / grabber.audioChannels
            }
        } finally {
            grabber.stop(); grabber.release()
        }
        log.appendLine("ffmpeg: $frames frames")
        check(frames in 90_000..100_000) { "cuadros decodificados: $frames" }

        check(AppIcon.width == 256) { "ícono: ${AppIcon.width}px" }
        log.appendLine("icon: ok")

        val lines = LrcParser.parse("[00:01.00]Hola\n[00:02.50]Mundo")
        check(lines?.size == 2) { "LRC" }
        wav.delete()
        log.insert(0, "OK\n")
        0
    } catch (t: Throwable) {
        log.insert(0, "FAIL ${t}\n")
        log.appendLine(t.stackTraceToString())
        1
    }
    if (report != null) report.writeText(log.toString()) else print(log)
    return code
}

private fun writeSineWav(file: File, rate: Int, bits: Int, seconds: Int) {
    val channels = 2
    val frames = rate * seconds
    val bytesPerSample = bits / 8
    val dataSize = frames * channels * bytesPerSample
    DataOutputStream(FileOutputStream(file).buffered()).use { out ->
        fun le32(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF) }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.writeBytes("RIFF"); le32(36 + dataSize); out.writeBytes("WAVE")
        out.writeBytes("fmt "); le32(16); le16(1); le16(channels); le32(rate)
        le32(rate * channels * bytesPerSample); le16(channels * bytesPerSample); le16(bits)
        out.writeBytes("data"); le32(dataSize)
        val max = (1 shl (bits - 1)) - 1
        for (i in 0 until frames) {
            val v = (sin(2 * PI * 440 * i / rate) * max * 0.5).toInt()
            repeat(channels) { for (b in 0 until bytesPerSample) out.write((v shr (8 * b)) and 0xFF) }
        }
    }
}
