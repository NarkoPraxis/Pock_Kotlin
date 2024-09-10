package utility

import android.graphics.Canvas
import enums.Direction
import gameobjects.Settings
import physics.Point
import shapes.Explosion
import shapes.ScoreExplosion

object Effects {
    val collisions = MutableList(0) { Explosion() }
    val scoreExplosions = MutableList(0) { ScoreExplosion() }

    fun clearCollisionEffects() {
        for (collision in collisions) {
            if (collision.angle == Direction.FULL) {
                collision.implode()
            }
        }
    }

    fun drawEffects(canvas: Canvas) {
        val tempCollisions = collisions.toList()
        for (collision in tempCollisions) {
            collision.drawTo(canvas)
            if (collision.finished) {
                collisions.remove(collision)
            }
        }
        val tempExplosion = scoreExplosions.toList()
        for (scoreExplosion in tempExplosion) {
            scoreExplosion.drawTo(canvas)
            if (scoreExplosion.finished) {
                scoreExplosions.remove(scoreExplosion)
            }
        }
    }


    fun addShieldedCollisionEffect(firstColor: Int, secondColor: Int, location: Point) {
        scoreExplosions.add(ScoreExplosion(firstColor, secondColor, location, Settings.screenRatio / 3f, true))
        scoreExplosions.add(ScoreExplosion(firstColor, secondColor, location, Settings.screenRatio / 3f, false))
    }

    fun addScoreEffect(firstColor: Int, secondColor: Int, location: Point, highGoal: Boolean) {
        scoreExplosions.add(ScoreExplosion(firstColor, secondColor, location, Settings.screenRatio / 3f, highGoal))
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

    fun addPuckCollisionEffect(firstSpeed: Float, secondSpeed: Float, firstColor: Int, secondColor: Int, collisionPoint: Point ) {
        if (firstSpeed > secondSpeed) {
            val radiusModifier = 3f * (firstSpeed / Settings.maxPuckSpeed)
            collisions.add(Explosion(PaintBucket.effectColor, firstColor, PaintBucket.backgroundColor, collisionPoint, Settings.screenRatio * radiusModifier, true, Direction.FULL, 100))
        } else if (secondSpeed > firstSpeed) {
            val radiusModifier = 3f * (secondSpeed / Settings.maxPuckSpeed)
            collisions.add(Explosion(PaintBucket.effectColor, secondColor, PaintBucket.backgroundColor, collisionPoint, Settings.screenRatio * radiusModifier, true, Direction.FULL, 100))
        } else {
            collisions.add(Explosion(PaintBucket.backgroundColor, PaintBucket.effectColor, PaintBucket.backgroundColor, collisionPoint, Settings.screenRatio * 3f, true, Direction.FULL, 100))
        }
    }
}