package cl.lossless.player.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

/** Idiomas de la app, los mismos cuatro que en Android. */
enum class Lang(val code: String, val nativeName: String) {
    ES("es", "Español"),
    EN("en", "English"),
    FR("fr", "Français"),
    PT("pt", "Português");

    companion object {
        fun fromCode(code: String?): Lang? = entries.firstOrNull { it.code == code }
        fun system(): Lang = fromCode(Locale.getDefault().language) ?: ES
    }
}

/** Un texto en los cuatro idiomas. [format] reemplaza %1$s, %2$s… como en Android. */
class Str(private val es: String, private val en: String, private val fr: String, private val pt: String) {
    fun get(lang: Lang): String = when (lang) {
        Lang.ES -> es
        Lang.EN -> en
        Lang.FR -> fr
        Lang.PT -> pt
    }
}

val LocalLang = staticCompositionLocalOf { Lang.ES }

@Composable
fun t(s: Str): String = s.get(LocalLang.current)

@Composable
fun t(s: Str, vararg args: Any): String = String.format(s.get(LocalLang.current), *args)

/** Textos de la versión de escritorio. */
object S {
    val songs = Str("Canciones", "Songs", "Titres", "Músicas")
    val albums = Str("Álbumes", "Albums", "Albums", "Álbuns")
    val folders = Str("Carpetas", "Folders", "Dossiers", "Pastas")
    val settings = Str("Ajustes", "Settings", "Réglages", "Ajustes")
    val search = Str("Buscar", "Search", "Rechercher", "Pesquisar")
    val back = Str("Volver", "Back", "Retour", "Voltar")
    val playAll = Str("Reproducir todo", "Play all", "Tout lire", "Tocar tudo")

    val emptyTitle = Str(
        "Agrega tu carpeta de música",
        "Add your music folder",
        "Ajoutez votre dossier de musique",
        "Adicione sua pasta de música",
    )
    val emptyBody = Str(
        "ResuenApp lee FLAC, ALAC, WAV, MP3, AAC y OGG de las carpetas que elijas.",
        "ResuenApp plays FLAC, ALAC, WAV, MP3, AAC and OGG from the folders you choose.",
        "ResuenApp lit les FLAC, ALAC, WAV, MP3, AAC et OGG des dossiers choisis.",
        "O ResuenApp toca FLAC, ALAC, WAV, MP3, AAC e OGG das pastas que você escolher.",
    )
    val addFolder = Str("Agregar carpeta", "Add folder", "Ajouter un dossier", "Adicionar pasta")
    val removeFolder = Str("Quitar", "Remove", "Retirer", "Remover")
    val rescan = Str("Volver a escanear", "Rescan", "Réanalyser", "Escanear de novo")
    val scanning = Str("Escaneando… %1\$d archivos", "Scanning… %1\$d files", "Analyse… %1\$d fichiers", "Escaneando… %1\$d arquivos")
    val trackCount = Str("%1\$d canciones", "%1\$d songs", "%1\$d titres", "%1\$d músicas")
    val noResults = Str("Sin resultados", "No results", "Aucun résultat", "Nenhum resultado")
    val unknownArtist = Str("Artista desconocido", "Unknown artist", "Artiste inconnu", "Artista desconhecido")
    val unknownAlbum = Str("Álbum desconocido", "Unknown album", "Album inconnu", "Álbum desconhecido")
    val variousArtists = Str("Varios artistas", "Various artists", "Artistes divers", "Vários artistas")

    val libraryFolders = Str("Carpetas de música", "Music folders", "Dossiers de musique", "Pastas de música")
    val language = Str("Idioma", "Language", "Langue", "Idioma")
    val languageSystem = Str("Idioma del sistema", "System language", "Langue du système", "Idioma do sistema")
    val chooseFolder = Str("Elige una carpeta de música", "Choose a music folder", "Choisissez un dossier de musique", "Escolha uma pasta de música")

    val output = Str("Salida de audio", "Audio output", "Sortie audio", "Saída de áudio")
    val outputShared = Str(
        "Mezclador de Windows (modo compartido)",
        "Windows mixer (shared mode)",
        "Mélangeur Windows (mode partagé)",
        "Mixer do Windows (modo compartilhado)",
    )
    val outputNote = Str(
        "Esta primera versión decodifica sin pérdida, pero Windows puede remuestrear. El modo bit-perfect (WASAPI exclusivo) llegará en una próxima versión.",
        "This first version decodes losslessly, but Windows may resample. Bit-perfect mode (WASAPI exclusive) is coming in a later version.",
        "Cette première version décode sans perte, mais Windows peut rééchantillonner. Le mode bit-perfect (WASAPI exclusif) arrivera dans une prochaine version.",
        "Esta primeira versão decodifica sem perdas, mas o Windows pode reamostrar. O modo bit-perfect (WASAPI exclusivo) virá numa próxima versão.",
    )
    val outputFormat = Str("Archivo %1\$s · salida %2\$s", "File %1\$s · output %2\$s", "Fichier %1\$s · sortie %2\$s", "Arquivo %1\$s · saída %2\$s")
    val volume = Str("Volumen", "Volume", "Volume", "Volume")
    val about = Str("Acerca de", "About", "À propos", "Sobre")
    val version = Str("Versión %1\$s", "Version %1\$s", "Version %1\$s", "Versão %1\$s")

    val play = Str("Reproducir", "Play", "Lecture", "Tocar")
    val pause = Str("Pausa", "Pause", "Pause", "Pausar")
    val next = Str("Siguiente", "Next", "Suivant", "Próxima")
    val previous = Str("Anterior", "Previous", "Précédent", "Anterior")
    val nothingPlaying = Str("Nada sonando", "Nothing playing", "Rien en lecture", "Nada tocando")
    val playbackError = Str("No se pudo reproducir: %1\$s", "Couldn't play: %1\$s", "Lecture impossible : %1\$s", "Não foi possível tocar: %1\$s")

    val lyrics = Str("Letra", "Lyrics", "Paroles", "Letra")
    val lyricsLoading = Str("Buscando letra…", "Looking for lyrics…", "Recherche des paroles…", "Procurando letra…")
    val lyricsNone = Str("No se encontró letra", "No lyrics found", "Aucunes paroles trouvées", "Nenhuma letra encontrada")
    val lyricsInstrumental = Str("Instrumental", "Instrumental", "Instrumental", "Instrumental")
    val lyricsOffline = Str(
        "Sin conexión con LRCLIB",
        "Couldn't reach LRCLIB",
        "Impossible de joindre LRCLIB",
        "Sem conexão com o LRCLIB",
    )
    val lyricsSource = Str("Fuente: %1\$s", "Source: %1\$s", "Source : %1\$s", "Fonte: %1\$s")
    val sourceSidecar = Str("archivo .lrc", ".lrc file", "fichier .lrc", "arquivo .lrc")
    val sourceEmbedded = Str("etiquetas del archivo", "file tags", "étiquettes du fichier", "tags do arquivo")
    val sourceLrclib = Str("LRCLIB", "LRCLIB", "LRCLIB", "LRCLIB")
}
