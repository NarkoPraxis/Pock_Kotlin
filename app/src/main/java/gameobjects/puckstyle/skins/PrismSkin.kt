package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import androidx.core.graphics.withTranslation
import gameobjects.puckstyle.Palette.hsv
import gameobjects.puckstyle.paddles.PrismLaunch
import physics.Point

class PrismSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

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

    override fun drawBody(canvas: Canvas) {
        val sides = 6
        val angleOffset = renderer.frame * 0.8f
        val osc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        canvas.withTranslation(renderer.x, renderer.y) {
            rotate(angleOffset)

            for (i in 0 until sides) {
                val a1 = (i * 360f / sides) * Math.PI.toFloat() / 180f
                val a2 = ((i + 1) * 360f / sides) * Math.PI.toFloat() / 180f
                path.reset()
                path.moveTo(0f, 0f)
                path.lineTo(
                    kotlin.math.cos(a1) * renderer.radius,
                    kotlin.math.sin(a1) * renderer.radius
                )
                path.lineTo(
                    kotlin.math.cos(a2) * renderer.radius,
                    kotlin.math.sin(a2) * renderer.radius
                )
                path.close()
                facet.color = Palette.hsvThemed(hues[i % hues.size] + osc)

                if (renderer.shielded) {
                    val purpleCenter = if (baseHue > 180f) 290f else 270f
                    val purpleHue = purpleCenter + osc * 0.5f + (i - 2.5f) * 6f
                    facet.color = Palette.hsvThemed(purpleHue)
                } else if (renderer.isInert) {
                    facet.color = Palette.withAlpha(hsv( hues[i % hues.size], .10f, .90f), 255)
                }
                drawPath(path, facet)
            }
            edge.strokeWidth = renderer.strokePaint.strokeWidth
            edge.color = Palette.hsvHighlight(baseHue - osc)

            if (renderer.isInert) {
                edge.color = theme.inert.secondary
            }
            if (renderer.shielded) {
                edge.color = theme.shield.secondary
            }
            path.reset()
            for (i in 0 until sides) {
                val a = (i * 360f / sides) * Math.PI.toFloat() / 180f
                val px = kotlin.math.cos(a) * renderer.radius
                val py = kotlin.math.sin(a) * renderer.radius
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, edge)
        }
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        val spawnRotDeg = renderer.frame * 0.8f
        val spawnOsc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        val baseHue = Palette.themeHue(theme)
        PrismLaunch.scatterTriangles(renderer.x, renderer.y, renderer.radius, spawnRotDeg, spawnOsc, baseHue)
    }

    override fun onShieldedCollision(position: Point) {
        val spawnRotDeg = renderer.frame * 0.8f
        val spawnOsc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        val baseHue = Palette.themeHue(theme)
        PrismLaunch.scatterTriangles(renderer.x, renderer.y, renderer.radius, spawnRotDeg, spawnOsc, baseHue)

    }
}
