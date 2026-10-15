package cl.lossless.player.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Handler
import android.os.Looper
import cl.lossless.player.R

/**
 * Activa el modo bit-perfect de Android 14+ en DAC USB.
 *
 * En este modo el mezclador del sistema se salta: no hay remuestreo, ni mezcla con
 * otros sonidos, ni volumen por software (el volumen lo controla el DAC). Solo aplica
 * a dispositivos USB; para jack y Bluetooth Android no ofrece esta vía.
 */
class BitPerfectController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(AudioManager::class.java)

    // Debe coincidir con los atributos que usa ExoPlayer (USAGE_MEDIA).
    private val musicAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    @Volatile private var activeDevice: AudioDeviceInfo? = null
    @Volatile private var lastSpec: AudioSpec? = null

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
            lastSpec?.let { apply(it) }
        }
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
            if (removed.any { it.id == activeDevice?.id }) activeDevice = null
            lastSpec?.let { apply(it) }
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        // Texto inicial de la salida, ya en el idioma de la app.
        PlaybackStatus.output.compareAndSet(
            PlaybackStatus.UNKNOWN,
            appContext.getString(R.string.player_output_standard),
        )
    }

    /** Configura la salida para la frecuencia y canales del archivo que va a sonar. */
    fun apply(spec: AudioSpec) {
        lastSpec = spec
        val usb = findUsbOutput()
        if (usb == null) {
            clear()
            PlaybackStatus.output.value = describeNonUsbOutput()
            return
        }

        val candidate = audioManager.getSupportedMixerAttributes(usb)
            .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
            .filter {
                it.format.sampleRate == spec.sampleRate &&
                    it.format.channelCount == spec.channelCount
            }
            .minByOrNull { encodingPriority(it.format.encoding) }

        if (candidate == null) {
            clear()
            PlaybackStatus.output.value = appContext.getString(
                R.string.player_output_usb_no_bit_perfect,
                spec.sampleRate,
                spec.channelCount,
            )
            return
        }

        val ok = audioManager.setPreferredMixerAttributes(musicAttributes, usb, candidate)
        activeDevice = if (ok) usb else null
        PlaybackStatus.output.value = if (ok) {
            "${PlaybackStatus.BIT_PERFECT_PREFIX} · ${usb.productName} · ${candidate.format.sampleRate} Hz · " +
                encodingName(candidate.format.encoding)
        } else {
            appContext.getString(R.string.player_output_bit_perfect_rejected, usb.productName)
        }
    }

    fun clear() {
        activeDevice?.let { audioManager.clearPreferredMixerAttributes(musicAttributes, it) }
        activeDevice = null
    }

    fun release() {
        clear()
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
    }

    private fun findUsbOutput(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

    private fun describeNonUsbOutput(): String {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        return when {
            outputs.any { it == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP } ->
                appContext.getString(R.string.player_output_bluetooth)
            outputs.any { it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it == AudioDeviceInfo.TYPE_WIRED_HEADSET } ->
                appContext.getString(R.string.player_output_wired)
            else -> appContext.getString(R.string.player_output_standard)
        }
    }

    // ExoPlayer entrega PCM float de 32 bits (ver LosslessRenderersFactory), que
    // representa con exactitud muestras de 16 y 24 bits. Se prefiere un mezclador
    // con el mismo formato.
    private fun encodingPriority(encoding: Int) = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> 0
        AudioFormat.ENCODING_PCM_32BIT -> 1
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 2
        AudioFormat.ENCODING_PCM_16BIT -> 3
        else -> 9
    }

    private fun encodingName(encoding: Int) = when (encoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> "float32"
        AudioFormat.ENCODING_PCM_32BIT -> appContext.getString(R.string.player_encoding_bits, 32)
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> appContext.getString(R.string.player_encoding_bits, 24)
        AudioFormat.ENCODING_PCM_16BIT -> appContext.getString(R.string.player_encoding_bits, 16)
        else -> appContext.getString(R.string.player_encoding_other, encoding)
    }
}
