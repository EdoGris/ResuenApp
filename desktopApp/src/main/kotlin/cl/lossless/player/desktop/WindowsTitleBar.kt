package cl.lossless.player.desktop

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/**
 * Barra de título de Windows con los colores de la app: fondo del tema, texto del tema y borde
 * del color de acento, en vez del color de énfasis de Windows. Los colores de barra y borde
 * solo existen en Windows 11; en Windows 10 queda al menos la barra oscura con los temas oscuros.
 */
object WindowsTitleBar {

    @Suppress("FunctionName")
    private interface Dwmapi : Library {
        fun DwmSetWindowAttribute(hwnd: Pointer, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: Dwmapi? by lazy {
        if (IsMac || !System.getProperty("os.name").lowercase().contains("windows")) null
        else runCatching { Native.load("dwmapi", Dwmapi::class.java) }.getOrNull()
    }

    private const val USE_IMMERSIVE_DARK_MODE = 20
    private const val BORDER_COLOR = 34
    private const val CAPTION_COLOR = 35
    private const val TEXT_COLOR = 36

    fun apply(window: ComposeWindow, dark: Boolean, caption: Color, text: Color, border: Color) {
        val api = dwm ?: return
        runCatching {
            val hwnd = Pointer(window.windowHandle)
            fun set(attribute: Int, value: Int) = api.DwmSetWindowAttribute(hwnd, attribute, IntByReference(value), 4)
            set(USE_IMMERSIVE_DARK_MODE, if (dark) 1 else 0)
            set(CAPTION_COLOR, caption.colorRef())
            set(TEXT_COLOR, text.colorRef())
            set(BORDER_COLOR, border.colorRef())
        }
    }

    /** COLORREF de Windows: 0x00BBGGRR. */
    private fun Color.colorRef(): Int {
        val argb = toArgb()
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (b shl 16) or (g shl 8) or r
    }
}
