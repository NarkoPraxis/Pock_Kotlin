package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.puckstyle.CachedShaderSkin
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import kotlin.random.Random

class PlasmaSkin(theme: ColorTheme) : CachedShaderSkin(theme) {

    private val core = Color.WHITE
    private val mid = if (theme.isWarm) Color.rgb(255, 170, 60) else Color.rgb(120, 220, 255)
    private val edge = if (theme.isWarm) Color.rgb(255, 60, 40) else Color.rgb(40, 100, 255)

    private val arc = Paint().apply { color = core; isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun createShader(radius: Float): Shader =
        RadialGradient(0f, 0f, radius,
            intArrayOf(core, mid, edge),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP)

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        ensureShader(renderer.radius)
        canvas.save()
        canvas.translate(renderer.x, renderer.y)
        canvas.drawCircle(0f, 0f, renderer.radius, fill)
        arc.strokeWidth = renderer.strokePaint.strokeWidth * 0.5f
        repeat(3) {
            val a1 = Random.nextFloat() * Math.PI.toFloat() * 2
            val a2 = a1 + (Random.nextFloat() - 0.5f) * 2
            arc.color = Color.argb(180, 255, 255, 255)
            canvas.drawLine(
                kotlin.math.cos(a1) * renderer.radius * 0.5f,
                kotlin.math.sin(a1) * renderer.radius * 0.5f,
                kotlin.math.cos(a2) * renderer.radius * 0.9f,
                kotlin.math.sin(a2) * renderer.radius * 0.9f,
                arc
            )
        }
        canvas.restore()
    }
}
