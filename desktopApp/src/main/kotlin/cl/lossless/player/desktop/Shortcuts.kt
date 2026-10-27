package cl.lossless.player.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.github.kwhat.jnativehook.GlobalScreen
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener
import java.util.logging.Level
import java.util.logging.Logger

/** Acciones de reproducción que se pueden asignar a una tecla. */
enum class PlaybackAction(val label: Str) {
    PLAY_PAUSE(S.shortcutPlayPause),
    STOP(S.shortcutStop),
    NEXT(S.shortcutNext),
    PREVIOUS(S.shortcutPrevious),
    FORWARD(S.shortcutForward),
    REWIND(S.shortcutRewind),
}

/** Una combinación de teclas: la tecla y los modificadores que deben estar pulsados. */
data class KeyCombo(val keyCode: Long, val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false, val meta: Boolean = false) {

    fun matches(e: KeyEvent): Boolean =
        e.key.keyCode == keyCode && e.isCtrlPressed == ctrl && e.isAltPressed == alt && e.isShiftPressed == shift && e.isMetaPressed == meta

    /** "Ctrl+→", "⌘S", "Espacio"… */
    fun label(space: String): String = buildString {
        if (IsMac) {
            if (ctrl) append("⌃"); if (alt) append("⌥"); if (shift) append("⇧"); if (meta) append("⌘")
        } else {
            if (ctrl) append("Ctrl+"); if (alt) append("Alt+"); if (shift) append("Shift+"); if (meta) append("Win+")
        }
        append(keyName(Key(keyCode), space))
    }

    fun encode(): String = listOf(keyCode, ctrl, alt, shift, meta).joinToString(":")

    companion object {
        fun from(e: KeyEvent) = KeyCombo(e.key.keyCode, e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.isMetaPressed)

        fun decode(raw: String): KeyCombo? {
            val p = raw.split(':')
            if (p.size != 5) return null
            val code = p[0].toLongOrNull() ?: return null
            return KeyCombo(code, p[1].toBoolean(), p[2].toBoolean(), p[3].toBoolean(), p[4].toBoolean())
        }

        /** Ctrl en Windows, ⌘ en macOS. */
        fun primary(key: Key) = if (IsMac) KeyCombo(key.keyCode, meta = true) else KeyCombo(key.keyCode, ctrl = true)

        private fun keyName(key: Key, space: String): String = when (key) {
            Key.Spacebar -> space
            Key.DirectionRight -> "→"
            Key.DirectionLeft -> "←"
            Key.DirectionUp -> "↑"
            Key.DirectionDown -> "↓"
            else -> key.toString().removePrefix("Key: ")
        }

        private val MODIFIERS = setOf(
            Key.CtrlLeft, Key.CtrlRight, Key.AltLeft, Key.AltRight, Key.ShiftLeft, Key.ShiftRight,
            Key.MetaLeft, Key.MetaRight,
        )

        fun isModifier(key: Key) = key in MODIFIERS
    }
}

/**
 * Atajos de teclado de reproducción, configurables en Ajustes. Funcionan con la ventana de
 * ResuenApp (completa o mini) activa; las teclas multimedia del teclado (▶︎⏸, ⏹, ⏭, ⏮)
 * funcionan además con la app en segundo plano.
 */
object Shortcuts {

    val DEFAULTS: Map<PlaybackAction, KeyCombo> = mapOf(
        PlaybackAction.PLAY_PAUSE to KeyCombo(Key.Spacebar.keyCode),
        PlaybackAction.STOP to KeyCombo.primary(Key.S),
        PlaybackAction.NEXT to KeyCombo.primary(Key.DirectionRight),
        PlaybackAction.PREVIOUS to KeyCombo.primary(Key.DirectionLeft),
        PlaybackAction.FORWARD to KeyCombo(Key.DirectionRight.keyCode),
        PlaybackAction.REWIND to KeyCombo(Key.DirectionLeft.keyCode),
    )

    /** Asignación actual; una acción sin entrada no tiene tecla. */
    val bindings = mutableStateMapOf<PlaybackAction, KeyCombo>().apply { putAll(AppSettings.loadShortcuts() ?: DEFAULTS) }

    /** Acción que espera su nueva tecla (se eligió "cambiar" en Ajustes). */
    var capturing by mutableStateOf<PlaybackAction?>(null)

