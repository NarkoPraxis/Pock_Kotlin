package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.puckstyle.CachedShaderSkin
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import androidx.core.graphics.withTranslation
import gameobjects.puckstyle.paddles.IceLaunch
import physics.Point

class IceSkin(theme: ColorTheme, override val renderer: PuckRenderer) : CachedShaderSkin(theme, renderer) {

    private var lastColors = theme.main

    private val rimStroke = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        //Todo: Ice Score Effect
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        IceLaunch.spawnImpact(position.x, position.y, renderer.radius * .4f, theme)
    }

    override fun onShieldedCollision(position: Point) {
        IceLaunch.spawnImpact(position.x, position.y, renderer.radius * .6f, theme)
    }

    override fun createShader(radius: Float): Shader {
        val midColor = Palette.lerpColor(lastColors.primary, Color.WHITE, 0.55f)
        return RadialGradient(0f, 0f, radius,
            intArrayOf(lastColors.primary, midColor, Color.WHITE),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP)
    }

    override fun drawBody(canvas: Canvas) {
        val colors = resolvedColors()
        if (colors != lastColors) {
            lastColors = colors
            invalidateShader()
        }
        ensureShader(renderer.radius)
        canvas.withTranslation(renderer.x, renderer.y) {
            drawCircle(0f, 0f, renderer.radius, fill)
        }
        rimStroke.strokeWidth = renderer.strokePaint.strokeWidth * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, rimStroke)
    }
}
