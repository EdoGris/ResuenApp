package cl.lossless.player.desktop

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer
import java.nio.DoubleBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10
import kotlin.math.roundToInt

data class PlayerState(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val playing: Boolean = false,
    val durationMs: Long = 0,
    /** "24 bits / 96 kHz" de la salida abierta, o null si no hay nada sonando. */
    val outputFormat: String? = null,
    val error: String? = null,
) {
    val current: Track? get() = queue.getOrNull(index)
}

/**
 * Cola de reproducción y motor de audio: FFmpeg decodifica a PCM de 32 bits y Java Sound lo
 * envía a la salida predeterminada de Windows con la frecuencia del archivo, en 24 bits si se
 * puede (16 si no). Es modo compartido: Windows mezcla y puede remuestrear.
 */
class Player {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    @Volatile private var session: Session? = null
    @Volatile private var thread: Thread? = null
    @Volatile var volume: Float = AppSettings.volume
        private set

    /** La cola se terminó de reproducir; si llegan más pistas con [append], sigue con ellas. */
    @Volatile private var endedAtTail = false

    fun playQueue(tracks: List<Track>, start: Int) {
        if (tracks.isEmpty()) return
        endedAtTail = false
        _state.update { it.copy(queue = tracks, index = start.coerceIn(tracks.indices), error = null) }
        startCurrent()
    }

    fun toggle() {
        val s = session
        if (s == null) {
            if (_state.value.current != null) startCurrent()
            return
        }
        s.paused = !s.paused
        _state.update { it.copy(playing = !s.paused) }
    }

    fun next() {
        val st = _state.value
        if (st.index + 1 < st.queue.size) {
            _state.update { it.copy(index = it.index + 1, error = null) }
            startCurrent()
        }
    }

    fun previous() {
        val st = _state.value
        if (positionMs() > 3_000 || st.index == 0) {
            seek(0)
        } else if (st.index > 0) {
            _state.update { it.copy(index = it.index - 1, error = null) }
            startCurrent()
        }
    }

    fun seek(ms: Long) {
        session?.seekToMs = ms.coerceAtLeast(0)
    }

    fun positionMs(): Long = session?.positionMs() ?: 0

    fun setVolume(v: Float) {
        volume = v.coerceIn(0f, 1f)
        AppSettings.volume = volume
        session?.applyVolume(volume)
    }

    /**
     * Corre [block] con los archivos [paths] cerrados: si uno es el que suena, detiene la salida,
     * espera a que FFmpeg lo suelte (Windows no deja reescribir un archivo abierto) y luego
     * retoma en el mismo punto, en pausa si lo estaba. Bloqueante.
     */
    fun <T> withFilesReleased(paths: Set<String>, block: () -> T): T {
        val s = session
        val current = _state.value.current
        if (s == null || current == null || current.path !in paths) return block()
        val position = s.positionMs()
        val paused = s.paused
        s.stop()
        thread?.join(2_000)
        try {
            return block()
        } finally {
            if (session === s) {
                startCurrent()
                session?.let { it.seekToMs = position; it.paused = paused }
                if (paused) _state.update { it.copy(playing = false) }
            }
        }
    }

    /**
     * Agrega [tracks] al final de la cola (las pistas de Drive llegan a medida que se descargan).
     * Si la cola ya se había terminado esperando la siguiente, sigue con la primera agregada.
     */
    fun append(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        _state.update { it.copy(queue = it.queue + tracks) }
        if (endedAtTail && session == null) {
            endedAtTail = false
            _state.update { it.copy(index = it.index + 1, error = null) }
            startCurrent()
        }
    }

    /** Cambia en la cola las pistas cuyas etiquetas se editaron, sin cortar la reproducción. */
    fun replaceTracks(updated: Map<String, Track>) {
        _state.update { st -> st.copy(queue = st.queue.map { updated[it.path] ?: it }) }
    }

    fun shutdown() {
        session?.stop()
        session = null
    }

    private fun startCurrent() {
        endedAtTail = false
        session?.stop()
        val track = _state.value.current ?: return
        val s = Session(track)
        session = s
        _state.update { it.copy(playing = true, durationMs = track.durationMs, outputFormat = null) }
        thread = Thread({ s.run() }, "resuenapp-audio").apply { isDaemon = true; start() }
    }

    private fun onEnded(s: Session) {
        if (session !== s) return
        val st = _state.value
        if (st.index + 1 < st.queue.size) {
            _state.update { it.copy(index = it.index + 1) }
            startCurrent()
        } else {
            session = null
            endedAtTail = true
            _state.update { it.copy(playing = false, outputFormat = null) }
        }
    }

    private fun onFailed(s: Session, error: Throwable) {
        if (session !== s) return
        session = null
        _state.update { it.copy(playing = false, outputFormat = null, error = error.message ?: error.javaClass.simpleName) }
    }

