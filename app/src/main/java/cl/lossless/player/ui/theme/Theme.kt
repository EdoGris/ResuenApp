package cl.lossless.player.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.annotation.StringRes
import cl.lossless.player.R

enum class ThemeMode(@StringRes val labelRes: Int) {
    SYSTEM(R.string.settings_theme_system),
    DARK(R.string.settings_theme_dark),
    LIGHT(R.string.settings_theme_light),
    /** Oscuro con las superficies azul noche de la versión de escritorio ("Fusión Neón"). */
    NIGHT(R.string.settings_theme_night),
}

/**
 * Color de acento de la app. Todo el diseño (pestañas, elemento seleccionado, carpetas,
 * íconos, controles y, opcionalmente, los textos) se deriva de su tono.
 */
enum class AccentColor(
    @StringRes val labelRes: Int,
    val hue: Float,
    val saturation: Float,
    /** Color exacto de la paleta neón: en tema oscuro se usa tal cual como color principal. */
    private val exact: Long? = null,
) {
    BLUE(R.string.settings_accent_blue, 220f, 0.95f),
    TEAL(R.string.settings_accent_teal, 178f, 0.85f),
    GREEN(R.string.settings_accent_green, 145f, 0.75f),
    AMBER(R.string.settings_accent_amber, 38f, 1.00f),
    ORANGE(R.string.settings_accent_orange, 18f, 0.95f),
    RED(R.string.settings_accent_red, 355f, 0.85f),
    PINK(R.string.settings_accent_pink, 330f, 0.85f),
    PURPLE(R.string.settings_accent_purple, 268f, 0.80f),

    // Paleta neón de la versión de escritorio (mismos colores que en Windows).
    NEON_CYAN(R.string.settings_accent_neon_cyan, 182.6f, 0.82f, 0xFF14C4CC),
    NEON_TEAL(R.string.settings_accent_neon_teal, 169.1f, 0.71f, 0xFF22C7A9),
    NEON_AMBER(R.string.settings_accent_neon_amber, 43.3f, 1.00f, 0xFFFFB800),
    NEON_BLUE(R.string.settings_accent_neon_blue, 206.6f, 0.90f, 0xFF2196F3),
    NEON_PINK(R.string.settings_accent_neon_pink, 339.6f, 0.82f, 0xFFE91E63),
    ;

    /** Color principal en tema oscuro. */
    fun primaryDark(): Color = exact?.let { Color(it) } ?: darkPrimary(hue, saturation)

    /** Color de muestra para el selector. */
    fun swatch(dark: Boolean): Color = if (dark) primaryDark() else lightPrimary(hue, saturation)
}

/** Superficies del tema "Noche": las mismas de la app de Windows. */
private object Night {
    val background = Color(0xFF0A0D14)
    val surface = Color(0xFF121722)
    val card = Color(0xFF1A202C)
    val border = Color(0xFF1F283B)
    val hover = Color(0xFF2A354D)
}

