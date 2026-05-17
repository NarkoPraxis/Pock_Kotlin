package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import gameobjects.puckstyle.CachedBrushSkin
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import kotlin.random.Random
import gameobjects.puckstyle.paddles.PlasmaLaunch
import physics.Point
import utility.Effects
import utility.PaintBucket

class PlasmaSkin(override val renderer: PuckRenderer) : CachedBrushSkin(renderer) {

    private var lastColors = theme.main

    private val arcColor = Color(Palette.withAlpha(Palette.WHITE, 180))

    // Cached radius-derived values
    private var cachedRadius = -1f
    private var innerR = 0f
    private var outerR = 0f
    private var arcStrokeWidth = 0f

    private fun ensureCache() {
        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            innerR = renderer.radius * 0.5f
            outerR = renderer.radius * 0.9f
            arcStrokeWidth = renderer.strokeWidth * 0.5f
        }
    }

    override fun buildBrush(radius: Float): Brush =
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to PaintBucket.white,
                0.4f to Color(lastColors.primary),
                1f to Color(lastColors.secondary)
            ),
            center = Offset.Zero,
            radius = radius
        )

    override fun DrawScope.drawBody() {
        val colors = responsiveGroup
        if (colors != lastColors) {
            lastColors = colors
            invalidateBrush()
        }
        ensureCache()
        ensureBrush(renderer.radius)

        withTransform({ translate(renderer.x, renderer.y) }) {
            drawCircle(brush = cachedBrush!!, radius = renderer.radius, center = Offset.Zero)
            repeat(3) {
                val a1 = Random.nextFloat() * TWO_PI
                val a2 = a1 + (Random.nextFloat() - 0.5f) * 2
                drawLine(
                    arcColor,
                    Offset(kotlin.math.cos(a1) * innerR, kotlin.math.sin(a1) * innerR),
                    Offset(kotlin.math.cos(a2) * outerR, kotlin.math.sin(a2) * outerR),
                    strokeWidth = arcStrokeWidth,
                    cap = StrokeCap.Round
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

    override val explosionFrequency get() = 10
    override val scatterDensity get() = 1.2f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        PlasmaLaunch.spawnCelebration(position.x, position.y, renderer.radius, theme.main.primary, theme.main.secondary, highGoal, fullCircle = false)
    }

    override fun onVictory(x: Float, y: Float) {
        PlasmaLaunch.spawnCelebration(x, y, renderer.radius, theme.main.primary, theme.main.secondary, highGoal = true, fullCircle = true)
    }

    companion object {
        private val TWO_PI = kotlin.math.PI.toFloat() * 2f
    }
}