    /** Una pista sonando. Corre en su propio hilo; los controles le llegan por campos volátiles. */
    private inner class Session(private val track: Track) {
        @Volatile var paused = false
        @Volatile var seekToMs = -1L
        @Volatile private var stopped = false
        @Volatile private var line: SourceDataLine? = null
        @Volatile private var baseMs = 0L
        @Volatile private var baseFrame = 0L
        @Volatile private var rate = 44_100

        fun stop() {
            stopped = true
        }

        fun positionMs(): Long {
            val l = line ?: return baseMs
            return baseMs + (l.longFramePosition - baseFrame) * 1000 / rate
        }

        fun applyVolume(v: Float) {
            val l = line ?: return
            if (!l.isControlSupported(FloatControl.Type.MASTER_GAIN)) return
            val gain = l.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val db = if (v <= 0.001f) gain.minimum else (20 * log10(v)).coerceIn(gain.minimum, gain.maximum)
            gain.value = db
        }

        fun run() {
            var grabber: FFmpegFrameGrabber? = null
            var out: SourceDataLine? = null
            try {
                grabber = Decoder.open(track.file)
                rate = grabber.sampleRate
                val channels = grabber.audioChannels
                val (opened, bits) = openLine(rate, channels, track.bitsPerSample)
                out = opened
                line = opened
                applyVolume(volume)
                _state.update { it.copy(outputFormat = "$bits bits / ${khz(rate)} kHz", durationMs = durationOf(grabber)) }
                opened.start()
                val bytesPerFrame = channels * bits / 8
                var buffer = ByteArray(0)

                while (!stopped) {
                    val seek = seekToMs
                    if (seek >= 0) {
                        seekToMs = -1
                        grabber.setTimestamp(seek * 1000)
                        opened.flush()
                        baseMs = seek
                        baseFrame = opened.longFramePosition
                    }
                    if (paused) {
                        if (opened.isRunning) opened.stop()
                        Thread.sleep(20)
                        continue
                    } else if (!opened.isRunning) {
                        opened.start()
                    }
                    val frame = grabber.grabSamples() ?: break
                    val samples = Decoder.toInt32Interleaved(frame, channels) ?: continue
                    val needed = samples.size / channels * bytesPerFrame
                    if (buffer.size < needed) buffer = ByteArray(needed)
                    val n = Decoder.pack(samples, bits, buffer)
                    var offset = 0
                    while (offset < n && !stopped && seekToMs < 0) {
                        offset += opened.write(buffer, offset, n - offset)
                    }
                }
                if (!stopped) {
                    opened.drain()
                    onEnded(this)
                }
            } catch (t: Throwable) {
                if (!stopped) onFailed(this, t)
            } finally {
                line = null
                runCatching { out?.stop() }
                runCatching { out?.close() }
                runCatching { grabber?.stop() }
                runCatching { grabber?.release() }
            }
        }

        private fun durationOf(g: FFmpegFrameGrabber): Long =
            (g.lengthInTime / 1000).takeIf { it > 0 } ?: track.durationMs
    }

    /** Abre la salida en 24 bits si el archivo lo es y el sistema lo acepta; si no, en 16. */
    private fun openLine(rate: Int, channels: Int, sourceBits: Int): Pair<SourceDataLine, Int> {
        val depths = if (sourceBits > 16) listOf(24, 16) else listOf(16)
        var last: Exception? = null
        for (bits in depths) {
            val format = AudioFormat(rate.toFloat(), bits, channels, true, false)
            try {
                val line = AudioSystem.getSourceDataLine(format)
                val bytesPerSecond = rate * channels * bits / 8
                line.open(format, bytesPerSecond / 4)
                return line to bits
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("No audio output")
    }
}

/** Decodificación con FFmpeg (javacv), separada para poder probarla sin salida de audio. */
object Decoder {

    fun open(file: java.io.File): FFmpegFrameGrabber {
        val g = FFmpegFrameGrabber(file)
        g.sampleFormat = avutil.AV_SAMPLE_FMT_S32
        g.start()
        if (g.audioChannels > 2) {
            // Java Sound no abre salidas multicanal de forma fiable: se mezcla a estéreo.
            g.stop(); g.release()
            return FFmpegFrameGrabber(file).apply {
                sampleFormat = avutil.AV_SAMPLE_FMT_S32
                audioChannels = 2
                start()
            }
        }
        return g
    }

    /** Muestras del cuadro como enteros de 32 bits intercalados (L R L R…). */
    fun toInt32Interleaved(frame: Frame, channels: Int): IntArray? {
        val buffers = frame.samples ?: return null
        if (buffers.isEmpty()) return null
        val planes = buffers.map { readPlane(it) }
        if (planes.size == 1) return planes[0]
        // Formato planar: un búfer por canal.
        val perChannel = planes.minOf { it.size }
        val out = IntArray(perChannel * channels)
        for (i in 0 until perChannel) for (c in 0 until channels) out[i * channels + c] = planes[c.coerceAtMost(planes.lastIndex)][i]
        return out
    }

    private fun readPlane(b: java.nio.Buffer): IntArray {
        val buf = b.duplicate().rewind()
        return when (buf) {
            is IntBuffer -> IntArray(buf.remaining()).also { buf.get(it) }
            is ShortBuffer -> IntArray(buf.remaining()) { buf.get().toInt() shl 16 }
            is FloatBuffer -> IntArray(buf.remaining()) { (buf.get().coerceIn(-1f, 1f) * Int.MAX_VALUE).roundToInt() }
            is DoubleBuffer -> IntArray(buf.remaining()) { (buf.get().coerceIn(-1.0, 1.0) * Int.MAX_VALUE).toInt() }
            is ByteBuffer -> IntArray(buf.remaining()) { ((buf.get().toInt() and 0xFF) - 128) shl 24 }
            else -> IntArray(0)
        }
    }

    /** Escribe las muestras en little-endian con [bits] (24 o 16). Devuelve los bytes escritos. */
    fun pack(samples: IntArray, bits: Int, out: ByteArray): Int {
        var o = 0
        if (bits == 24) {
            for (v in samples) {
                out[o++] = (v shr 8).toByte()
                out[o++] = (v shr 16).toByte()
                out[o++] = (v shr 24).toByte()
            }
        } else {
            for (v in samples) {
                out[o++] = (v shr 16).toByte()
                out[o++] = (v shr 24).toByte()
            }
        }
        return o
    }
}
