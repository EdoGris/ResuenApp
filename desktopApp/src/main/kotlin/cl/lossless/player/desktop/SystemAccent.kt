package cl.lossless.player.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.util.concurrent.TimeUnit

/**
 * Color de énfasis del sistema, para la opción "Color del sistema" de Ajustes: el de
 * Configuración → Personalización → Colores en Windows, o el de Ajustes del Sistema →
 * Apariencia en macOS. [refresh] lo vuelve a leer (la app lo hace cada pocos segundos).
 */
object SystemAccent {

    var color by mutableStateOf(read())
        private set

    /** Bloqueante (lanza un proceso corto): llamar desde un hilo de E/S. */
    fun read(): Color = runCatching { if (IsMac) readMac() else readWindows() }.getOrNull() ?: WINDOWS_BLUE

    fun update(c: Color) {
        if (c != color) color = c
    }

    /** HKCU\Software\Microsoft\Windows\DWM\AccentColor, un DWORD 0xAABBGGRR. */
    private fun readWindows(): Color? {
        val out = run("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\DWM", "/v", "AccentColor") ?: return null
        val value = Regex("0x([0-9a-fA-F]+)").find(out)?.groupValues?.get(1)?.toLongOrNull(16) ?: return null
        val r = (value and 0xFF).toInt()
        val g = ((value shr 8) and 0xFF).toInt()
        val b = ((value shr 16) and 0xFF).toInt()
        return Color(r, g, b)
    }

    /** AppleAccentColor: sin valor es azul (multicolor); si no, -1 grafito y 0..6 rojo a rosa. */
    private fun readMac(): Color {
        val index = run("defaults", "read", "-g", "AppleAccentColor")?.trim()?.toIntOrNull() ?: 4
        return Color(
            when (index) {
                -1 -> 0xFF8E8E93
                0 -> 0xFFFF3B30
                1 -> 0xFFFF9500
                2 -> 0xFFFFCC00
                3 -> 0xFF28CD41
                5 -> 0xFFAF52DE
                6 -> 0xFFFF2D55
                else -> 0xFF007AFF
            },
        )
    }

    private fun run(vararg command: String): String? {
        val p = ProcessBuilder(*command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(3, TimeUnit.SECONDS) || p.exitValue() != 0) return null
        return out
    }

    private val WINDOWS_BLUE = Color(0xFF0078D4)
}