    // El setter privado chocaría en la JVM con setMediaKeys(Boolean): se le da otro nombre.
    @set:JvmName("updateMediaKeys")
    var mediaKeys by mutableStateOf(AppSettings.mediaKeys)
        private set

    const val SEEK_STEP_MS = 10_000L

    fun assign(action: PlaybackAction, combo: KeyCombo?) {
        // Una misma tecla no puede hacer dos cosas: se quita de la acción que la tenía.
        if (combo != null) bindings.entries.firstOrNull { it.key != action && it.value == combo }?.let { bindings.remove(it.key) }
        if (combo == null) bindings.remove(action) else bindings[action] = combo
        AppSettings.saveShortcuts(bindings.toMap())
    }

    fun resetDefaults() {
        bindings.clear()
        bindings.putAll(DEFAULTS)
        AppSettings.saveShortcuts(DEFAULTS)
    }

    /**
     * Para el onPreviewKeyEvent de cada ventana: solo atiende la captura de una tecla nueva en
     * Ajustes (Esc cancela, Supr/Retroceso la deja sin tecla).
     */
    fun handleCapture(e: KeyEvent): Boolean {
        val action = capturing ?: return false
        if (e.type != KeyEventType.KeyDown) return true
        if (KeyCombo.isModifier(e.key)) return true
        when (e.key) {
            Key.Escape -> Unit
            Key.Delete, Key.Backspace -> assign(action, null)
            else -> assign(action, KeyCombo.from(e))
        }
        capturing = null
        return true
    }

    /**
     * Para el onKeyEvent de cada ventana: llega solo si nada enfocado usó la tecla, así escribir
     * un espacio en la búsqueda no pausa la música.
     */
    fun handle(e: KeyEvent, player: Player): Boolean {
        if (e.type != KeyEventType.KeyDown) return false
        val action = bindings.entries.firstOrNull { it.value.matches(e) }?.key ?: return false
        perform(action, player)
        return true
    }

    fun perform(action: PlaybackAction, player: Player) {
        val hasTrack = player.state.value.current != null
        when (action) {
            PlaybackAction.PLAY_PAUSE -> if (hasTrack) player.toggle()
            PlaybackAction.STOP -> player.stop()
            PlaybackAction.NEXT -> player.next()
            PlaybackAction.PREVIOUS -> if (hasTrack) player.previous()
            PlaybackAction.FORWARD -> player.seekBy(SEEK_STEP_MS)
            PlaybackAction.REWIND -> player.seekBy(-SEEK_STEP_MS)
        }
    }

    // ---------- Teclas multimedia globales (JNativeHook) ----------

    private var player: Player? = null
    private var hooked = false

    private val mediaListener = object : NativeKeyListener {
        override fun nativeKeyPressed(e: NativeKeyEvent) {
            val p = player ?: return
            val action = when (e.keyCode) {
                NativeKeyEvent.VC_MEDIA_PLAY -> PlaybackAction.PLAY_PAUSE
                NativeKeyEvent.VC_MEDIA_STOP -> PlaybackAction.STOP
                NativeKeyEvent.VC_MEDIA_NEXT -> PlaybackAction.NEXT
                NativeKeyEvent.VC_MEDIA_PREVIOUS -> PlaybackAction.PREVIOUS
                else -> return
            }
            perform(action, p)
        }
    }

    /** Llamar una vez al abrir la app. */
    fun start(player: Player) {
        this.player = player
        if (mediaKeys) hook()
    }

    fun setMediaKeys(enabled: Boolean) {
        mediaKeys = enabled
        AppSettings.mediaKeys = enabled
        if (enabled) hook() else unhook()
    }

    fun shutdown() = unhook()

    private fun hook() {
        if (hooked) return
        // JNativeHook registra cada tecla en el log de Java; no hace falta.
        Logger.getLogger(GlobalScreen::class.java.packageName).apply { level = Level.OFF; useParentHandlers = false }
        hooked = runCatching {
            GlobalScreen.registerNativeHook()
            GlobalScreen.addNativeKeyListener(mediaListener)
        }.isSuccess
    }

    private fun unhook() {
        if (!hooked) return
        runCatching {
            GlobalScreen.removeNativeKeyListener(mediaListener)
            GlobalScreen.unregisterNativeHook()
        }
        hooked = false
    }
}
