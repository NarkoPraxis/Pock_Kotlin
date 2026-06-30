package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
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

    // Reusable arc-angle storage (a1, a2) per arc, refilled in place each frame.
    private val arcA1 = FloatArray(3)
    private val arcA2 = FloatArray(3)
    // Static-UI cache: only recompute the crackle when the strobe clock advances,
    // so repeated repaints of the same frozen frame reuse the same angles.
    private var cachedStrobe = Int.MIN_VALUE

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

        // Compute the three crackle arcs into the hoisted angle arrays.
        if (renderer.staticUiMode) {
            // Static UI: reseed off the strobe clock so the arcs keep crackling in place (the
            // circle geometry is frozen); reading strobe also re-invalidates the static canvas
            // so it keeps repainting. Advance every strobe frame (no /2) and spread the seed with
            // a multiplicative hash so consecutive frames/arcs decorrelate — otherwise sequential
            // seeds produce near-identical "sprinkle" arcs instead of fast crackling plasma lines.
            // Only recompute (and only then allocate Random) when the strobe clock actually advances;
            // repeated repaints of the same frozen frame reuse the cached angles.
            if (renderer.strobe != cachedStrobe) {
                cachedStrobe = renderer.strobe
                for (i in 0 until 3) {
                    val rnd = Random(renderer.strobe.toLong() * 0x9E3779B97F4A7C15uL.toLong() + (i + 1) * 0x2545F4914F6CDD1DuL.toLong())
                    val a1 = rnd.nextFloat() * TWO_PI
                    arcA1[i] = a1
                    arcA2[i] = a1 + (rnd.nextFloat() - 0.5f) * 2
                }
            }
        } else {
            // Live play uses global randomness (the Random singleton allocates nothing).
            for (i in 0 until 3) {
                val a1 = Random.nextFloat() * TWO_PI
                arcA1[i] = a1
                arcA2[i] = a1 + (Random.nextFloat() - 0.5f) * 2
            }
        }

        translate(renderer.x, renderer.y) {
            drawCircle(brush = cachedBrush!!, radius = renderer.radius, center = Offset.Zero)
            for (i in 0 until 3) {
                val a1 = arcA1[i]
                val a2 = arcA2[i]
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
        // Burst spawns at the ball's pop position (mid-zone), not on the goal edge, so crackle a full
        // circle instead of an inward half-arc.
        PlasmaLaunch.spawnCelebration(position.x, position.y, renderer.radius * 2f, renderer.bakedPrimary(theme.main.primary), renderer.bakedSecondary(theme.main.secondary), highGoal, fullCircle = true)
    }

    override fun onVictory(x: Float, y: Float) {
        PlasmaLaunch.spawnCelebration(x, y, renderer.radius, renderer.bakedPrimary(theme.main.primary), renderer.bakedSecondary(theme.main.secondary), highGoal = true, fullCircle = true)
    }

    companion object {
        private val TWO_PI = kotlin.math.PI.toFloat() * 2f
    }
}