private fun hsl(h: Float, s: Float, l: Float) = Color.hsl(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/**
 * Color saturado de luminosidad [l], corregida lo justo para que su luminancia relativa quede
 * entre [minLum] y [maxLum]. Así cada tono se ve vivo y a la vez mantiene el contraste
 * (≥ 4.5:1 en texto), algo que una luminosidad HSL fija no logra: a igual L, el amarillo
 * se ve mucho más claro que el violeta.
 */
private fun vivid(h: Float, s: Float, l: Float, minLum: Float = 0f, maxLum: Float = 1f): Color {
    val base = hsl(h, s, l)
    val lum = base.luminance()
    if (lum in minLum..maxLum) return base
    var lo = if (lum < minLum) l else 0f
    var hi = if (lum < minLum) 1f else l
    repeat(24) {
        val mid = (lo + hi) / 2
        val tooDark = hsl(h, s, mid).luminance() < minLum
        val tooLight = hsl(h, s, mid).luminance() > maxLum
        if (lum < minLum) { if (tooDark) lo = mid else hi = mid } else { if (tooLight) hi = mid else lo = mid }
    }
    return hsl(h, s, if (lum < minLum) hi else lo)
}

private fun darkPrimary(h: Float, s: Float) = vivid(h, s, 0.60f, minLum = 0.28f)
private fun lightPrimary(h: Float, s: Float) = vivid(h, s, 0.45f, maxLum = 0.12f)

private fun darkScheme(accent: AccentColor, tintedText: Boolean): ColorScheme {
    val h = accent.hue
    val s = accent.saturation
    return darkColorScheme(
        primary = accent.primaryDark(),
        onPrimary = vivid(h, s, 0.10f, maxLum = 0.03f),
        primaryContainer = vivid(h, s * 0.9f, 0.30f, maxLum = 0.04f),
        onPrimaryContainer = vivid(h, s, 0.90f, minLum = 0.75f),
        secondary = vivid(h, 1f, 0.60f, minLum = 0.36f),
        onSecondary = vivid(h, s, 0.10f, maxLum = 0.03f),
        secondaryContainer = vivid(h, s * 0.6f, 0.24f, maxLum = 0.04f),
        onSecondaryContainer = vivid(h, s, 0.88f, minLum = 0.72f),
        tertiary = vivid(h + 40f, s, 0.62f, minLum = 0.28f),
        background = Color(0xFF161616),
        onBackground = if (tintedText) hsl(h, 0.35f, 0.92f) else Color(0xFFE8E8E8),
        surface = Color(0xFF1C1C1C),
        onSurface = if (tintedText) hsl(h, 0.35f, 0.92f) else Color(0xFFE8E8E8),
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = if (tintedText) hsl(h, 0.25f, 0.70f) else Color(0xFFA8A8A8),
        surfaceContainerLowest = Color(0xFF121212),
        surfaceContainerLow = Color(0xFF1A1A1A),
        surfaceContainer = Color(0xFF212121),
        surfaceContainerHigh = Color(0xFF282828),
        surfaceContainerHighest = Color(0xFF303030),
        outline = Color(0xFF4A4A4A),
        outlineVariant = Color(0xFF333333),
    )
}

private fun lightScheme(accent: AccentColor, tintedText: Boolean): ColorScheme {
    val h = accent.hue
    val s = accent.saturation
    return lightColorScheme(
        primary = lightPrimary(h, s),
        onPrimary = Color.White,
        primaryContainer = vivid(h, s, 0.88f, minLum = 0.68f),
        onPrimaryContainer = vivid(h, s, 0.15f, maxLum = 0.03f),
        secondary = vivid(h, s, 0.45f, maxLum = 0.10f),
        onSecondary = Color.White,
        secondaryContainer = vivid(h, s * 0.7f, 0.88f, minLum = 0.66f),
        onSecondaryContainer = vivid(h, s, 0.15f, maxLum = 0.03f),
        tertiary = vivid(h + 40f, s, 0.42f, maxLum = 0.12f),
        background = Color(0xFFF6F7FA),
        onBackground = if (tintedText) hsl(h, 0.40f, 0.14f) else Color(0xFF1A1C1E),
        surface = Color(0xFFF6F7FA),
        onSurface = if (tintedText) hsl(h, 0.40f, 0.14f) else Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFE3E6EC),
        onSurfaceVariant = if (tintedText) hsl(h, 0.20f, 0.38f) else Color(0xFF5A5F68),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF1F3F7),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFE9ECF2),
        surfaceContainerHighest = Color(0xFFE2E5EC),
        outline = Color(0xFFB8BDC7),
        outlineVariant = Color(0xFFDADDE3),
    )
}

private fun hueOf(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}

private fun ColorScheme.withTintedText(h: Float, dark: Boolean): ColorScheme =
    if (dark) copy(
        onBackground = hsl(h, 0.35f, 0.92f),
        onSurface = hsl(h, 0.35f, 0.92f),
        onSurfaceVariant = hsl(h, 0.25f, 0.70f),
    ) else copy(
        onBackground = hsl(h, 0.40f, 0.14f),
        onSurface = hsl(h, 0.40f, 0.14f),
        onSurfaceVariant = hsl(h, 0.20f, 0.38f),
    )

/** Tema oscuro con las superficies azul noche de la paleta neón. */
private fun ColorScheme.night(): ColorScheme = copy(
    background = Night.background,
    surface = Night.background,
    surfaceVariant = Night.card,
    surfaceContainerLowest = Night.background,
    surfaceContainerLow = Night.surface,
    surfaceContainer = Night.card,
    surfaceContainerHigh = Color(0xFF222A3C),
    surfaceContainerHighest = Night.hover,
    outline = Color(0xFF3A4766),
    outlineVariant = Night.border,
)

/** true si el tema efectivo es oscuro. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK, ThemeMode.NIGHT -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun LosslessTheme(
    mode: ThemeMode = ThemeMode.DARK,
    accent: AccentColor = AccentColor.BLUE,
    tintedText: Boolean = false,
    dynamicColor: Boolean = false,
    font: AppFont = AppFont.SYSTEM,
    textSize: TextSize = TextSize.NORMAL,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark()
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // "Colorear textos" también con Material You: matiz del color principal del fondo de pantalla.
            if (tintedText) scheme.withTintedText(hueOf(scheme.primary), dark) else scheme
        }
        dark -> darkScheme(accent, tintedText)
        else -> lightScheme(accent, tintedText)
    }.let { if (mode == ThemeMode.NIGHT) it.night() else it }
    val typography = remember(font) { appTypography(font) }
    // El tamaño escala todo el texto en "sp", también el que no usa la tipografía del tema
    // (letras, insignias), y se suma al tamaño de fuente del sistema. En "Normal" se deja la
    // densidad del sistema tal cual (Android 14 escala las fuentes grandes de forma no lineal).
    val density = LocalDensity.current
    val scaled = remember(density, textSize) {
        if (textSize == TextSize.NORMAL) density else Density(density.density, density.fontScale * textSize.scale)
    }
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = colors, typography = typography, content = content)
    }
}
