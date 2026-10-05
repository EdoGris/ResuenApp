package cl.lossless.player.playback

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Carga las carátulas para la notificación y la pantalla de bloqueo; si la pista no tiene
 * carátula, muestra el logo de la app en lugar de dejar el espacio vacío.
 */
@OptIn(UnstableApi::class)
class LogoFallbackBitmapLoader(
    private val delegate: BitmapLoader,
    private val logo: () -> Bitmap,
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = delegate.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = delegate.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> = delegate.loadBitmap(uri)

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? =
        delegate.loadBitmapFromMetadata(metadata) ?: Futures.immediateFuture(logo())
}
