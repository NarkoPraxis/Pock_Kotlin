package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import gameobjects.puckstyle.CachedShaderSkin
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import androidx.core.graphics.withTranslation

class MetalSkin(theme: ColorTheme) : CachedShaderSkin(theme) {

    private val grey = Color.rgb(140, 140, 150)
    private val lightGrey = Color.rgb(220, 220, 230)
    private val darkGrey = Color.rgb(70, 70, 80)
    private val accentTint = responsive

    private val edgePaint = Paint().apply { color = darkGrey; isAntiAlias = false; style = Paint.Style.STROKE }

    init {
        fill.isAntiAlias = false
    }

    override fun createShader(radius: Float): Shader =
        LinearGradient(0f, -radius, 0f, radius,
            intArrayOf(lightGrey, accentTint, grey, darkGrey),
            floatArrayOf(0f, 0.3f, 0.65f, 1f),
            Shader.TileMode.CLAMP)

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        ensureShader(renderer.radius)
        canvas.withTranslation(renderer.x, renderer.y) {
            drawCircle(0f, 0f, renderer.radius, fill)
        }
        edgePaint.strokeWidth = renderer.strokePaint.strokeWidth * 0.9f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, edgePaint)
        renderer.chargePaint.color = grey
    }
}
