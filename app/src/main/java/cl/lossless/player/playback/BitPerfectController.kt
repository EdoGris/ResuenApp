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

/**
 * Activa el modo bit-perfect de Android 14+ en DAC USB.
 *
 * En este modo el mezclador del sistema se salta: no hay remuestreo, ni mezcla con
 * otros sonidos, ni volumen por software (el volumen lo controla el DAC). Solo aplica
 * a dispositivos USB; para jack y Bluetooth Android no ofrece esta vía.
 */
class BitPerfectController(context: Context) {

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
            PlaybackStatus.output.value =
                "DAC USB conectado, pero no ofrece bit-perfect a ${spec.sampleRate} Hz " +
                    "/ ${spec.channelCount} ch: se usa el mezclador (remuestreo)"
            return
        }

        val ok = audioManager.setPreferredMixerAttributes(musicAttributes, usb, candidate)
        activeDevice = if (ok) usb else null
        PlaybackStatus.output.value = if (ok) {
            "BIT-PERFECT · ${usb.productName} · ${candidate.format.sampleRate} Hz · " +
                encodingName(candidate.format.encoding)
        } else {
            "El sistema rechazó el modo bit-perfect en ${usb.productName}"
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
                "Bluetooth: decodificación sin pérdida, pero el enlace recomprime (códec BT)"
            outputs.any { it == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it == AudioDeviceInfo.TYPE_WIRED_HEADSET } ->
                "Audífonos con cable: sin pérdida hasta el mezclador del sistema"
            else -> "Salida estándar del sistema"
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
        AudioFormat.ENCODING_PCM_32BIT -> "32 bits"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24 bits"
        AudioFormat.ENCODING_PCM_16BIT -> "16 bits"
        else -> "encoding $encoding"
    }
}
