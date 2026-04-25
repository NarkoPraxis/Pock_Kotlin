package gameobjects.puckstyle.tails

import android.graphics.Canvas
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import shapes.DrawablePoint

class RainbowTail(override val theme: ColorTheme) : TailRenderer {
    private var points: MutableList<DrawablePoint>? = null
    private val hueOffset = Palette.themeHue(theme)

    override val zIndex: Int
        get() = 1

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        val rainbowLen = (20 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        if (points == null || points!!.size != rainbowLen) points = MutableList(rainbowLen) { DrawablePoint(renderer.x, renderer.y) }
        val points = points!!
        val colors = resolvedColors(renderer)
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) points[i] = points[i - 1]
            else points[i] = DrawablePoint(renderer.x, renderer.y, renderer.strokeColor)
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            val color = when {
                renderer.isInert -> colors.primary
                renderer.shielded || renderer.currentCharge >= Settings.sweetSpotMin -> theme.effect.primary
                else -> Palette.hsvThemed(renderer.frame * 4f + hueOffset - i * 15f)
            }
            points[i].setColor(color)
            points[i].size = renderer.radius * 1.1f - Settings.strokeWidth - renderer.radius * ((i - 1).coerceAtLeast(0).toFloat() / (points.size - 1))
            points[i].setAlpha((255f * (1 - ratio)).toInt())
            points[i].drawTo(canvas)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
