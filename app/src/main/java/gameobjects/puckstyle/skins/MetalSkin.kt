package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckSkin

class MetalSkin(override val theme: ColorTheme) : PuckSkin {

    private val grey = Color.rgb(140, 140, 150)
    private val lightGrey = Color.rgb(220, 220, 230)
    private val darkGrey = Color.rgb(70, 70, 80)
    private val accentTint = if (theme.isWarm) Color.rgb(200, 160, 160) else Color.rgb(160, 180, 210)

    private val fill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val edge = Paint().apply { color = darkGrey; isAntiAlias = false; style = Paint.Style.STROKE }

    private var lastRadius = -1f
    private fun ensureShader(radius: Float) {
        if (radius != lastRadius) {
            fill.shader = LinearGradient(0f, -radius, 0f, radius,
                intArrayOf(lightGrey, accentTint, grey, darkGrey),
                floatArrayOf(0f, 0.3f, 0.65f, 1f),
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
        edge.strokeWidth = puck.strokePaint.strokeWidth * 0.9f
        canvas.drawCircle(puck.x, puck.y, radius, edge)
        puck.chargePaint.color = grey
    }
}
