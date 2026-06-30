package utility

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import enums.Direction
import gameobjects.Settings
import physics.Point
import shapes.Explosion

object Effects {

    interface PersistentEffect {
        fun draw(scope: DrawScope)
        fun step()
        val isDone: Boolean
        fun onScoreSignal(): Boolean = false
    }

    val collisions = MutableList(0) { Explosion() }
    private val persistentEffects = mutableListOf<PersistentEffect>()
    private val pendingEffects = mutableListOf<PersistentEffect>()

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

    fun clearCollisionEffects() {
        for (collision in collisions) {
            if (collision.angle == Direction.FULL) {
                collision.implode()
            }
        }
    }

    fun DrawScope.drawEffects() {
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

        var ci = 0
        while (ci < collisions.size) {
            val c = collisions[ci]
            with(c) { drawTo() }
            if (c.finished) collisions.removeAt(ci) else ci++
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

    fun addWallCollisionEffect(bounceDirection: Direction, fillColor: Int, puckPosition: Point) {
        val effectInt = PaintBucket.effectColor.toArgb()
        val bgInt = PaintBucket.backgroundColor.toArgb()
        when (bounceDirection) {
            Direction.LEFT   -> collisions.add(Explosion(effectInt, fillColor, bgInt, Point(Settings.shortParticleSide, puckPosition.y), Settings.ballRadius * 1.5f, false, Direction.LEFT))
            Direction.RIGHT  -> collisions.add(Explosion(effectInt, fillColor, bgInt, Point(Settings.screenWidth - Settings.shortParticleSide, puckPosition.y), Settings.ballRadius * 1.5f, false, Direction.RIGHT))
            Direction.TOP    -> collisions.add(Explosion(effectInt, fillColor, bgInt, Point(puckPosition.x, Settings.topGoalBottom), Settings.ballRadius * 1.5f, false, Direction.TOP))
            Direction.BOTTOM -> collisions.add(Explosion(effectInt, fillColor, bgInt, Point(puckPosition.x, Settings.bottomGoalTop), Settings.ballRadius * 1.5f, false, Direction.BOTTOM))
            else -> {}
        }
    }
}
