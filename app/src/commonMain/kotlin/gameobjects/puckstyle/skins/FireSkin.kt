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

    // renderer.radius is effectively immutable after setup; cache radius-derived sizes
    // behind a cachedRadius guard so we don't recompute radius*const every frame.
    private var cachedRadius = -1f
    private var innerCoreSize = 0f

    val INNER_CORE_SIZE: Float
        get() {
            if (cachedRadius != renderer.radius) ensureCache()
            return innerCoreSize
        }

    private fun ensureCache() {
        cachedRadius = renderer.radius
        innerCoreSize = renderer.radius * .6f
    }

    override fun DrawScope.drawBody() {
        if (cachedRadius != renderer.radius) ensureCache()
        val colors = responsiveGroup
        val center = Offset(renderer.x, renderer.y)
        drawCircle(Color(colors.secondary), renderer.radius, center)
        drawCircle(Color(colors.primary), innerCoreSize, center)
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
