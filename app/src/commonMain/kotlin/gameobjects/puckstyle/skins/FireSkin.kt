package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.FireLaunch
import physics.Point

class FireSkin(override val renderer: PuckRenderer) : PuckSkin {

    override val zIndex = 0

    val INNER_CORE_SIZE get() = renderer.radius * .6f

    override fun DrawScope.drawBody() {
        val colors = responsiveGroup
        val center = Offset(renderer.x, renderer.y)
        drawCircle(Color(colors.secondary), renderer.radius, center)
        drawCircle(Color(colors.primary), INNER_CORE_SIZE, center)
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        FireLaunch.spawnFireImpact(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary, theme.inert.primary)
    }

    override fun onShieldedCollision(position: Point) {
        FireLaunch.spawnFireImpact(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary, theme.inert.secondary)
    }

    override val explosionFrequency get() = 20
    override val scatterDensity get() = 0.8f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        FireLaunch.spawnFireCelebration(position.x, position.y, renderer.radius, renderer.bakedSecondary(theme.main.secondary), highGoal, fullCircle = false)
    }

    override fun onVictory(x: Float, y: Float) {
        FireLaunch.spawnFireCelebration(x, y, renderer.radius, renderer.bakedSecondary(theme.main.secondary), highGoal = true, fullCircle = true)
    }
}
