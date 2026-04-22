package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import enums.Direction
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import physics.Point
import shapes.Explosion
import utility.Effects
import utility.PaintBucket
import kotlin.math.cos
import kotlin.math.sin

class ClassicSkin(override val theme: ColorTheme) : PuckSkin {

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, renderer.fillPaint)
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, renderer.strokePaint)
    }

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(ClassicScoreEffect(theme.primary, otherColor, position, Settings.screenRatio / 3f, highGoal))
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        val radiusModifier = 3f * (speed / Settings.maxPuckSpeed)
        Effects.addPersistentEffect(ClassicCollisionEffect(theme.primary, position, Settings.screenRatio * radiusModifier))
    }

    override fun onShieldedCollision(position: Point) {
        Effects.addPersistentEffect(ClassicCollisionEffect(theme.primary, position, Settings.screenRatio * 3f))
    }

    private class ClassicScoreEffect(
        scoringColor: Int,
        scoredColor: Int,
        private val position: Point,
        private val radius: Float,
        highGoal: Boolean
    ) : Effects.PersistentEffect {
        private val scoringPaint = Paint().apply {
            color = scoringColor; isAntiAlias = true; isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND; strokeWidth = 12f
        }
        private val scoredPaint = Paint().apply {
            color = scoredColor; isAntiAlias = true; isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND; strokeWidth = 12f
        }
        private val directions = mutableListOf<Point>()
        private val step = 10f
        private var currentDistance = 0f
        private var drawScored = true
        private var done = false
        override val isDone get() = done

        init {
            for (angle in listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, Math.PI)) {
                val a = angle.toFloat()
                if (highGoal) directions.add(Point(cos(a), sin(a)))
                else directions.add(-Point(cos(a), sin(a)))
            }
        }

        override fun step() {}

        override fun draw(canvas: Canvas) {
            if (done) return
            for (direction in directions) {
                val dx = position.x + direction.x * currentDistance
                val dy = position.y + direction.y * currentDistance
                canvas.drawCircle(dx, dy, radius, scoringPaint)
                if (drawScored) canvas.drawCircle(dx, dy, radius, scoredPaint)
            }
            if (drawScored) {
                currentDistance += step
                scoredPaint.alpha = scoredPaint.alpha - step.toInt()
                if (scoredPaint.alpha < step) drawScored = false
            } else {
                currentDistance += step / 2
                scoringPaint.alpha = scoringPaint.alpha - step.toInt()
                if (scoringPaint.alpha < step) done = true
            }
        }
    }

    private class ClassicCollisionEffect(
        color: Int, position: Point, radius: Float
    ) : Effects.PersistentEffect {
        private val explosion = Explosion(
            PaintBucket.effectColor, color, PaintBucket.backgroundColor,
            position, radius, true, Direction.FULL, 100
        )
        override val isDone get() = explosion.finished
        override fun step() {}
        override fun draw(canvas: Canvas) { explosion.drawTo(canvas) }
    }
}
