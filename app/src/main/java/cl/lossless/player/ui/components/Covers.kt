package cl.lossless.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cl.lossless.player.covers.CoverRef
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * Carátula desde la caché de la app ([cover]) o desde una URI/URL ([model]).
 * Sin imagen muestra [fallback] (por defecto, una nota musical).
 */
@Composable
fun CoverImage(
    modifier: Modifier = Modifier,
    cover: CoverRef? = null,
    model: Any? = null,
    shape: Shape = RoundedCornerShape(8.dp),
    fallback: @Composable () -> Unit = {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(0.45f),
        )
    },
) {
    val context = LocalContext.current
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerLowest),
        contentAlignment = Alignment.Center,
    ) {
        val data: Any? = when {
            cover != null -> ImageRequest.Builder(context)
                .data(cover.file)
                .memoryCacheKey("${cover.file.path}#${cover.stamp}")
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            else -> model
        }
        if (data == null) {
            fallback()
        } else {
            AsyncImage(
                model = data,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Miniatura de pista: carátula si existe; si no, el número de pista en una caja. */
@Composable
fun TrackThumb(
    number: String,
    cover: CoverRef?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    model: Any? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    CoverImage(
        modifier = modifier.size(size).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        cover = cover,
        model = model,
        shape = shape,
        fallback = {
            Text(
                number,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

/** Carátula grande cuadrada (detalle de álbum, reproductor). */
@Composable
fun LargeCover(cover: CoverRef?, model: Any?, modifier: Modifier = Modifier) {
    CoverImage(
        modifier = modifier.fillMaxWidth(),
        cover = cover,
        model = model,
        shape = RoundedCornerShape(24.dp),
        // Sin carátula: el logo de la app en lugar de la nota musical
        fallback = { ResuenLogo(Modifier.fillMaxSize(), showHiResBadge = false) },
    )
}
