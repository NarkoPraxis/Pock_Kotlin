package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckSkin

class FireSkin(override val theme: ColorTheme) : PuckSkin {

    private val centerColor = if (theme.isWarm) Color.rgb(255, 240, 180) else Color.rgb(220, 200, 255)
    private val midColor = if (theme.isWarm) Color.rgb(255, 140, 40) else Color.rgb(160, 80, 230)
    private val edgeColor = if (theme.isWarm) Color.rgb(180, 30, 20) else Color.rgb(70, 20, 140)

    private val fill = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private var lastRadius = -1f
    private fun ensureShader(x: Float, y: Float, radius: Float) {
        if (radius != lastRadius) {
            fill.shader = RadialGradient(0f, 0f, radius,
                intArrayOf(centerColor, midColor, edgeColor),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP)
            lastRadius = radius
        }
    }

    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        ensureShader(puck.x, puck.y, radius)
        canvas.save()
        canvas.translate(puck.x, puck.y)
        canvas.drawCircle(0f, 0f, radius, fill)
        canvas.restore()
    }
}
