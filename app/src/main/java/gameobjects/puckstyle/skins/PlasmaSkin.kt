package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckSkin
import kotlin.random.Random

class PlasmaSkin(override val theme: ColorTheme) : PuckSkin {

    private val core = Color.WHITE
    private val mid = if (theme.isWarm) Color.rgb(255, 170, 60) else Color.rgb(120, 220, 255)
    private val edge = if (theme.isWarm) Color.rgb(255, 60, 40) else Color.rgb(40, 100, 255)

    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val arc = Paint().apply { color = core; isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    private var lastRadius = -1f
    private fun ensureShader(radius: Float) {
        if (radius != lastRadius) {
            fill.shader = RadialGradient(0f, 0f, radius,
                intArrayOf(core, mid, edge),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP)
            lastRadius = radius
        }
    }

    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        ensureShader(radius)
        canvas.save()
        canvas.translate(puck.x, puck.y)
        canvas.drawCircle(0f, 0f, radius, fill)
        arc.strokeWidth = puck.strokePaint.strokeWidth * 0.5f
        repeat(3) {
            val a1 = Random.nextFloat() * Math.PI.toFloat() * 2
            val a2 = a1 + (Random.nextFloat() - 0.5f) * 2
            arc.color = Color.argb(180, 255, 255, 255)
            canvas.drawLine(
                kotlin.math.cos(a1) * radius * 0.5f,
                kotlin.math.sin(a1) * radius * 0.5f,
                kotlin.math.cos(a2) * radius * 0.9f,
                kotlin.math.sin(a2) * radius * 0.9f,
                arc
            )
        }
        canvas.restore()
    }
}
