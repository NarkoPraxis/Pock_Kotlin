package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.BallSize
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.FireLaunch
import physics.Point

class FireSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

    override val zIndex = 0

    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        strokeWidth = renderer.strokePaint.strokeWidth
    }

    override fun drawBody(canvas: Canvas) {
        val colors = resolvedColors()

        fillPaint.color = colors.secondary
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, fillPaint)

        fillPaint.color = colors.primary
        canvas.drawCircle(renderer.x, renderer.y, renderer.r(BallSize.P060), fillPaint)
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        FireLaunch.spawnFireImpact(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary, theme.inert.primary)
    }

    override fun onShieldedCollision(position: Point) {
        FireLaunch.spawnFireImpact(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary, theme.inert.secondary)
    }

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        FireLaunch.spawnFireCelebration(position.x, position.y, renderer.radius, responsiveSecondary, highGoal, fullCircle = false)
    }
}
