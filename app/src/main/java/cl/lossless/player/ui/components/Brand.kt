package cl.lossless.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.lossless.player.R

/** Colores de la identidad ResuenApp ("Fusión Neón"). */
object BrandColors {
    val Night = Color(0xFF05070C)
    val NightLight = Color(0xFF141A2A)
    val Cyan = Color(0xFF14C4CC)
    val Teal = Color(0xFF22C7A9)
    val Amber = Color(0xFFFFB800)
}

/**
 * Isotipo de ResuenApp: la "R" de frecuencia lineal sobre fondo nocturno con retícula,
 * resplandor neón y, opcionalmente, la insignia HI-RES. Se adapta al tamaño que recibe.
 */
@Composable
fun ResuenLogo(
    modifier: Modifier = Modifier,
    showHiResBadge: Boolean = true,
    glow: Float = 0.6f,
) {
    BoxWithConstraints(modifier) {
        val size = minOf(maxWidth, maxHeight)
        val shape = RoundedCornerShape(size * 0.26f)
        val mark = painterResource(R.drawable.ic_launcher_foreground)
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(Brush.radialGradient(listOf(BrandColors.NightLight, BrandColors.Night)))
                .drawBehind {
                    // Retícula sutil, como en el ícono
                    val grid = Color.White.copy(alpha = 0.08f)
                    listOf(0.25f, 0.5f, 0.75f).forEach { f ->
                        drawLine(grid, Offset(this.size.width * f, 0f), Offset(this.size.width * f, this.size.height), 1f)
                    }
                    drawLine(grid, Offset(0f, this.size.height / 2), Offset(this.size.width, this.size.height / 2), 1f)
                }
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.18f), BrandColors.Teal.copy(alpha = 0.35f))),
                    shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // El trazo ocupa ~45 % del lienzo de 108dp del ícono adaptativo: se amplía para llenar.
            if (glow > 0f) {
                Image(
                    mark, null,
                    Modifier.fillMaxSize().scale(1.4f).blur(size * 0.07f).alpha(glow),
                )
            }
            Image(mark, contentDescription = "ResuenApp", modifier = Modifier.fillMaxSize().scale(1.4f))
            if (showHiResBadge) {
                HiResBadge(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = size * 0.07f, bottom = size * 0.06f),
                    height = size * 0.11f,
                )
            }
        }
    }
}

@Composable
private fun HiResBadge(modifier: Modifier, height: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(height * 0.25f)
    val fontSize = with(LocalDensity.current) { (height * 0.5f).toSp() }
    Text(
        "HI-RES",
        color = BrandColors.Amber,
        fontSize = fontSize,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        letterSpacing = fontSize * 0.15f,
        modifier = modifier
            .border(1.dp, BrandColors.Amber, shape)
            .background(BrandColors.Night.copy(alpha = 0.7f), shape)
            .padding(horizontal = height * 0.45f, vertical = height * 0.12f),
    )
}
