package cl.lossless.player.playback.eq

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Coeficientes normalizados (a0 = 1) de un filtro bicuadrático. */
data class BiquadCoefficients(
    val b0: Double, val b1: Double, val b2: Double,
    val a1: Double, val a2: Double,
) {
    /** Respuesta en magnitud (dB) a la frecuencia [freq] con frecuencia de muestreo [fs]. */
    fun magnitudeDb(freq: Double, fs: Double): Double {
        val w = 2 * PI * freq / fs
        val c1 = cos(w); val s1 = sin(w)
        val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nRe = b0 + b1 * c1 + b2 * c2
        val nIm = -(b1 * s1 + b2 * s2)
        val dRe = 1 + a1 * c1 + a2 * c2
        val dIm = -(a1 * s1 + a2 * s2)
        return 10 * log10((nRe * nRe + nIm * nIm) / (dRe * dRe + dIm * dIm))
    }

    companion object {
        /** Filtro peaking (campana) del "Audio EQ Cookbook" de R. Bristow-Johnson. */
        fun peaking(freq: Double, gainDb: Double, q: Double, fs: Double): BiquadCoefficients {
            val a = 10.0.pow(gainDb / 40)
            val w0 = 2 * PI * freq / fs
            val alpha = sin(w0) / (2 * q)
            val cosW = cos(w0)
            val a0 = 1 + alpha / a
            return BiquadCoefficients(
                b0 = (1 + alpha * a) / a0,
                b1 = (-2 * cosW) / a0,
                b2 = (1 - alpha * a) / a0,
                a1 = (-2 * cosW) / a0,
                a2 = (1 - alpha / a) / a0,
            )
        }

        /** Paso alto de 2.º orden (Butterworth con Q = 0.707). */
        fun highPass(freq: Double, fs: Double, q: Double = 0.7071): BiquadCoefficients {
            val w0 = 2 * PI * freq / fs
            val cosW = cos(w0)
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            return BiquadCoefficients(
                b0 = (1 + cosW) / 2 / a0,
                b1 = -(1 + cosW) / a0,
                b2 = (1 + cosW) / 2 / a0,
                a1 = -2 * cosW / a0,
                a2 = (1 - alpha) / a0,
            )
        }

        /** Paso bajo de 2.º orden (Butterworth con Q = 0.707). */
        fun lowPass(freq: Double, fs: Double, q: Double = 0.7071): BiquadCoefficients {
            val w0 = 2 * PI * freq / fs
            val cosW = cos(w0)
            val alpha = sin(w0) / (2 * q)
            val a0 = 1 + alpha
            return BiquadCoefficients(
                b0 = (1 - cosW) / 2 / a0,
                b1 = (1 - cosW) / a0,
                b2 = (1 - cosW) / 2 / a0,
                a1 = -2 * cosW / a0,
                a2 = (1 - alpha) / a0,
            )
        }

        /** Low-shelf con pendiente S = 1 (para el refuerzo de graves). */
        fun lowShelf(freq: Double, gainDb: Double, fs: Double): BiquadCoefficients {
            val a = 10.0.pow(gainDb / 40)
            val w0 = 2 * PI * freq / fs
            val cosW = cos(w0)
            val alpha = sin(w0) / 2 * sqrt(2.0)
            val sqA = 2 * sqrt(a) * alpha
            val a0 = (a + 1) + (a - 1) * cosW + sqA
            return BiquadCoefficients(
                b0 = a * ((a + 1) - (a - 1) * cosW + sqA) / a0,
                b1 = 2 * a * ((a - 1) - (a + 1) * cosW) / a0,
                b2 = a * ((a + 1) - (a - 1) * cosW - sqA) / a0,
                a1 = -2 * ((a - 1) + (a + 1) * cosW) / a0,
                a2 = ((a + 1) + (a - 1) * cosW - sqA) / a0,
            )
        }
    }
}

/** Estado de un filtro (forma directa II transpuesta, doble precisión). */
class Biquad(private var c: BiquadCoefficients) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun setCoefficients(coefficients: BiquadCoefficients) { c = coefficients }

    fun process(x: Double): Double {
        val y = c.b0 * x + z1
        z1 = c.b1 * x - c.a1 * y + z2
        z2 = c.b2 * x - c.a2 * y
        return y
    }

    fun reset() { z1 = 0.0; z2 = 0.0 }
}

object EqMath {
    /** Etapas de filtrado necesarias para [settings] (se omiten las bandas en 0 dB). */
    fun stages(settings: EqualizerSettings, fs: Double): List<BiquadCoefficients> = buildList {
        if (settings.bassBoost > 0f) {
            add(BiquadCoefficients.lowShelf(EqSpec.BASS_BOOST_FREQ.toDouble(), settings.bassBoost * EqSpec.BASS_BOOST_MAX_DB.toDouble(), fs))
        }
        EqSpec.FREQUENCIES.forEachIndexed { i, f ->
            val gain = settings.bands.getOrElse(i) { 0f }
            // Bandas por encima de ~Nyquist no se pueden aplicar a esta frecuencia de muestreo.
            if (gain != 0f && f < fs * 0.45) {
                add(BiquadCoefficients.peaking(f.toDouble(), gain.toDouble(), EqSpec.BAND_Q.toDouble(), fs))
            }
        }
    }

    /** Curva de respuesta total (dB) en [freqs], para dibujarla en la UI. */
    fun responseDb(settings: EqualizerSettings, freqs: DoubleArray, fs: Double = 48_000.0): DoubleArray {
        val stages = stages(settings, fs)
        return DoubleArray(freqs.size) { i ->
            settings.preampDb + stages.sumOf { it.magnitudeDb(freqs[i], fs) }
        }
    }
}
