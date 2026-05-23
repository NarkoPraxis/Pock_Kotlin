package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import enums.Direction
import gameobjects.Settings
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import physics.Point
import shapes.Explosion
import utility.Effects
import utility.PaintBucket
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ClassicSkin(override val renderer: PuckRenderer) : PuckSkin {

    override fun DrawScope.drawBody() {
        val center = Offset(renderer.x, renderer.y)
        drawCircle(Color(responsivePrimary), renderer.radius, center)
        drawCircle(
            color = Color(responsiveSecondary),
            radius = renderer.radius,
            center = center,
            style = Stroke(width = renderer.strokeWidth)
        )
    }

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(ClassicScoreEffect(theme.main.primary, otherColor, position, Settings.screenRatio / 3f, highGoal))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(ClassicScoreEffect(theme.main.primary, theme.main.secondary, Point(x, y), Settings.screenRatio / 3f, highGoal = true, fullCircle = true))
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        val radiusModifier = 3f * (speed / Settings.maxPuckSpeed)
        Effects.addPersistentEffect(ClassicCollisionEffect(theme.main.primary, position, Settings.screenRatio * radiusModifier))
    }

    override fun onShieldedCollision(position: Point) {
        Effects.addPersistentEffect(ClassicCollisionEffect(theme.main.primary, position, Settings.screenRatio * 3f))
    }

    private class ClassicScoreEffect(
        private val scoringColor: Int,
        private val scoredColor: Int,
        private val position: Point,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean = false
    ) : Effects.PersistentEffect {
        private val directions = mutableListOf<Point>()
        private val step = 10f
        private var currentDistance = 0f
        private var drawScored = true
        private var done = false
        private var scoringAlpha = 255
        private var scoredAlpha = 255
        override val isDone get() = done

        init {
            if (fullCircle) {
                for (i in 0 until 12) {
                    val a = (i * 2.0 * PI / 12).toFloat()
                    directions.add(Point(cos(a), sin(a)))
                }
            } else {
                for (angle in listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, PI)) {
                    val a = angle.toFloat()
                    if (highGoal) directions.add(Point(cos(a), sin(a)))
                    else directions.add(-Point(cos(a), sin(a)))
                }
            }
        }

        override fun step() {}

        override fun draw(scope: DrawScope) {
            if (done) return
            for (direction in directions) {
                val dx = position.x + direction.x * currentDistance
                val dy = position.y + direction.y * currentDistance
                scope.drawCircle(Color(scoringColor).copy(alpha = scoringAlpha / 255f), radius, Offset(dx, dy))
                if (drawScored) scope.drawCircle(Color(scoredColor).copy(alpha = scoredAlpha / 255f), radius, Offset(dx, dy))
            }
            if (drawScored) {
                currentDistance += step
                scoredAlpha -= step.toInt()
                if (scoredAlpha < step) drawScored = false
            } else {
                currentDistance += step / 2
                scoringAlpha -= step.toInt()
                if (scoringAlpha < step) done = true
            }
        }
    }

    private class ClassicCollisionEffect(
        color: Int, position: Point, radius: Float
    ) : Effects.PersistentEffect {
        private val explosion = Explosion(
            PaintBucket.effectColor.toArgb(), color, color,
            position, radius, true, Direction.FULL, 100
        )
        override val isDone get() = explosion.finished
        override fun step() {}
        override fun draw(scope: DrawScope) {
            with(explosion) { scope.drawTo() }
        }
    }
}
