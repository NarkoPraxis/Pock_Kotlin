package utility

import androidx.compose.ui.graphics.drawscope.DrawScope
import enums.Direction
import gameobjects.Settings
import physics.Point
import shapes.FlashBurst
import shapes.FlashTuning
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Effects {

    interface PersistentEffect {
        fun draw(scope: DrawScope)
        fun step()
        val isDone: Boolean
        fun onScoreSignal(): Boolean = false
    }

    private val persistentEffects = mutableListOf<PersistentEffect>()
    private val pendingEffects = mutableListOf<PersistentEffect>()

    // Impact flash bursts (see Plans/Impact Effects): sharp, short-lived collision "POW" bursts drawn
    // in front of the pucks, consistent across every game and never styled by ball type. Separate from
    // the per-ball persistentEffects above. Gated by Settings.impactEffectsEnabled.
    private val flashEffects = mutableListOf<FlashBurst>()
    private val pendingFlashEffects = mutableListOf<FlashBurst>()

    // Priority effects draw in front of the balls (via drawPriorityEffects, called after the
    // players are drawn) rather than behind them like regular persistent effects. Used for the rare
    // case where an effect must overlay its own skin — e.g. the dragon's victory fire breath.
    private val priorityEffects = mutableListOf<PersistentEffect>()
    private val pendingPriorityEffects = mutableListOf<PersistentEffect>()

    // Score effects: the paddle-toss landing celebration (ScoredPaddle → spawnPaddleScoreCelebration).
    // These are NOT drawn here; the score dial draws them (via drawScoreEffects) interleaved between its
    // section backgrounds and its numerals, in whichever pass (lifted/normal) the dial uses — so the
    // burst reads as landing inside the number. Kept in their own list so routing them here never
    // reorders the regular persistent/priority effects.
    private val scoreEffects = mutableListOf<PersistentEffect>()
    private val pendingScoreEffects = mutableListOf<PersistentEffect>()

    // While true, addPersistentEffect / addPriorityEffect divert into scoreEffects. Set only around the
    // paddle-toss celebration spawn so its bursts join the dial layer; the pierced-ball pop burst (also
    // an onUsedToScore call) stays a normal persistent effect because it fires with this flag false.
    var routeToScoreEffects = false

    fun addPersistentEffect(effect: PersistentEffect) {
        if (routeToScoreEffects) pendingScoreEffects.add(effect) else pendingEffects.add(effect)
    }

    fun addPriorityEffect(effect: PersistentEffect) {
        if (routeToScoreEffects) pendingScoreEffects.add(effect) else pendingPriorityEffects.add(effect)
    }

    fun clearPersistentEffects() {
        persistentEffects.clear()
        pendingEffects.clear()
        priorityEffects.clear()
        pendingPriorityEffects.clear()
        scoreEffects.clear()
        pendingScoreEffects.clear()
    }

    fun signalScored() {
        val iter = persistentEffects.iterator()
        while (iter.hasNext()) {
            if (!iter.next().onScoreSignal()) iter.remove()
        }
        val priorityIter = priorityEffects.iterator()
        while (priorityIter.hasNext()) {
            if (!priorityIter.next().onScoreSignal()) priorityIter.remove()
        }
    }

    fun DrawScope.drawEffects() {
        // Persistent-effect layer is gated by the Graphics "Persistent Effects" setting. When off,
        // drawing them is a no-op; we also drop any active/queued ones so they can't accumulate
        // unbounded (step never runs to retire them) or burst back on when re-enabled. Priority/score
        // effects and the wall-collision bursts below are unaffected.
        if (Settings.persistentEffectsEnabled) {
            // Indexed while-loops (not iterators) so this per-frame path never allocates an ArrayList
            // iterator — note .iterator() allocates even when the list is empty.
            var pi = 0
            while (pi < persistentEffects.size) {
                val e = persistentEffects[pi]
                e.step()
                e.draw(this)
                if (e.isDone) persistentEffects.removeAt(pi) else pi++
            }
            if (pendingEffects.isNotEmpty()) {
                persistentEffects.addAll(pendingEffects)
                pendingEffects.clear()
            }
        } else {
            if (persistentEffects.isNotEmpty()) persistentEffects.clear()
            if (pendingEffects.isNotEmpty()) pendingEffects.clear()
        }
    }

    /** Draws effects that must overlay the balls. Called after the players are drawn. */
    fun DrawScope.drawPriorityEffects() {
        var i = 0
        while (i < priorityEffects.size) {
            val e = priorityEffects[i]
            e.step()
            e.draw(this)
            if (e.isDone) priorityEffects.removeAt(i) else i++
        }
        if (pendingPriorityEffects.isNotEmpty()) {
            priorityEffects.addAll(pendingPriorityEffects)
            pendingPriorityEffects.clear()
        }
    }

    /**
     * Draws the score-dial bursts (the paddle-toss landing celebration). Called by ScoreDial between
     * its section backgrounds and its numerals, so each burst sits in front of the dial face but behind
     * the digit it feeds. Exactly one dial pass renders per frame, so this steps each effect once/frame.
     */
    fun DrawScope.drawScoreEffects() {
        var i = 0
        while (i < scoreEffects.size) {
            val e = scoreEffects[i]
            e.step()
            e.draw(this)
            if (e.isDone) scoreEffects.removeAt(i) else i++
        }
        if (pendingScoreEffects.isNotEmpty()) {
            scoreEffects.addAll(pendingScoreEffects)
            pendingScoreEffects.clear()
        }
    }

    /** Signed rotation (±[amount]) that turns [axis] toward [normal] the shortest way. */
    private fun rotationToward(axis: Float, normal: Float, amount: Float): Float {
        val d = atan2(sin(normal - axis), cos(normal - axis))
        return if (d >= 0f) amount else -amount
    }

    /**
     * Fires a Wall-on-Ball impact: TWO flash sub-bursts to either side of the collision point along
     * the wall it hit (up/down for a vertical side wall, left/right for a goal). [colorArgb] is the
     * ball's baked spark colour; [intensity] scales the burst (Plan 01 passes a flat 1f; Plan 02 feeds
     * a context-derived value). No-op when Impact Effects are disabled.
     */
    fun addWallCollisionEffect(
        bounceDirection: Direction,
        colorArgb: Int,
        puckPosition: Point,
        intensity: Float = 1f,
        angleFactor: Float = 1f,   // 1 = head-on/perpendicular (symmetric), 0 = fully glancing
        tangentSign: Float = 1f    // sign of along-wall travel: +1 = along the +axis sub-burst (A), -1 = toward B
    ) {
        if (!Settings.impactEffectsEnabled) return
        val sr = Settings.screenRatio
        val off = FlashTuning.wallSubBurstOffset * sr
        // Collision point clamped to the wall it hit, the along-wall axis the two sub-bursts fan on, and
        // the outward normal (from the wall into the play field) the cones are rotated toward.
        val cx: Float; val cy: Float; val axisRad: Float; val normalRad: Float
        when (bounceDirection) {
            Direction.LEFT   -> { cx = Settings.shortParticleSide; cy = puckPosition.y; axisRad = PI.toFloat() / 2f; normalRad = 0f }
            Direction.RIGHT  -> { cx = Settings.screenWidth - Settings.shortParticleSide; cy = puckPosition.y; axisRad = PI.toFloat() / 2f; normalRad = PI.toFloat() }
            Direction.TOP    -> { cx = puckPosition.x; cy = Settings.topGoalBottom; axisRad = 0f; normalRad = PI.toFloat() / 2f }
            Direction.BOTTOM -> { cx = puckPosition.x; cy = Settings.bottomGoalTop; axisRad = 0f; normalRad = -PI.toFloat() / 2f }
            else -> return
        }
        // Each sub-burst's cone points ALONG the wall (up/down or left/right) but is rotated toward the
        // play field by exactly the cone's half-angle, so its wall-side edge grazes the wall and no
        // particle can be aimed into it. The wall blocks the ball; it blocks the sparks too.
        val coneHalf = FlashTuning.coneSpreadDeg * (PI.toFloat() / 180f)
        val centerA = axisRad + rotationToward(axisRad, normalRad, coneHalf)
        val centerB = (axisRad + PI.toFloat()).let { it + rotationToward(it, normalRad, coneHalf) }
        // Angle-driven asymmetry: a head-on hit (angleFactor→1) leaves the two sub-bursts equal; a
        // glancing hit (angleFactor→0) throws the FORWARD (along-travel) burst farther/bigger and at
        // full intensity, while the OPPOSITE (against-travel) burst is both shorter-reaching AND much
        // less intense (fewer/shorter/smaller particles). tangentSign says which sub-burst is forward:
        // A points +axis, so A is forward when tangentSign >= 0.
        val glancing = (1f - angleFactor).coerceIn(0f, 1f)
        val fwdScale = 1f + glancing * FlashTuning.wallForwardSizeBias
        val bwdScale = (1f - glancing * FlashTuning.wallBackwardSizeBias).coerceAtLeast(FlashTuning.wallBackwardSizeMin)
        val bwdIntensity = intensity *
            (1f - glancing * FlashTuning.wallBackwardIntensityBias).coerceAtLeast(FlashTuning.wallBackwardIntensityMin)
        val aScale = if (tangentSign >= 0f) fwdScale else bwdScale
        val bScale = if (tangentSign >= 0f) bwdScale else fwdScale
        val aIntensity = if (tangentSign >= 0f) intensity else bwdIntensity
        val bIntensity = if (tangentSign >= 0f) bwdIntensity else intensity
        // Sub-burst A points +axis, B points -axis; offset each along the axis so they read as a pair.
        val dx = cos(axisRad); val dy = sin(axisRad)
        pendingFlashEffects.add(FlashBurst(cx + dx * off, cy + dy * off, colorArgb, centerA, aIntensity, sizeScale = aScale))
        pendingFlashEffects.add(FlashBurst(cx - dx * off, cy - dy * off, colorArgb, centerB, bIntensity, sizeScale = bScale))
    }

    /**
     * Fires a Ball-on-Ball impact: ONE forward "shotgun" burst along the ball's own pre-collision
     * [headingRad], with a little side-to-side spray. Called once per ball (twice per collision), each
     * with that ball's baked colour + intensity. [scatter] (0 head-on … 1 glancing) widens the main
     * cone so a glancing clip reads as more scattered. No-op when Impact Effects are disabled.
     */
    fun addBallCollisionEffect(
        x: Float,
        y: Float,
        colorArgb: Int,
        headingRad: Float,
        intensity: Float,
        scatter: Float
    ) {
        if (!Settings.impactEffectsEnabled) return
        val spread = FlashTuning.ballConeSpreadDeg * (1f + scatter.coerceIn(0f, 1f) * FlashTuning.ballScatterScale)
        pendingFlashEffects.add(
            FlashBurst(
                x, y, colorArgb, headingRad, intensity,
                coneSpreadDeg = spread,
                sideDotCount = FlashTuning.ballSideDotCount
            )
        )
    }

    /**
     * Steps, draws, and retires the impact flash bursts. Called from the draw pass AFTER the pucks so
     * the sparks pop over the balls. When Impact Effects are off this draws nothing and drops any
     * in-flight/queued bursts (they never step to retire otherwise). Indexed while-loop (not
     * .iterator()) to stay allocation-free, matching drawEffects/drawPriorityEffects.
     */
    fun DrawScope.drawFlashEffects() {
        if (!Settings.impactEffectsEnabled) {
            if (flashEffects.isNotEmpty()) flashEffects.clear()
            if (pendingFlashEffects.isNotEmpty()) pendingFlashEffects.clear()
            return
        }
        var i = 0
        while (i < flashEffects.size) {
            val e = flashEffects[i]
            e.step()
            with(e) { drawTo() }
            if (e.isDone) flashEffects.removeAt(i) else i++
        }
        if (pendingFlashEffects.isNotEmpty()) {
            flashEffects.addAll(pendingFlashEffects)
            pendingFlashEffects.clear()
        }
    }

    fun clearFlashEffects() {
        flashEffects.clear()
        pendingFlashEffects.clear()
    }
}
