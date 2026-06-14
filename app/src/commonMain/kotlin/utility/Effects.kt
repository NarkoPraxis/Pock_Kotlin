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

    fun addPersistentEffect(effect: PersistentEffect) {
        pendingEffects.add(effect)
    }

    fun addPriorityEffect(effect: PersistentEffect) {
        pendingPriorityEffects.add(effect)
    }

    fun clearPersistentEffects() {
        persistentEffects.clear()
        pendingEffects.clear()
        priorityEffects.clear()
        pendingPriorityEffects.clear()
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
        val persistIter = persistentEffects.iterator()
        while (persistIter.hasNext()) {
            val e = persistIter.next()
            e.step()
            e.draw(this)
            if (e.isDone) persistIter.remove()
        }
        if (pendingEffects.isNotEmpty()) {
            persistentEffects.addAll(pendingEffects)
            pendingEffects.clear()
        }

        val collIter = collisions.iterator()
        while (collIter.hasNext()) {
            val c = collIter.next()
            with(c) { drawTo() }
            if (c.finished) collIter.remove()
        }
    }

    /** Draws effects that must overlay the balls. Called after the players are drawn. */
    fun DrawScope.drawPriorityEffects() {
        val iter = priorityEffects.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            e.step()
            e.draw(this)
            if (e.isDone) iter.remove()
        }
        if (pendingPriorityEffects.isNotEmpty()) {
            priorityEffects.addAll(pendingPriorityEffects)
            pendingPriorityEffects.clear()
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
