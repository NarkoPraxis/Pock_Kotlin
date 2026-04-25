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

    private var lastColors = theme.main

    private val arc = Paint().apply { color = Color.WHITE; isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun createShader(radius: Float): Shader =
        RadialGradient(0f, 0f, radius,
            intArrayOf(Color.WHITE, lastColors.primary, lastColors.secondary),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP)

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val colors = resolvedColors(renderer)
        if (colors != lastColors) {
            lastColors = colors
            invalidateShader()
        }
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
