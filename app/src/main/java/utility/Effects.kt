package utility

import android.graphics.Canvas
import enums.Direction
import gameobjects.Settings
import physics.Point
import shapes.Explosion

object Effects {

    interface PersistentEffect {
        fun draw(canvas: Canvas)
        fun step()
        val isDone: Boolean
        /** Return true to handle own removal; false to be cleared immediately. Default: false. */
        fun onScoreSignal(): Boolean = false
    }

    val collisions = MutableList(0) { Explosion() }
    private val persistentEffects = mutableListOf<PersistentEffect>()
    private val pendingEffects = mutableListOf<PersistentEffect>()

    fun addPersistentEffect(effect: PersistentEffect) {
        pendingEffects.add(effect)
    }

    fun clearPersistentEffects() {
        persistentEffects.clear()
        pendingEffects.clear()
    }

    /** Signals a goal was scored. Effects that return true from onScoreSignal() animate out on their own;
     *  all others are removed immediately. */
    fun signalScored() {
        val iter = persistentEffects.iterator()
        while (iter.hasNext()) {
            if (!iter.next().onScoreSignal()) iter.remove()
        }
    }

    fun clearCollisionEffects() {
        for (collision in collisions) {
            if (collision.angle == Direction.FULL) {
                collision.implode()
            }
        }
    }

    fun drawEffects(canvas: Canvas) {
        val persistIter = persistentEffects.iterator()
        while (persistIter.hasNext()) {
            val e = persistIter.next()
            e.step()
            e.draw(canvas)
            if (e.isDone) persistIter.remove()
        }
        if (pendingEffects.isNotEmpty()) {
            persistentEffects.addAll(pendingEffects)
            pendingEffects.clear()
        }

        val collIter = collisions.iterator()
        while (collIter.hasNext()) {
            val c = collIter.next()
            c.drawTo(canvas)
            if (c.finished) collIter.remove()
        }
    }

    fun addWallCollisionEffect(bounceDirection: Direction, fillColor: Int, puckPosition: Point) {
        when(bounceDirection) {
            Direction.LEFT -> collisions.add(Explosion(PaintBucket.effectColor, fillColor, PaintBucket.backgroundColor, Point(Settings.shortParticleSide, puckPosition.y), Settings.ballRadius * 1.5f, false, Direction.LEFT))
            Direction.RIGHT -> collisions.add(Explosion(PaintBucket.effectColor, fillColor, PaintBucket.backgroundColor, Point(Settings.screenWidth - Settings.shortParticleSide, puckPosition.y), Settings.ballRadius * 1.5f, false, Direction.RIGHT))
            Direction.TOP -> collisions.add(Explosion(PaintBucket.effectColor, fillColor, PaintBucket.backgroundColor, Point(puckPosition.x, Settings.topGoalBottom), Settings.ballRadius * 1.5f, false, Direction.TOP))
            Direction.BOTTOM -> collisions.add(Explosion(PaintBucket.effectColor, fillColor, PaintBucket.backgroundColor, Point(puckPosition.x, Settings.bottomGoalTop), Settings.ballRadius * 1.5f, false, Direction.BOTTOM))
            else -> {}
        }
    }
}
