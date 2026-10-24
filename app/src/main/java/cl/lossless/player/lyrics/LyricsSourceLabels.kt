package cl.lossless.player.lyrics

import androidx.annotation.StringRes
import cl.lossless.player.R

/** Texto visible (traducido) del origen de la letra. */
@get:StringRes
val LyricsSource.labelRes: Int
    get() = when (this) {
        LyricsSource.SIDECAR -> R.string.lyrics_source_sidecar
        LyricsSource.EMBEDDED -> R.string.lyrics_source_embedded
        LyricsSource.LRCLIB -> R.string.lyrics_source_lrclib
        LyricsSource.USER -> R.string.lyrics_source_user
        LyricsSource.ALIGNED -> R.string.lyrics_source_aligned
    }
