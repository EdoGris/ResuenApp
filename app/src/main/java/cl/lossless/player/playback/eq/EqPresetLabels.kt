package cl.lossless.player.playback.eq

import androidx.annotation.StringRes
import cl.lossless.player.R

/** Nombre visible (traducido) de la presintonía; los datos viven en el módulo compartido. */
@get:StringRes
val EqPreset.nameRes: Int
    get() = when (id) {
        "flat" -> R.string.player_eq_preset_flat
        "rock" -> R.string.player_eq_preset_rock
        "pop" -> R.string.player_eq_preset_pop
        "jazz" -> R.string.player_eq_preset_jazz
        "classical" -> R.string.player_eq_preset_classical
        "dance" -> R.string.player_eq_preset_dance
        "hiphop" -> R.string.player_eq_preset_hiphop
        "acoustic" -> R.string.player_eq_preset_acoustic
        "bass" -> R.string.player_eq_preset_bass
        "vocal" -> R.string.player_eq_preset_vocal
        else -> R.string.player_eq_preset_custom
    }
