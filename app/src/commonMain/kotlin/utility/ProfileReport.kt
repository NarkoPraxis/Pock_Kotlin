package utility

import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Hand-rolled serializer for the per-session profile JSON (no serialization dependency).
 * Emits EXACTLY the shape documented in Plan 01 Part C — this is the contract Plan 02's agent
 * and Plan 03's report script both read, so keep it stable. Bump [SCHEMA] on any shape change.
 */
object ProfileReport {
    const val SCHEMA = 1

    fun toJson(
        ball: String,
        label: String,
        targetMs: Float,
        jankThresholdMul: Float,
        jankCount: Int,
        gcDelta: Long,
        sectionNames: Array<String>,
        sectionSum: DoubleArray,
        sectionMax: FloatArray,
        samples: FloatArray,
        sampleCount: Int,
    ): String {
        val count = sampleCount

        // ---- frame stats ----
        var avg = 0.0
        var worst = 0f
        if (count > 0) {
            var sum = 0.0
            for (i in 0 until count) {
                val v = samples[i]
                sum += v
                if (v > worst) worst = v
            }
            avg = sum / count
        }
        // Percentiles from a sorted copy (one cold allocation — fine off the hot path).
        val sorted = FloatArray(count)
        for (i in 0 until count) sorted[i] = samples[i]
        sorted.sort()
        val p50 = percentile(sorted, 0.50f)
        val p95 = percentile(sorted, 0.95f)
        val p99 = percentile(sorted, 0.99f)
        val jankPct = if (count > 0) jankCount * 100.0 / count else 0.0

        // ---- section stats ----
        val avgMs = DoubleArray(sectionNames.size)
        var avgTotal = 0.0
        for (s in sectionNames.indices) {
            avgMs[s] = if (count > 0) sectionSum[s] / count else 0.0
            avgTotal += avgMs[s]
        }

        val sb = StringBuilder(1024 + count * 6)
        sb.append("{\n")
        sb.append("  \"schema\": ").append(SCHEMA).append(",\n")
        sb.append("  \"ball\": ").append(quote(ball)).append(",\n")
        sb.append("  \"label\": ").append(quote(label)).append(",\n")
        sb.append("  \"device\": ").append(quote(profileDeviceDescription())).append(",\n")
        sb.append("  \"appVersion\": ").append(quote(profileAppVersion())).append(",\n")
        sb.append("  \"timestampMs\": ").append(PlatformStorage.currentTimeMs()).append(",\n")
        sb.append("  \"targetMs\": ").append(round1(targetMs.toDouble())).append(",\n")
        sb.append("  \"jankThresholdMul\": ").append(round1(jankThresholdMul.toDouble())).append(",\n")

        sb.append("  \"frames\": {\n")
        sb.append("    \"count\": ").append(count).append(",\n")
        sb.append("    \"avgMs\": ").append(round1(avg)).append(",\n")
        sb.append("    \"p50Ms\": ").append(round1(p50.toDouble())).append(",\n")
        sb.append("    \"p95Ms\": ").append(round1(p95.toDouble())).append(",\n")
        sb.append("    \"p99Ms\": ").append(round1(p99.toDouble())).append(",\n")
        sb.append("    \"worstMs\": ").append(round1(worst.toDouble())).append(",\n")
        sb.append("    \"jankCount\": ").append(jankCount).append(",\n")
        sb.append("    \"jankPct\": ").append(round1(jankPct)).append("\n")
        sb.append("  },\n")

        sb.append("  \"gc\": { \"countDelta\": ").append(gcDelta).append(" },\n")

        sb.append("  \"sections\": [\n")
        for (s in sectionNames.indices) {
            val sharePct = if (avgTotal > 0.0) avgMs[s] / avgTotal * 100.0 else 0.0
            sb.append("    { \"name\": ").append(quote(sectionNames[s]))
                .append(", \"avgMs\": ").append(round1(avgMs[s]))
                .append(", \"maxMs\": ").append(round1(sectionMax[s].toDouble()))
                .append(", \"sharePct\": ").append(round1(sharePct))
                .append(" }")
            if (s < sectionNames.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")

        sb.append("  \"frameSamplesMs\": [")
        for (i in 0 until count) {
            if (i > 0) sb.append(", ")
            sb.append(round1(samples[i].toDouble()))
        }
        sb.append("]\n")

        sb.append("}\n")
        return sb.toString()
    }

    private fun percentile(sorted: FloatArray, p: Float): Float {
        if (sorted.isEmpty()) return 0f
        val rank = (p * (sorted.size - 1)).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[rank]
    }

    /** Rounds to 1 decimal, emitting an integer-free representation like "21.3". */
    private fun round1(v: Double): String {
        val scaled = (v * 10.0).roundToLong()
        val whole = scaled / 10
        val frac = (if (scaled < 0) -scaled else scaled) % 10
        return "$whole.$frac"
    }

    /** Minimal JSON string escaping (these values are effectively ASCII). */
    private fun quote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
