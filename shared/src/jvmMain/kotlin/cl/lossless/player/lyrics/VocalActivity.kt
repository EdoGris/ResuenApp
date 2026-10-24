package cl.lossless.player.lyrics

import cl.lossless.player.playback.eq.Biquad
import cl.lossless.player.playback.eq.BiquadCoefficients
import kotlin.math.log10

/** Tramo de la canción en el que (probablemente) hay voz. */
data class VocalSegment(val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

/**
 * Detección aproximada de los tramos con voz, sin internet ni modelos de IA.
 *
 * En casi todas las mezclas la voz principal va al centro y ocupa ~250–3500 Hz. Por cada
 * ventana de [HOP_MS] se mide la energía de esa banda en el canal central (L+R) y en los
 * costados (L−R): donde el centro domina y suena fuerte, probablemente se está cantando.
 * Es una heurística: sirve para repartir las líneas, no para un karaoke exacto.
 */
object VocalActivity {

    const val HOP_MS = 50

    /** Características por ventana: energía de la banda vocal al centro y a los costados (dB). */
    class Features(val midDb: FloatArray, val sideDb: FloatArray, val count: Int)

    /** Filtra la banda vocal del centro y los costados y acumula su energía por ventana. */
    class Accumulator(sampleRate: Int) {
        private val fs = sampleRate.toDouble()
        private val hop = (sampleRate * HOP_MS / 1000).coerceAtLeast(1)
        private fun bandFilters() = listOf(
            Biquad(BiquadCoefficients.highPass(250.0, fs)),
            Biquad(BiquadCoefficients.lowPass(3_500.0, fs)),
        )
        private val midFilters = bandFilters()
        private val sideFilters = bandFilters()
        private var midSum = 0.0
        private var sideSum = 0.0
        private var inHop = 0
        private var mid = FloatArray(4_096)
        private var side = FloatArray(4_096)
        var count = 0
            private set

        fun add(left: Double, right: Double) {
            var m = (left + right) * 0.5
            var s = (left - right) * 0.5
            for (f in midFilters) m = f.process(m)
            for (f in sideFilters) s = f.process(s)
            midSum += m * m
            sideSum += s * s
            if (++inHop == hop) {
                if (count == mid.size) { mid = mid.copyOf(count * 2); side = side.copyOf(count * 2) }
                mid[count] = toDb(midSum / hop)
                side[count] = toDb(sideSum / hop)
                count++
                midSum = 0.0; sideSum = 0.0; inHop = 0
            }
        }

        fun features() = Features(mid, side, count)

        private fun toDb(power: Double) = (10 * log10(power + 1e-10)).toFloat()
    }

    // ---------- Detección (pura, probada en tests) ----------

    /** Tramos con voz a partir de las características por ventana. */
    fun detect(features: Features): List<VocalSegment> {
        val n = features.count
        if (n == 0) return emptyList()
        // Puntaje: volumen de la banda vocal al centro + cuánto domina el centro sobre los costados.
        val score = FloatArray(n) { i ->
            val mid = features.midDb[i]
            mid + 0.5f * (mid - features.sideDb[i]).coerceIn(-12f, 24f)
        }
        val smooth = movingAverage(score, window = 600 / HOP_MS)
        val sorted = smooth.sortedArray()
        val low = sorted[(n * 0.30).toInt().coerceAtMost(n - 1)]
        val high = sorted[(n * 0.95).toInt().coerceAtMost(n - 1)]
        if (high - low < 3f) return emptyList() // sin contraste: no se puede distinguir nada
        val threshold = low + 0.35f * (high - low)

        // Ventanas activas → tramos; se unen huecos cortos y se descartan tramos muy breves.
        val raw = mutableListOf<IntRange>()
        var start = -1
        for (i in 0 until n) {
            val on = smooth[i] > threshold
            if (on && start < 0) start = i
            if (!on && start >= 0) { raw += start until i; start = -1 }
        }
        if (start >= 0) raw += start until n

        val maxGap = 700 / HOP_MS
        val merged = mutableListOf<IntRange>()
        for (r in raw) {
            val last = merged.lastOrNull()
            if (last != null && r.first - last.last <= maxGap) merged[merged.lastIndex] = last.first..r.last
            else merged += r
        }
        val minLength = 800 / HOP_MS
        return merged
            .filter { it.last - it.first + 1 >= minLength }
            .map { VocalSegment(it.first.toLong() * HOP_MS, (it.last + 1).toLong() * HOP_MS) }
    }

    private fun movingAverage(values: FloatArray, window: Int): FloatArray {
        val half = (window / 2).coerceAtLeast(1)
        val prefix = DoubleArray(values.size + 1)
        for (i in values.indices) prefix[i + 1] = prefix[i] + values[i]
        return FloatArray(values.size) { i ->
            val a = (i - half).coerceAtLeast(0)
            val b = (i + half + 1).coerceAtMost(values.size)
            ((prefix[b] - prefix[a]) / (b - a)).toFloat()
        }
    }
}
