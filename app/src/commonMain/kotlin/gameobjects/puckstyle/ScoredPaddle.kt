package gameobjects.puckstyle

import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import physics.Point
import physics.Ticker

/**
 * A "tossed" paddle (Score Redesign, Plan 3).
 *
 * When a player is scored on, this wrapper flies a static stand-in of the loser's paddle along a
 * curved arc from the pop point into the winner's number on the score dial — the number "collects"
 * the paddle. Rendering is delegated to the loser's live [PaddleLaunchEffect] via
 * [PaddleLaunchEffect.drawStandIn], so the flung paddle matches that player's selected ball (shape
 * and colours), not a generic bar. The loser's renderer survives the pop (only the ball visually
 * teleports), so its paddle can still be posed at an arbitrary point. It is reused across scores,
 * never reallocated, and computes its arc point into reused fields each frame.
 */
class ScoredPaddle {

    // Frames the toss takes from pop point to the number. Tuned slow enough that the eye can track
    // the paddle's whole flight (~0.9s at 60fps) before the number reacts.
    private val tossFrames = 54
    // Bézier control offset (perpendicular bow), × screenRatio.
    private val bowRatio = 4f
    // Degrees the bar tumbles over the flight (polish).
    private val spinDegrees = 200f

    var active = false
        private set

    /** Which dial number this toss feeds (the scoring player) — read on arrival to spin it. */
    var targetIsHigh = false
        private set

    private val ticker = Ticker(tossFrames, accending = true)
    // The loser's live paddle, posed each frame as the tossed stand-in. Null only before the first
    // spawn / after reset (draw no-ops then).
    private var paddle: PaddleLaunchEffect? = null

    // Quadratic Bézier: start → control → end. Held in fields (no per-frame allocation).
    private var sx = 0f; private var sy = 0f
    private var cx = 0f; private var cy = 0f
    private var ex = 0f; private var ey = 0f

    private var curX = 0f
    private var curY = 0f
    private var spin = 0f

    // Reused landing point for the arrival celebration (one spawn per score — no per-frame alloc).
    private val landPoint = Point()

    fun spawn(paddle: PaddleLaunchEffect, startX: Float, startY: Float, endX: Float, endY: Float, targetIsHigh: Boolean) {
        this.paddle = paddle
        this.targetIsHigh = targetIsHigh
        sx = startX; sy = startY
        ex = endX; ey = endY

        // Control point: midpoint pushed perpendicular to the chord, toward the screen centre, so the
        // paddle bows inward into the play area as it flies (a "tossing" arc).
        val mx = (sx + ex) * 0.5f
        val my = (sy + ey) * 0.5f
        val dx = ex - sx
        val dy = ey - sy
        val len = hypot(dx, dy).coerceAtLeast(1f)
        var px = -dy / len
        var py = dx / len
        // Pick the perpendicular that points toward the screen centre.
        val towardCx = Settings.middleX - mx
        val towardCy = Settings.middleY - my
        if (px * towardCx + py * towardCy < 0f) { px = -px; py = -py }
        val bow = Settings.screenRatio * bowRatio
        cx = mx + px * bow
        cy = my + py * bow

        curX = sx; curY = sy
        spin = 0f
        ticker.reset(tossFrames)
        active = true
    }

    /** Advance one frame. Returns true on the frame it lands (then goes inactive). */
    fun update(): Boolean {
        if (!active) return false
        val raw = ticker.ratio.coerceIn(0f, 1f)
        val t = 1f - (1f - raw) * (1f - raw)   // ease-out: settle into the number
        val mt = 1f - t
        curX = mt * mt * sx + 2f * mt * t * cx + t * t * ex
        curY = mt * mt * sy + 2f * mt * t * cy + t * t * ey
        spin = t * spinDegrees
        val landed = ticker.tick
        if (landed) {
            active = false
            // On the frame it vanishes into the number, burst the paddle-matched score celebration at
            // the number so it reads as the paddle exploding as it awards the point. Reuses each
            // design's existing onUsedToScore burst (see BallStyleFactory.spawnPaddleScoreCelebration).
            paddle?.let {
                landPoint.setLocation(ex, ey)
                BallStyleFactory.spawnPaddleScoreCelebration(it, landPoint, it.responsiveSecondary, targetIsHigh)
                // The stand-in kept spinning through its flight; re-align the paddle's own rotation
                // with the ball so a spinning paddle doesn't resume play out of phase (no-op for
                // paddles without a persistent spin; 0 when the ball doesn't rotate).
                it.syncRotationToBall()
            }
        }
        return landed
    }

    fun reset() {
        active = false
    }

    fun DrawScope.draw() {
        if (!active) return
        val p = paddle ?: return
        // Tumble is carried by the aim vector; drawStandIn poses the loser's real paddle here.
        val rad = spin * (PI.toFloat() / 180f)
        p.drawStandIn(this, curX, curY, cos(rad), sin(rad))
    }
}
