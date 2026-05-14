package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.Palette.hsv
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.RainbowLaunch
import physics.Point
import utility.Effects

class RainbowSkin(override val renderer: PuckRenderer) : PuckSkin {

    private val hueOffset = Palette.themeHue(theme)
    private val shieldHue = Palette.colorHue(theme.shield.primary)

    override fun DrawScope.drawBody() {
        val baseHue = renderer.frame * 4f + hueOffset
        val osc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f

        val fillColorInt = when {
            renderer.isInert -> hsv(baseHue, 0.10f, 0.90f)
            renderer.shielded -> Palette.hsvThemed(shieldHue + osc)
            else -> Palette.hsvThemed(baseHue)
        }
        val strokeColorInt = when {
            renderer.isInert -> theme.inert.secondary
            renderer.shielded -> theme.shield.secondary
            else -> theme.main.primary
        }

        val center = Offset(renderer.x, renderer.y)
        drawCircle(Color(fillColorInt), renderer.radius, center)
        drawCircle(
            Color(strokeColorInt),
            renderer.radius,
            center,
            style = Stroke(width = renderer.strokeWidth)
        )
        renderer.chargeColor = fillColorInt
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        RainbowLaunch.spawnRainbow(renderer.x, renderer.y, renderer.radius)
    }

    override fun onShieldedCollision(position: Point) {
        RainbowLaunch.spawnRainbow(renderer.x, renderer.y, renderer.radius)
    }

    override val explosionFrequency get() = 20

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(RainbowLaunch.spawnCelebration(position.x, position.y, renderer.radius))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(RainbowLaunch.spawnCelebration(x, y, renderer.radius))
    }
}
