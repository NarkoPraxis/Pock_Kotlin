package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.FireLaunch
import physics.Point

class FireSkin( override val renderer: PuckRenderer) : PuckSkin {

    override val zIndex = 0

    val INNER_CORE_SIZE get() = renderer.radius * .6f

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    override fun drawBody(canvas: Canvas) {
        val colors = responsiveGroup

        fillPaint.color = colors.secondary
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, fillPaint)

        fillPaint.color = colors.primary
        canvas.drawCircle(renderer.x, renderer.y, INNER_CORE_SIZE, fillPaint)
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        FireLaunch.spawnFireImpact(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary, theme.inert.primary)
    }

    override fun onShieldedCollision(position: Point) {
        FireLaunch.spawnFireImpact(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary, theme.inert.secondary)
    }

    override val explosionFrequency get() = 20
    override val scatterDensity get() = 0.8f

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        FireLaunch.spawnFireCelebration(position.x, position.y, renderer.radius, responsiveSecondary, highGoal, fullCircle = false)
    }

    override fun onVictory(x: Float, y: Float) {
        FireLaunch.spawnFireCelebration(x, y, renderer.radius, responsiveSecondary, highGoal = true, fullCircle = true)
    }
}
