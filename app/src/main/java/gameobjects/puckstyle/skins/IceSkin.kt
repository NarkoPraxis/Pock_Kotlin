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

class IceSkin(theme: ColorTheme) : CachedShaderSkin(theme) {

    private val centerColor = theme.primary
    private val midColor = Palette.lerpColor(theme.primary, Color.WHITE, 0.55f)
    private val edgeColor = Color.WHITE

    private val rimStroke = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    override fun createShader(radius: Float): Shader =
        RadialGradient(0f, 0f, radius,
            intArrayOf(centerColor, midColor, edgeColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP)

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        ensureShader(renderer.radius)
        canvas.save()
        canvas.translate(renderer.x, renderer.y)
        canvas.drawCircle(0f, 0f, renderer.radius, fill)
        canvas.restore()
        rimStroke.strokeWidth = renderer.strokePaint.strokeWidth * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, rimStroke)
    }
}
