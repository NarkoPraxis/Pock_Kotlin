package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.puckstyle.CachedShaderSkin
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer

class IceSkin(theme: ColorTheme) : CachedShaderSkin(theme) {

    private val centerColor = if (theme.isWarm) Color.rgb(255, 230, 235) else Color.rgb(220, 255, 250)
    private val midColor = if (theme.isWarm) Color.rgb(255, 180, 160) else Color.rgb(120, 220, 200)
    private val edgeColor = if (theme.isWarm) Color.rgb(230, 120, 140) else Color.rgb(60, 160, 140)

    private val rimStroke = Paint().apply {
        color = if (theme.isWarm) Color.rgb(255, 220, 220) else Color.rgb(210, 255, 255)
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
