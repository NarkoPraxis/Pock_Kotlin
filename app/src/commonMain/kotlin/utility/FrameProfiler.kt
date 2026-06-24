package utility

/**
 * Dev-only frame/section profiler. Zero heap allocation on the per-frame path:
 * all buffers are sized once (object init or startSession), and the per-frame work
 * is arithmetic into primitive arrays. Serialization + file write happen ONLY in
 * endSession(), off the hot path. Everything early-returns when !enabled.
 *
 * Turning it off for release: [enabled] defaults false and is only set from
 * [Storage.profilerEnabled] (writable only from the dev-only Settings toggle, itself gated behind
 * [DEV_TOOLS]). Flip [DEV_TOOLS] to false for a release build to compile out the toggle and HUD;
 * the begin/end/onFrame calls then early-return on the boolean and the JIT/AOT can inline them away.
 */
object FrameProfiler {
    /** Master compile-time gate for all dev profiling UI (Settings toggle + in-game HUD). */
    const val DEV_TOOLS = false

    var enabled: Boolean = false            // gate behind a debug flag (see Storage.profilerEnabled)

    // ---- coarse section ids (the "where is the time going" breakdown) ----
    const val S_ARENA = 0      // background, walls, score zones, non-puck arena
    const val S_SKIN = 1       // PuckSkin.drawBody for both pucks
    const val S_TAIL = 2       // TailRenderer for both pucks
    const val S_PADDLE = 3     // PaddleLaunchEffect for both pucks
    const val S_PARTICLES = 4  // Explosion / ScoreExplosion / effects
    const val S_HUD = 5        // overlay/labels/score text (incl. this HUD)
    const val S_LOGIC = 6      // per-frame game-state tick (collision/score/danger/bot) — not a draw section
    const val SECTION_COUNT = 7
    val sectionNames = arrayOf("arena", "skin", "tail", "paddle", "particles", "hud", "logic")

    // ---- live rolling window (HUD only) ----
    private const val WINDOW = 120          // ~2s at 60fps
    private val frameMs = FloatArray(WINDOW)
    private var idx = 0
    private var filled = 0
    var hudAvgMs: Float = 0f; private set
    var hudWorstMs: Float = 0f; private set
    var hudJank: Int = 0; private set

    // ---- per-frame section scratch (reset every frame, never reallocated) ----
    private val sectionStart = LongArray(SECTION_COUNT)
    private val sectionAccum = FloatArray(SECTION_COUNT)   // ms accumulated THIS frame
    private val sectionLast = FloatArray(SECTION_COUNT)    // last completed frame's accum (HUD readout)

    /** Last completed frame's time in [section], for the live HUD breakdown. */
    fun hudSectionMs(section: Int): Float = sectionLast[section]

    // ---- session aggregates ----
    private var sessionActive = false
    private var sessionBall = ""
    private var sessionLabel = ""
    private var sessionStartNanos = 0L
    private var warmupUntilNanos = 0L
    private const val WARMUP_MS = 1000L                     // discard first ~1s
    private var lastFrameNanos = 0L
    private var jankThresholdMul = 1.5f
    private var targetIntervalMs = 16f

    private var samples = FloatArray(0)                     // frame ms, alloc'd at startSession
    private var sampleCount = 0
    private val sectionSum = DoubleArray(SECTION_COUNT)     // session ms totals per section
    private val sectionMax = FloatArray(SECTION_COUNT)      // session worst single-frame per section
    private var jankCount = 0
    private var gcAtStart = -1L

    // ===== per-frame path (hot — must not allocate) =====

    /** Call once at the very top of Drawing.drawFrame(). */
    fun onFrame(nowNanos: Long, targetMs: Float) {
        if (!enabled) return
        targetIntervalMs = targetMs

        // 1) frame delta from previous frame
        if (lastFrameNanos != 0L) {
            val deltaMs = (nowNanos - lastFrameNanos) / 1_000_000f
            // rolling HUD window (always, even before a session/warm-up)
            frameMs[idx] = deltaMs
            idx = (idx + 1) % WINDOW
            if (filled < WINDOW) filled++
            var sum = 0f; var worst = 0f; var jank = 0
            for (i in 0 until filled) {
                val v = frameMs[i]; sum += v
                if (v > worst) worst = v
                if (v > targetMs * jankThresholdMul) jank++
            }
            hudAvgMs = sum / filled; hudWorstMs = worst; hudJank = jank

            // session recording (after warm-up only)
            if (sessionActive && nowNanos >= warmupUntilNanos) {
                if (sampleCount < samples.size) samples[sampleCount++] = deltaMs
                if (deltaMs > targetMs * jankThresholdMul) jankCount++
                // roll the PREVIOUS frame's section accumulation into the session
                for (s in 0 until SECTION_COUNT) {
                    val v = sectionAccum[s]
                    sectionSum[s] += v
                    if (v > sectionMax[s]) sectionMax[s] = v
                }
            }
        }
        lastFrameNanos = nowNanos
        // 2) snapshot the just-drawn frame's section times for the HUD, then clear for the next frame
        for (s in 0 until SECTION_COUNT) {
            sectionLast[s] = sectionAccum[s]
            sectionAccum[s] = 0f
        }
    }

    /** Wrap a draw region: begin(S_SKIN) ... end(S_SKIN). Cheap; nanoTime only. */
    fun begin(section: Int) {
        if (!enabled || !sessionActive) return
        sectionStart[section] = nowNanos()
    }

    fun end(section: Int) {
        if (!enabled || !sessionActive) return
        sectionAccum[section] += (nowNanos() - sectionStart[section]) / 1_000_000f
    }

    // ===== session control (cold — allocation OK here, off hot path) =====

    fun startSession(ballName: String, label: String, maxFrames: Int = 4096) {
        if (!enabled) return
        samples = FloatArray(maxFrames)         // ONE allocation, before frames run
        sampleCount = 0
        sectionSum.fill(0.0); sectionMax.fill(0f)
        jankCount = 0
        sessionBall = ballName; sessionLabel = label
        gcAtStart = readGcCount()
        lastFrameNanos = 0L
        sessionStartNanos = nowNanos()
        warmupUntilNanos = sessionStartNanos + WARMUP_MS * 1_000_000L
        sessionActive = true
    }

    /** Stops the session, serializes the JSON, writes it, returns the file path. */
    fun endSession(): String {
        if (!enabled || !sessionActive) return ""
        sessionActive = false
        val gcDelta = if (gcAtStart >= 0) readGcCount() - gcAtStart else -1L
        val json = ProfileReport.toJson(
            ball = sessionBall,
            label = sessionLabel,
            targetMs = targetIntervalMs,
            jankThresholdMul = jankThresholdMul,
            jankCount = jankCount,
            gcDelta = gcDelta,
            sectionNames = sectionNames,
            sectionSum = sectionSum,
            sectionMax = sectionMax,
            samples = samples,
            sampleCount = sampleCount,
        )
        val name = "pock-profile_${sessionBall}_${sessionLabel}_${nowNanos()}.json"
        return writeProfileFile(name, json)
    }

    val isSessionActive: Boolean get() = sessionActive
    val currentBall: String get() = sessionBall
    val currentLabel: String get() = sessionLabel
}
