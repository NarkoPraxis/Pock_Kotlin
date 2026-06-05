package gameobjects.puckstyle

import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The single canonical "swoosh" a tail traces when a ball is shown statically in UI
 * (the ball-selection carousel). Instead of trailing the puck's live motion, a tail in
 * [PuckRenderer.staticUiMode] poses itself along this fixed curve — a frozen screenshot of
 * the ball mid-flight.
 *
 * The curve is authored once here, in **radius units**, with the ball head at the origin and
 * local +y pointing "down" the swoosh (away from the ball). Consumers scale by the ball radius
 * and translate to the ball's screen position. Because the high-player carousel canvas is already
 * mirrored 180°, the same local curve renders correctly for both players.
 *
 * Two consumers share the one curve:
 *  - **List tails** (Classic, Neon, …) sample by fraction along the arc — see [worldByFraction].
 *  - **Bone tails** (Dragon, Cat, Axolotl) walk the arc at fixed segment spacing — see [poseSpineAlong].
 */
object StaticTailPath {

    // The swoosh is a quarter-ellipse arc (radius units). Picture an ellipse whose left edge sits at
    // the ball centre and whose centre is off the ball's right side: the tail follows that ellipse
    // from its left edge (the ball) down to its bottom, so it leaves the ball heading straight down
    // and hooks to the RIGHT. Flatter/curvier than a long straight trail (the "squished, more
    // pronounced" look). Massage the shape here:
    //   SWOOSH_RX — horizontal reach of the hook (bigger = curls further right)
    //   SWOOSH_RY — vertical reach / tail length (bigger = longer, taller arc)
    private const val SWOOSH_RX = 2.8f
    private const val SWOOSH_RY = 3.6f
    private const val KAPPA = 0.5523f   // Bézier handle length for a clean quarter ellipse/circle

    private const val P0X = 0f;                       private const val P0Y = 0f                 // ball (ellipse left edge)
    private const val P1X = 0f;                       private const val P1Y = SWOOSH_RY * KAPPA
    private const val P2X = SWOOSH_RX * (1f - KAPPA); private const val P2Y = SWOOSH_RY
    private const val P3X = SWOOSH_RX;                private const val P3Y = SWOOSH_RY          // ellipse bottom

    private const val SAMPLES = 96

    private val sampleX = FloatArray(SAMPLES + 1)
    private val sampleY = FloatArray(SAMPLES + 1)
    private val cumLen = FloatArray(SAMPLES + 1)   // cumulative arc length, radius units

    /** Total arc length of the swoosh, in radius units. */
    val totalLength: Float

    init {
        sampleX[0] = P0X; sampleY[0] = P0Y; cumLen[0] = 0f
        var prevX = P0X; var prevY = P0Y; var acc = 0f
        for (s in 1..SAMPLES) {
            val t = s.toFloat() / SAMPLES
            val x = bezier(P0X, P1X, P2X, P3X, t)
            val y = bezier(P0Y, P1Y, P2Y, P3Y, t)
            acc += hypot(x - prevX, y - prevY)
            sampleX[s] = x; sampleY[s] = y; cumLen[s] = acc
            prevX = x; prevY = y
        }
        totalLength = acc
    }

    private fun bezier(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val u = 1f - t
        return u * u * u * a + 3f * u * u * t * b + 3f * u * t * t * c + t * t * t * d
    }

    /** Local point (radius units) at the given arc length along the curve. */
    private fun localAtLength(lenInRadii: Float): Offset {
        val target = lenInRadii.coerceIn(0f, totalLength)
        var idx = 1
        while (idx < SAMPLES && cumLen[idx] < target) idx++
        val l0 = cumLen[idx - 1]; val l1 = cumLen[idx]
        val t = if (l1 > l0) (target - l0) / (l1 - l0) else 0f
        val x = sampleX[idx - 1] + (sampleX[idx] - sampleX[idx - 1]) * t
        val y = sampleY[idx - 1] + (sampleY[idx] - sampleY[idx - 1]) * t
        return Offset(x, y)
    }

    /**
     * Absolute screen point for a trailing index expressed as [ratio] (0 = ball head, 1 = tail tip).
     * Used by the list tails, which already address their points by index ratio.
     */
    fun worldByFraction(ratio: Float, radius: Float, headX: Float, headY: Float): Offset {
        val p = localAtLength(ratio.coerceIn(0f, 1f) * totalLength)
        return Offset(headX + p.x * radius, headY + p.y * radius)
    }

    /**
     * Pose a bone-tail spine statically along the swoosh. Each segment is placed at a fixed
     * arc-length step (`spacingPx` apart, matching the live tails' inter-segment spacing) starting
     * from the head, and [segAngle] is set to the local heading from the previous segment — the same
     * contract the bone tails' own `fillTo`/integration step produces, so the existing body-path,
     * width and shadow code downstream works unchanged.
     *
     * The three biological tails feed their own (differently sized) bone arrays in here, so the
     * posing math lives in exactly one place.
     */
    fun poseSpineAlong(
        spineX: FloatArray,
        spineY: FloatArray,
        segAngle: FloatArray,
        count: Int,
        spacingPx: Float,
        headX: Float,
        headY: Float,
        radius: Float
    ) {
        val safeRadius = radius.coerceAtLeast(0.001f)
        var prevX = headX; var prevY = headY
        for (i in 0 until count) {
            val targetLenRadii = (spacingPx * (i + 1)) / safeRadius
            val p = localAtLength(targetLenRadii)
            val wx = headX + p.x * radius
            val wy = headY + p.y * radius
            spineX[i] = wx
            spineY[i] = wy
            segAngle[i] = atan2(wy - prevY, wx - prevX)
            prevX = wx; prevY = wy
        }
    }
}
