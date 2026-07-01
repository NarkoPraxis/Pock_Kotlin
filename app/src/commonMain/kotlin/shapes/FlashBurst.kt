package shapes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import physics.Ticker
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * One directional "POW!!" impact burst — a short-lived mix of tapered spike spines and small dots
 * that launch from [originX]/[originY] within a cone around [coneCenterRad], grow-then-die inside a
 * jittered lifespan, and fade out near end of life. Deliberately generic: Wall-on-Ball spawns two of
 * these (opposite along-wall axes); Ball-on-Ball (Plan 03) spawns one per ball along its heading.
 *
 * [intensity] (1f = baseline) gently scales particle count, size, and lifespan — Plan 01 always
 * passes 1f; Plan 02 feeds a real value. [colorArgb] is baked at spawn so a rainbow ball's spark
 * never strobes.
 *
 * Allocation discipline (render-performance convention): every per-particle array is allocated once
 * here at construction and Random is used ONLY here. The per-frame [drawTo] reads value-class
 * `Offset`/`Color` and allocates nothing.
 */
class FlashBurst(
    private val originX: Float,
    private val originY: Float,
    private val colorArgb: Int,
    coneCenterRad: Float,
    intensity: Float,
    coneSpreadDeg: Float = FlashTuning.coneSpreadDeg,   // cone half-angle (ball bursts pass a tighter/wider value)
    sizeScale: Float = 1f,                              // extra reach/size multiplier (wall asymmetry); 1 = unchanged
    sideDotCount: Int = 0                               // extra small, short-lived dots sprayed ±90° off the cone (ball-on-ball)
) {
    private val pAngleRad: FloatArray   // outward direction of each particle (cone center + jitter)
    private val pIsSpike: BooleanArray  // spike vs dot
    private val pMaxLen: FloatArray     // spike peak length, or dot radius (screenRatio units)
    private val pTravel: FloatArray     // dot flight distance, or spike inner-end break travel
    private val pPhase: FloatArray      // 0..1 life offset so particles don't move in lockstep
    private val pLifeScale: FloatArray  // <1 = particle finishes its arc sooner (short-lived side dots)
    private val life: Ticker
    private val spikePath = Path()      // reused each frame; accumulates all spike triangles for one fill

    init {
        val countFactor = 1f + (intensity - 1f) * FlashTuning.intensityCountScale
        // sizeScale folds the caller's per-burst reach multiplier into the intensity size factor so a
        // glancing wall hit's forward burst physically reaches farther without changing its cone width.
        val sizeFactor = (1f + (intensity - 1f) * FlashTuning.intensitySizeScale) * sizeScale
        val lifeFactor = 1f + (intensity - 1f) * FlashTuning.intensityLifeScale

        var nSpikes = (FlashTuning.spikeCount * countFactor).roundToInt().coerceAtLeast(0)
        var nDots = (FlashTuning.dotCount * countFactor).roundToInt().coerceAtLeast(0)
        val mainTotal = nSpikes + nDots
        if (mainTotal > FlashTuning.maxParticlesPerBurst && mainTotal > 0) {
            val scale = FlashTuning.maxParticlesPerBurst.toFloat() / mainTotal
            nSpikes = (nSpikes * scale).roundToInt()
            nDots = (nDots * scale).roundToInt()
        }
        val nMain = nSpikes + nDots
        val nSide = sideDotCount.coerceAtLeast(0)
        val n = nMain + nSide

        pAngleRad = FloatArray(n)
        pIsSpike = BooleanArray(n)
        pMaxLen = FloatArray(n)
        pTravel = FloatArray(n)
        pPhase = FloatArray(n)
        pLifeScale = FloatArray(n)

        val halfCone = coneSpreadDeg * (PI.toFloat() / 180f)
        for (i in 0 until nMain) {
            val spike = i < nSpikes
            pIsSpike[i] = spike
            pAngleRad[i] = coneCenterRad + (Random.nextFloat() * 2f - 1f) * halfCone
            pPhase[i] = Random.nextFloat()
            pLifeScale[i] = 1f
            if (spike) {
                pMaxLen[i] = ((FlashTuning.spikeMaxLen +
                        (Random.nextFloat() * 2f - 1f) * FlashTuning.spikeMaxLenJitter) * sizeFactor)
                    .coerceAtLeast(0f)
                pTravel[i] = FlashTuning.spikeBreakTravel * sizeScale
            } else {
                pMaxLen[i] = (FlashTuning.dotRadiusMin +
                        Random.nextFloat() * (FlashTuning.dotRadiusMax - FlashTuning.dotRadiusMin)) * sizeFactor
                pTravel[i] = (FlashTuning.dotTravel + Random.nextFloat() * FlashTuning.dotTravelJitter) * sizeScale
            }
        }

        // Side dots: a small sideways spray (±90° off the heading) that is smaller and dies sooner than
        // the main burst — the "little side-to-side" flick around a ball-on-ball shotgun.
        if (nSide > 0) {
            val sideHalf = FlashTuning.ballSideSpreadDeg * (PI.toFloat() / 180f)
            val perp = PI.toFloat() / 2f
            for (k in 0 until nSide) {
                val i = nMain + k
                val side = if (k % 2 == 0) 1f else -1f
                pIsSpike[i] = false
                pAngleRad[i] = coneCenterRad + side * perp + (Random.nextFloat() * 2f - 1f) * sideHalf
                pPhase[i] = Random.nextFloat()
                pLifeScale[i] = FlashTuning.ballSideLifeScale
                pMaxLen[i] = (FlashTuning.dotRadiusMin +
                        Random.nextFloat() * (FlashTuning.dotRadiusMax - FlashTuning.dotRadiusMin)) *
                        sizeFactor * FlashTuning.ballSideSizeScale
                pTravel[i] = (FlashTuning.dotTravel + Random.nextFloat() * FlashTuning.dotTravelJitter) *
                        sizeScale * FlashTuning.ballSideSizeScale
            }
        }

        val jitter = if (FlashTuning.lifeFramesJitter > 0)
            Random.nextInt(-FlashTuning.lifeFramesJitter, FlashTuning.lifeFramesJitter + 1) else 0
        val frames = ((FlashTuning.lifeFrames + jitter) * lifeFactor).roundToInt().coerceAtLeast(1)
        life = Ticker(frames, accending = true)
    }

    val isDone: Boolean get() = life.finished

    fun step() { life.tick }

    // Direct-coordinate trig into absolute screen space (per the DrawScope rotation gotcha — never
    // withTransform { rotate() }). Spikes are filled triangles (wide base at the origin end tapering to
    // a point at the tip); dots are circles. Allocation-free: spike triangles accumulate into the reused
    // spikePath for a single fill, and Offset/Color are value classes.
    fun DrawScope.drawTo() {
        val t = life.ratio
        val sr = Settings.screenRatio
        val fadeStart = FlashTuning.alphaFadeStart
        val a = (if (t < fadeStart) 1f else 1f - (t - fadeStart) / (1f - fadeStart)).coerceIn(0f, 1f)
        if (a <= 0f) return
        val col = Color(colorArgb).copy(alpha = a)

        val growEnd = FlashTuning.spikeGrowEnd
        val breakStart = FlashTuning.spikeBreakStart
        val halfBase = FlashTuning.spikeBaseWidth * sr * 0.5f

        spikePath.reset()
        var anySpike = false
        for (i in pAngleRad.indices) {
            // Dividing by pLifeScale (<1 for side dots) advances local time faster, so those particles
            // reach the end of their grow/shrink/fade arc sooner — a genuinely shorter life, not just
            // a smaller size. Main particles use 1f (unchanged).
            val lt = ((t - pPhase[i] * 0.3f) / pLifeScale[i]).coerceIn(0f, 1f)
            val dir = pAngleRad[i]
            val cosD = cos(dir)
            val sinD = sin(dir)
            if (pIsSpike[i]) {
                val grow = (lt / growEnd).coerceAtMost(1f)
                val shrink = ((lt - growEnd) / (1f - growEnd)).coerceIn(0f, 1f)
                val len = pMaxLen[i] * sr * grow * (1f - shrink)
                if (len <= 0f) continue
                val innerDist = if (lt < breakStart) 0f
                else pTravel[i] * sr * ((lt - breakStart) / (1f - breakStart))
                // Base centre (inner end), tip (outer end), and the base's two corners along the
                // perpendicular. Base narrows as the spine shrinks so the taper stays proportional.
                val bx = originX + cosD * innerDist
                val by = originY + sinD * innerDist
                val tipX = bx + cosD * len
                val tipY = by + sinD * len
                val hw = halfBase * (1f - shrink)
                val px = -sinD * hw
                val py = cosD * hw
                spikePath.moveTo(bx + px, by + py)
                spikePath.lineTo(tipX, tipY)
                spikePath.lineTo(bx - px, by - py)
                spikePath.close()
                anySpike = true
            } else {
                val radius = pMaxLen[i] * sr * (1f - lt)
                if (radius <= 0f) continue
                val ease = lt * (2f - lt)   // easeOut so dots decelerate outward
                val dist = pTravel[i] * sr * ease
                drawCircle(col, radius, Offset(originX + cosD * dist, originY + sinD * dist))
            }
        }
        if (anySpike) drawPath(spikePath, col)
    }
}
