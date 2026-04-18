package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class PrismSkin(override val theme: ColorTheme) : PuckSkin {

    private val facet = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val edge = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val path = Path()

    private val baseHue = Palette.themeHue(theme)
    private val hues = floatArrayOf(
        baseHue,
        baseHue + 40f,
        baseHue - 30f,
        baseHue + 20f,
        baseHue + 60f,
        baseHue - 15f
    )

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val sides = 6
        val angleOffset = renderer.frame * 0.8f
        val osc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        canvas.save()
        canvas.translate(renderer.x, renderer.y)
        canvas.rotate(angleOffset)

        for (i in 0 until sides) {
            val a1 = (i * 360f / sides) * Math.PI.toFloat() / 180f
            val a2 = ((i + 1) * 360f / sides) * Math.PI.toFloat() / 180f
            path.reset()
            path.moveTo(0f, 0f)
            path.lineTo(kotlin.math.cos(a1) * renderer.radius, kotlin.math.sin(a1) * renderer.radius)
            path.lineTo(kotlin.math.cos(a2) * renderer.radius, kotlin.math.sin(a2) * renderer.radius)
            path.close()
            facet.color = Palette.hsvThemed(hues[i % hues.size] + osc)
            canvas.drawPath(path, facet)
        }
        edge.strokeWidth = renderer.strokePaint.strokeWidth * 0.6f
        edge.color = Palette.hsvHighlight(baseHue - osc)

        path.reset()
        for (i in 0 until sides) {
            val a = (i * 360f / sides) * Math.PI.toFloat() / 180f
            val px = kotlin.math.cos(a) * renderer.radius
            val py = kotlin.math.sin(a) * renderer.radius
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, edge)
        canvas.restore()
    }
}
