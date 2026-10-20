package cl.lossless.player.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import cl.lossless.player.R

/**
 * Tipo de letra de la app. Las familias sans más usadas hoy vienen incluidas en la app
 * (res/font, licencia OFL, solo caracteres latinos); lo que no traen, como letras en
 * japonés o coreano, lo dibuja la fuente del sistema.
 */
enum class AppFont(private val brandName: String?, @StringRes private val labelRes: Int = 0) {
    SYSTEM(null, R.string.settings_font_system),
    ROBOTO("Roboto"),
    INTER("Inter"),
    OPEN_SANS("Open Sans"),
    NUNITO("Nunito"),
    MONTSERRAT("Montserrat"),
    POPPINS("Poppins"),
    SERIF("Serif"),
    ;

    /** Nombre para mostrar: el de la familia o, si es genérico, traducido. */
    val label: String
        @Composable get() = brandName ?: stringResource(labelRes)

    /** null = la fuente predeterminada del sistema. */
    val family: FontFamily?
        get() = when (this) {
            SYSTEM -> null
            ROBOTO -> roboto
            INTER -> inter
            OPEN_SANS -> openSans
            NUNITO -> nunito
            MONTSERRAT -> montserrat
            POPPINS -> poppins
            SERIF -> FontFamily.Serif
        }
}

/** Tamaño del texto, sobre el tamaño de fuente elegido en el sistema. */
enum class TextSize(@StringRes val labelRes: Int, val scale: Float) {
    SMALL(R.string.settings_text_size_small, 0.9f),
    NORMAL(R.string.settings_text_size_normal, 1f),
    LARGE(R.string.settings_text_size_large, 1.15f),
    XLARGE(R.string.settings_text_size_xlarge, 1.3f),
}

private fun family(regular: Int, medium: Int, semibold: Int, bold: Int) = FontFamily(
    Font(regular, FontWeight.Normal),
    Font(medium, FontWeight.Medium),
    Font(semibold, FontWeight.SemiBold),
    Font(bold, FontWeight.Bold),
)

private val roboto = family(R.font.roboto_regular, R.font.roboto_medium, R.font.roboto_semibold, R.font.roboto_bold)
private val inter = family(R.font.inter_regular, R.font.inter_medium, R.font.inter_semibold, R.font.inter_bold)
private val openSans = family(R.font.open_sans_regular, R.font.open_sans_medium, R.font.open_sans_semibold, R.font.open_sans_bold)
private val nunito = family(R.font.nunito_regular, R.font.nunito_medium, R.font.nunito_semibold, R.font.nunito_bold)
private val montserrat = family(R.font.montserrat_regular, R.font.montserrat_medium, R.font.montserrat_semibold, R.font.montserrat_bold)
private val poppins = family(R.font.poppins_regular, R.font.poppins_medium, R.font.poppins_semibold, R.font.poppins_bold)

/** Tipografía de Material 3 con la familia elegida (el tamaño se aplica aparte, en la densidad). */
fun appTypography(font: AppFont): Typography {
    val base = Typography()
    val family = font.family ?: return base
    fun TextStyle.withFamily() = copy(fontFamily = family)
    return Typography(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = base.headlineMedium.withFamily(),
        headlineSmall = base.headlineSmall.withFamily(),
        titleLarge = base.titleLarge.withFamily(),
        titleMedium = base.titleMedium.withFamily(),
        titleSmall = base.titleSmall.withFamily(),
        bodyLarge = base.bodyLarge.withFamily(),
        bodyMedium = base.bodyMedium.withFamily(),
        bodySmall = base.bodySmall.withFamily(),
        labelLarge = base.labelLarge.withFamily(),
        labelMedium = base.labelMedium.withFamily(),
        labelSmall = base.labelSmall.withFamily(),
    )
}
