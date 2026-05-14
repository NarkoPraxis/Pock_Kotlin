package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects

class ClassicLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(ClassicResidual(rx, ry, renderer.radius, theme.main.primary))
    }

    private class ClassicResidual(
        private val cx: Float, private val cy: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(scope: DrawScope) {
            val t = (frame.toFloat() / 30f).coerceIn(0f, 1f)
            val r = radius * (1f + t)
            val alpha = (255f - 255f * (t - 0.2f)).toInt().coerceIn(0, 255)
            scope.drawCircle(
                color = Color(Palette.withAlpha(color, alpha)),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = Settings.strokeWidth * 2f)
            )
        }
    }
}
