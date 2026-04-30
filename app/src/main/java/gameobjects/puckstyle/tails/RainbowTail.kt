package gameobjects.puckstyle.tails

import android.graphics.Canvas
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.Palette.hsv
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import shapes.DrawablePoint

class RainbowTail(override val theme: ColorTheme, override val renderer: PuckRenderer) : TailRenderer {
    private var points: MutableList<DrawablePoint>? = null
    private val hueOffset = Palette.themeHue(theme)
    private val shieldHue = Palette.colorHue(theme.shield.primary)

    // Cached at init — tailLengthMultiplier and strokeWidth never change after setup.
    private val rainbowLen: Int = (20 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
    private val sizeMinusOne: Int = (rainbowLen - 1).coerceAtLeast(1)
    private val sizeMinusOneF: Float = sizeMinusOne.toFloat()

    // Radius-derived cache.
    private var cachedRadius = -1f
    private var baseSize = 0f   // renderer.radius * 1.1f - Settings.strokeWidth

    override val zIndex: Int
        get() = 1

    private fun ensureCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            baseSize = renderer.radius * 1.1f - Settings.strokeWidth
        }
    }

    override fun render(canvas: Canvas) {
        ensureCache()
        if (points == null || points!!.size != rainbowLen) {
            points = MutableList(rainbowLen) { DrawablePoint(renderer.x, renderer.y) }
        }
        val pts = points!!

        // Compute per-frame constants once outside the loop.
        val isInert = renderer.isInert
        val isShielded = renderer.shielded
        // sin oscillation for shielded branch — same value for every particle in a frame.
        val shieldOsc = if (isShielded) kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f else 0f
        val frameHue = renderer.frame * 4f + hueOffset

        for (i in rainbowLen - 1 downTo 0) {
            if (i - 1 >= 0) pts[i] = pts[i - 1]
            else pts[i] = DrawablePoint(renderer.x, renderer.y, renderer.strokeColor)
            val ratio = i.toFloat() / sizeMinusOneF
            val cycleHue = frameHue - i * 15f
            val color = when {
                isInert   -> hsv(cycleHue, 0.10f, 0.90f)
                isShielded -> Palette.hsvThemed(shieldHue + shieldOsc - i * 15f)
                else       -> Palette.hsvThemed(cycleHue)
            }
            pts[i].setColor(color)
            pts[i].size = baseSize - renderer.radius * ((i - 1).coerceAtLeast(0).toFloat() / sizeMinusOneF)
            pts[i].setAlpha((255f * (1 - ratio)).toInt())
            pts[i].drawTo(canvas)
        }
    }

    override fun clear() { points = null }

    override fun fillTo(x: Float, y: Float) {
        points?.forEach { it.x = x; it.y = y }
    }
}
