package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckSkin

class IceSkin(override val theme: ColorTheme) : PuckSkin {

    private val centerColor = if (theme.isWarm) Color.rgb(255, 230, 235) else Color.rgb(220, 255, 250)
    private val midColor = if (theme.isWarm) Color.rgb(255, 180, 160) else Color.rgb(120, 220, 200)
    private val edgeColor = if (theme.isWarm) Color.rgb(230, 120, 140) else Color.rgb(60, 160, 140)

    private val fill = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val rimStroke = Paint().apply {
        color = if (theme.isWarm) Color.rgb(255, 220, 220) else Color.rgb(210, 255, 255)
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private var lastRadius = -1f
    private fun ensureShader(radius: Float) {
        if (radius != lastRadius) {
            fill.shader = RadialGradient(0f, 0f, radius,
                intArrayOf(centerColor, midColor, edgeColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP)
            lastRadius = radius
        }
    }

    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        ensureShader(radius)
        canvas.save()
        canvas.translate(puck.x, puck.y)
        canvas.drawCircle(0f, 0f, radius, fill)
        canvas.restore()
        rimStroke.strokeWidth = puck.strokePaint.strokeWidth * 0.7f
        canvas.drawCircle(puck.x, puck.y, radius, rimStroke)
    }
}
