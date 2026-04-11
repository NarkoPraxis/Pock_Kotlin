package utility

import android.graphics.Canvas
import enums.Direction
import gameobjects.Settings
import physics.Point
import shapes.Explosion
import shapes.ScoreExplosion
import kotlin.math.cos
import kotlin.math.sin

object Effects {
    val collisions = MutableList(0) { Explosion() }
    val scoreExplosions = MutableList(0) { ScoreExplosion() }

    data class BurstParticle(var x: Float, var y: Float, var vx: Float, var vy: Float, var alpha: Float, val color: Int)
    private val burstParticles = mutableListOf<BurstParticle>()

    fun clearCollisionEffects() {
        for (collision in collisions) {
            if (collision.angle == Direction.FULL) {
                collision.implode()
            }
        }
        burstParticles.clear()
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
        if (Settings.scoreBurstEnabled) {
            val iter = burstParticles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.vy += Settings.screenRatio * 0.15f
                p.x += p.vx
                p.y += p.vy
                p.alpha -= 5f
                if (p.alpha <= 0f) { iter.remove(); continue }
                PaintBucket.scoreFlashPaint.color = p.color
                PaintBucket.scoreFlashPaint.alpha = p.alpha.toInt().coerceIn(0, 255)
                PaintBucket.scoreFlashPaint.style = android.graphics.Paint.Style.FILL
                canvas.drawCircle(p.x, p.y, Settings.screenRatio * 0.4f, PaintBucket.scoreFlashPaint)
            }
        }
    }


    fun addShieldedCollisionEffect(firstColor: Int, secondColor: Int, location: Point) {
        scoreExplosions.add(ScoreExplosion(firstColor, secondColor, location, Settings.screenRatio / 3f, true))
        scoreExplosions.add(ScoreExplosion(firstColor, secondColor, location, Settings.screenRatio / 3f, false))
    }

    fun addScoreEffect(firstColor: Int, secondColor: Int, location: Point, highGoal: Boolean) {
        scoreExplosions.add(ScoreExplosion(firstColor, secondColor, location, Settings.screenRatio / 3f, highGoal))
        if (Settings.scoreBurstEnabled) {
            val y = if (highGoal) Settings.topGoalBottom / 2f
                    else Settings.bottomGoalTop + (Settings.screenHeight - Settings.bottomGoalTop) / 2f
            val cx = Settings.screenWidth / 2f
            val color = firstColor
            repeat(40) {
                val angle = (Math.PI * 2.0 * it / 40).toFloat()
                val speed = Settings.screenRatio * (2f + (Math.random() * 3f).toFloat())
                burstParticles.add(BurstParticle(cx, y, cos(angle) * speed, sin(angle) * speed, 255f, color))
            }
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