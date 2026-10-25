package cl.lossless.player.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import javax.imageio.ImageIO

/** Paleta "Fusión Neón" de la maqueta de escritorio: fondos azul noche y acentos neón. */
object Neon {
    val Night = Color(0xFF0A0D14)
    val Surface = Color(0xFF121722)
    val Card = Color(0xFF1A202C)
    val Border = Color(0xFF1F283B)
    val Hover = Color(0xFF2A354D)

    val Cyan = Color(0xFF14C4CC)
    val Teal = Color(0xFF22C7A9)
    val Amber = Color(0xFFFFB800)
    val Blue = Color(0xFF2196F3)
    val Pink = Color(0xFFE91E63)

    val Text = Color(0xFFE2E8F0)      // slate-200
    val TextStrong = Color.White
    val Muted = Color(0xFF94A3B8)     // slate-400
    val Faint = Color(0xFF64748B)     // slate-500
}

private val NeonColors = darkColorScheme(
    primary = Neon.Cyan,
    onPrimary = Neon.Night,
    primaryContainer = Neon.Cyan.copy(alpha = 0.18f),
    onPrimaryContainer = Neon.Cyan,
    secondary = Neon.Teal,
    onSecondary = Neon.Night,
    secondaryContainer = Neon.Cyan.copy(alpha = 0.18f),
    onSecondaryContainer = Neon.Cyan,
    tertiary = Neon.Amber,
    background = Neon.Night,
    onBackground = Neon.Text,
    surface = Neon.Surface,
    onSurface = Neon.Text,
    surfaceVariant = Neon.Card,
    onSurfaceVariant = Neon.Muted,
    surfaceContainerLowest = Neon.Night,
    surfaceContainerLow = Neon.Surface,
    surfaceContainer = Neon.Card,
    surfaceContainerHigh = Neon.Hover,
    surfaceContainerHighest = Neon.Hover,
    outline = Neon.Hover,
    outlineVariant = Neon.Border,
    error = Color(0xFFFF6B81),
)

private fun resourceBytes(path: String): ByteArray =
    checkNotNull(Neon::class.java.classLoader.getResourceAsStream(path)) { path }.use { it.readBytes() }

/** Inter, la misma fuente de la maqueta (viene incluida, igual que en Android). */
val Inter = FontFamily(
    Font("inter-regular", resourceBytes("font/inter_regular.ttf"), FontWeight.Normal),
    Font("inter-medium", resourceBytes("font/inter_medium.ttf"), FontWeight.Medium),
    Font("inter-semibold", resourceBytes("font/inter_semibold.ttf"), FontWeight.SemiBold),
    Font("inter-bold", resourceBytes("font/inter_bold.ttf"), FontWeight.Bold),
)

/** Para todo dato técnico: rutas, duraciones, formatos, números. */
val Mono = FontFamily.Monospace

/** Ícono de la app (el mismo de Android), para la ventana y la barra de título. */
val AppIcon: ImageBitmap by lazy {
    ImageIO.read(resourceBytes("resuenapp.png").inputStream()).toComposeImageBitmap()
}

private val AppTypography = Typography().let { base ->
    fun TextStyle.inter() = copy(fontFamily = Inter)
    Typography(
        displayLarge = base.displayLarge.inter(),
        displayMedium = base.displayMedium.inter(),
        displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(),
        headlineMedium = base.headlineMedium.inter(),
        headlineSmall = base.headlineSmall.inter(),
        titleLarge = base.titleLarge.inter(),
        titleMedium = base.titleMedium.inter(),
        titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter(),
        bodyMedium = base.bodyMedium.inter(),
        bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter(),
        labelMedium = base.labelMedium.inter(),
        labelSmall = base.labelSmall.inter(),
    )
}

@Composable
fun NeonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NeonColors, typography = AppTypography, content = content)
}

/** Panel translúcido con borde, como .glass-panel / .glass-card de la maqueta. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(12.dp), color: Color = Neon.Surface.copy(alpha = 0.75f)): Modifier =
    clip(shape).background(color, shape).border(1.dp, Neon.Border, shape)

/** Resplandor neón alrededor de un elemento. */
fun Modifier.glow(color: Color = Neon.Cyan, radius: Dp = 14.dp, shape: Shape = RoundedCornerShape(50)): Modifier =
    shadow(radius, shape, clip = false, ambientColor = color, spotColor = color)

/** Rótulo de sección en mayúsculas y monoespaciado ("BIBLIOTECA"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Neon.Muted) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontFamily = Mono,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp,
    )
}

/** Insignia monoespaciada: formato, bits/kHz, estado de la salida. */
@Composable
fun NeonBadge(text: String, color: Color = Neon.Muted, emphasized: Boolean = false, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    Text(
        text,
        modifier = modifier
            .clip(shape)
            .background(if (emphasized) color.copy(alpha = 0.10f) else Neon.Card, shape)
            .border(1.dp, if (emphasized) color.copy(alpha = 0.35f) else Neon.Border, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = color,
        fontFamily = Mono,
        fontSize = 10.sp,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
    )
}
