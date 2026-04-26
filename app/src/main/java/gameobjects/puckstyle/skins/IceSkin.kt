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

class IceSkin(theme: ColorTheme) : CachedShaderSkin(theme) {

    private var lastColors = theme.main

    private val rimStroke = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    override fun createShader(radius: Float): Shader {
        val midColor = Palette.lerpColor(lastColors.primary, Color.WHITE, 0.55f)
        return RadialGradient(0f, 0f, radius,
            intArrayOf(lastColors.primary, midColor, Color.WHITE),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP)
    }

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val colors = resolvedColors(renderer)
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
