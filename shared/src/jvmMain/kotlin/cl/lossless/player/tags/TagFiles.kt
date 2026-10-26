package cl.lossless.player.tags

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** Campos que se pueden editar a mano, en el orden en que aparecen en el editor. */
enum class TagField(val key: FieldKey, val perTrack: Boolean = false, val numeric: Boolean = false) {
    TITLE(FieldKey.TITLE, perTrack = true),
    ARTIST(FieldKey.ARTIST),
    ALBUM(FieldKey.ALBUM),
    ALBUM_ARTIST(FieldKey.ALBUM_ARTIST),
    YEAR(FieldKey.YEAR, numeric = true),
    GENRE(FieldKey.GENRE),
    TRACK(FieldKey.TRACK, perTrack = true, numeric = true),
    DISC(FieldKey.DISC_NO, numeric = true),
}

/**
 * Lee y escribe etiquetas de archivos de audio con jaudiotagger (FLAC, MP3, M4A/ALAC, WAV, OGG).
 * Lo usan el editor de escritorio y la edición de varias canciones en Android.
 */
object TagFiles {

    init {
        // jaudiotagger escribe mucho en el registro por cada archivo.
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    /** Valor de cada campo del archivo (vacío si no lo tiene o no se pudo leer). Bloqueante. */
    fun read(file: File): Map<TagField, String> {
        val tag = runCatching { AudioFileIO.read(file).tag }.getOrNull()
        return TagField.entries.associateWith { f ->
            tag?.let { runCatching { it.getFirst(f.key) }.getOrNull() }?.trim().orEmpty()
        }
    }

    /** Escribe [changes] en el archivo; un valor vacío borra el campo. Bloqueante. */
    fun write(file: File, changes: Map<TagField, String>) {
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        for ((field, value) in changes) {
            val v = value.trim()
            if (v.isEmpty()) runCatching { tag.deleteField(field.key) } else tag.setField(field.key, v)
        }
        audio.commit()
    }
}
