package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.graphics.withTranslation
import gameobjects.puckstyle.paddles.GalaxyLaunch
import physics.Point

class GalaxySkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val rim = Paint().apply { color = theme.main.primary; isAntiAlias = true; style = Paint.Style.FILL }
    private val star = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val starPath = Path()
    private var lastRadius = -1f
    private var lastColors = theme.main

    override fun onCollisionWin(position: Point, speed: Float) {
        GalaxyLaunch.spawnStartImpact(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    override fun onShieldedCollision(position: Point) {
        GalaxyLaunch.spawnStartImpact(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    // FloatArray: [angle, radiusFraction, alphaFraction, sizeFraction] — pre-seeded edge noise blobs
    private val edgeDots: List<FloatArray> = List(28) {
        floatArrayOf(
            Random.nextFloat() * Math.PI.toFloat() * 2f,
            0.62f + Random.nextFloat() * 0.28f,
            0.25f + Random.nextFloat() * 0.55f,
            0.055f + Random.nextFloat() * 0.09f
        )
    }

    // FloatArray: [angularPos, distSeed, twinklePhase, angularDrift, twinkleSpeed]
    private val starSeeds: List<FloatArray> = List(24) {
        floatArrayOf(
            Random.nextFloat() * Math.PI.toFloat() * 2f,
            Random.nextFloat(),
            Random.nextFloat() * Math.PI.toFloat() * 2f,
            (Random.nextFloat() - 0.5f) * 0.048f,
            0.157f + Random.nextFloat() * 0.157f
        )
    }

    private fun ensureShader(radius: Float) {
        if (radius == lastRadius) return
        val darkCenter = Color.argb(255, 0, 0, 0)
        val preThemeEdge  = Palette.withAlpha(lastColors.primary, 130)
        val themeEdge = Palette.withAlpha(lastColors.primary, 60)
        val gone = Palette.withAlpha(lastColors.primary, 0)
        fill.shader = RadialGradient(
            0f, 0f, radius,
            intArrayOf(darkCenter, Color.argb(160, 0,0,0), preThemeEdge, themeEdge, themeEdge),
            floatArrayOf(0f, 0.5f, 0.7f, 0.9f, 1f),
            Shader.TileMode.CLAMP
        )
        lastRadius = radius
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, outerR: Float, innerR: Float, paint: Paint) {
        starPath.reset()
        for (i in 0 until 8) {
            val angle = (i * 45f - 90f) * Math.PI.toFloat() / 180f
            val r = if (i % 2 == 0) outerR else innerR
            val px = cx + cos(angle) * r
            val py = cy + sin(angle) * r
            if (i == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
        }
        starPath.close()
        canvas.drawPath(starPath, paint)
    }

    override fun drawBody(canvas: Canvas) {
        val colors = resolvedColors()
        if (colors != lastColors) {
            lastColors = colors
            lastRadius = -1f
        }
        ensureShader(renderer.radius)

//        rim.strokeWidth = renderer.strokePaint.strokeWidth * 0.8f
//        canvas.drawCircle(renderer.x, renderer.y, renderer.radius , rim)

        canvas.withTranslation(renderer.x, renderer.y) {
            drawCircle(0f, 0f, renderer.radius * 0.85f, fill)
        }

        for (seed in starSeeds) {
            seed[0] += seed[3]
            val ang = seed[0]
            val dist = (seed[1] * 0.72f + 0.05f) * renderer.radius
            val px = renderer.x + cos(ang) * dist
            val py = renderer.y + sin(ang) * dist
            val twinkle = (sin(renderer.frame * seed[4] + seed[2]) + 1f) * 0.5f
            star.alpha = (110 + 145 * twinkle).toInt()
            star.color = Palette.withAlpha(lastColors.primary, star.alpha)
            val outerR = renderer.radius * 0.12f * (0.6f + twinkle * 0.8f)
            drawStar(canvas, px, py, outerR, outerR * 0.38f, star)
        }
    }
}
