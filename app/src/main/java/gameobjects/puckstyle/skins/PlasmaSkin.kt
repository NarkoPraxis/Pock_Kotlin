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
import androidx.core.graphics.withTranslation
import gameobjects.puckstyle.paddles.PlasmaLaunch
import physics.Point
import utility.Effects

class PlasmaSkin(override val renderer: PuckRenderer) : CachedShaderSkin(renderer) {

    private var lastColors = theme.main

    // Color.argb(180, 255, 255, 255) is constant — set once at init
    private val arc = Paint().apply {
        color = Color.argb(180, 255, 255, 255)
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Cached radius-derived values
    private var cachedRadius = -1f
    private var innerR = 0f   // renderer.radius * 0.5f
    private var outerR = 0f   // renderer.radius * 0.9f

    private fun ensureCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            innerR = renderer.radius * 0.5f
            outerR = renderer.radius * 0.9f
            arc.strokeWidth = renderer.strokePaint.strokeWidth * 0.5f
        }
    }

    override fun createShader(radius: Float): Shader =
        RadialGradient(0f, 0f, radius,
            intArrayOf(Color.WHITE, lastColors.primary, lastColors.secondary),
            floatArrayOf(0f, 0.4f, 1f),
            Shader.TileMode.CLAMP)

    override fun drawBody(canvas: Canvas) {
        val colors = responsiveGroup
        if (colors != lastColors) {
            lastColors = colors
            invalidateShader()
        }
        ensureCache()
        ensureShader(renderer.radius)
        canvas.withTranslation(renderer.x, renderer.y) {
            drawCircle(0f, 0f, renderer.radius, fill)
            repeat(3) {
                val a1 = Random.nextFloat() * TWO_PI
                val a2 = a1 + (Random.nextFloat() - 0.5f) * 2
                drawLine(
                    kotlin.math.cos(a1) * innerR,
                    kotlin.math.sin(a1) * innerR,
                    kotlin.math.cos(a2) * outerR,
                    kotlin.math.sin(a2) * outerR,
                    arc
                )
            }
        }
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        PlasmaLaunch.spawnLighting(renderer.x, renderer.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    override fun onShieldedCollision(position: Point) {
        PlasmaLaunch.spawnLighting(renderer.x, renderer.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    override val explosionFrequency get() = 20

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        PlasmaLaunch.spawnCelebration(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary, highGoal, fullCircle = false)
    }

    override fun onVictory(x: Float, y: Float) {
        PlasmaLaunch.spawnCelebration(x, y, renderer.radius, responsivePrimary, responsiveSecondary, highGoal = true, fullCircle = true)
    }

    companion object {
        private val TWO_PI = kotlin.math.PI.toFloat() * 2f
    }
}
