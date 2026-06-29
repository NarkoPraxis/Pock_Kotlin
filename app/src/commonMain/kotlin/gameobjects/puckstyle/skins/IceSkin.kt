package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import gameobjects.Settings
import gameobjects.puckstyle.CachedBrushSkin
import gameobjects.puckstyle.ColorGroup
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.paddles.IceLaunch
import physics.Point
import utility.Effects
import utility.PaintBucket
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class IceSkin(override val renderer: PuckRenderer) : CachedBrushSkin(renderer) {

    private var lastColors = theme.main

    // Cache rim stroke width + the Stroke instance — updated only when radius changes
    private var cachedRadius = -1f
    private var rimStrokeWidth = 0f
    private var rimStroke: Stroke = Stroke(width = 0f)

    override val explosionFrequency get() = 40
    override val scatterDensity get() = 0.9f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(IceScoreEffect(position.x, position.y, renderer.radius, highGoal, fullCircle = true, theme))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(IceScoreEffect(x, y, renderer.radius, highGoal = true, fullCircle = true, theme))
    }

    private class IceScoreEffect(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean,
        private val theme: gameobjects.puckstyle.ColorTheme
    ) : Effects.PersistentEffect {
        private val maxDistance = radius * 5f
        private val crystalPath = Path()
        private val strokeWidth = Settings.strokeWidth * 0.5f

        private var centralFrame = 0
        private val centralDuration = 60
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        private class Crystal(
            var x: Float, var y: Float,
            val dirX: Float, val dirY: Float,
            val speed: Float,
            val maxDist: Float,
            val radius: Float
        ) {
            var traveled = 0f
            var postMeltFrame = -1
            var done = false
            val meltDuration = 18
            val fadeDuration = 25
        }

        // Array (not List) so the per-frame step()/draw() loops iterate by index with no
        // Iterator allocation. Built once in init.
        private val crystals: Array<Crystal>
        // Cached Stroke for the crystal outline — width is fixed per effect instance.
        private val crystalStroke = Stroke(width = strokeWidth)

        init {
            val baseAngles = listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, PI)
            val fullAngles = List(12) { i -> i * (2.0 * PI / 12) }
            val srcAngles = if (fullCircle) fullAngles else baseAngles
            crystals = Array(srcAngles.size) { idx ->
                val a = srcAngles[idx]
                val adj = if (!fullCircle && !highGoal) a + PI else a
                Crystal(cx, cy, cos(adj.toFloat()), sin(adj.toFloat()), maxDistance / 45f, maxDistance, radius * 0.3f)
            }
        }

        override fun step() {
            centralFrame++
            var allDone = true
            for (ci in crystals.indices) {
                val c = crystals[ci]
                if (c.done) continue
                allDone = false
                if (c.postMeltFrame < 0) {
                    c.x += c.dirX * c.speed; c.y += c.dirY * c.speed
                    c.traveled += c.speed
                    if (c.traveled >= c.maxDist) c.postMeltFrame = 0
                } else {
                    c.postMeltFrame++
                    if (c.postMeltFrame >= c.meltDuration + c.fadeDuration) c.done = true
                }
            }
            if (allDone && centralFrame >= centralDuration) _isDone = true
        }

        override fun draw(scope: DrawScope) {
            val centralT = (centralFrame / centralDuration.toFloat()).coerceIn(0f, 1f)
            val centralAlpha = (100 * (1f - centralT)).toInt().coerceIn(0, 255)
            if (centralAlpha > 0) {
                scope.drawCircle(
                    Color(Palette.withAlpha(theme.main.primary, centralAlpha)),
                    radius * 2.5f * centralT + radius * centralT,
                    Offset(cx, cy)
                )
            }

            for (ci in crystals.indices) {
                val c = crystals[ci]
                if (c.done) continue
                if (c.postMeltFrame < 0) {
                    val progress = (c.traveled / c.maxDist).coerceIn(0f, 2f)
                    val crystalT = TRAVEL_T_START + progress * (TRAVEL_T_END - TRAVEL_T_START)
                    val puddleAlpha = (80 * progress).toInt().coerceIn(0, 120)
                    if (puddleAlpha > 0) {
                        scope.drawCircle(
                            Color(Palette.withAlpha(theme.main.primary, puddleAlpha)),
                            c.radius * 1.5f * progress,
                            Offset(c.x, c.y)
                        )
                    }
                    scope.drawCrystalAt(c.x, c.y, crystalT, c.radius, crystalPath, theme.main.primary, crystalStroke)
                } else {
                    val meltFraction = (c.postMeltFrame.toFloat() / c.meltDuration).coerceIn(0f, 1f)
                    val crystalT = TRAVEL_T_END + meltFraction * (1.4f - TRAVEL_T_END)
                    scope.drawCrystalAt(c.x, c.y, crystalT, c.radius, crystalPath, theme.main.primary, crystalStroke)
                    val fadeT = ((c.postMeltFrame - c.meltDuration).toFloat() / c.fadeDuration).coerceIn(0f, 1f)
                    val alpha = (120 * (1f - fadeT)).toInt().coerceIn(0, 255)
                    if (alpha > 0) {
                        scope.drawCircle(
                            Color(Palette.withAlpha(theme.main.primary, alpha)),
                            c.radius * 1.5f * centralT,
                            Offset(c.x, c.y)
                        )
                    }
                }
            }
        }

        companion object {
            private val CRYSTAL_ANGLES = FloatArray(8) { i -> (i * 2.0 * PI / 8).toFloat() }
            private const val TRAVEL_T_START = 0.1f
            private const val TRAVEL_T_END = 0.35f

            fun DrawScope.drawCrystalAt(x: Float, y: Float, t: Float, r: Float, crystalPath: Path, primaryColor: Int, stroke: Stroke) {
                val crystalR = r * (1.4f - t * 1.1f)
                if (crystalR < 1f) return
                crystalPath.reset()
                for (i in 0 until 8) {
                    val angle = CRYSTAL_ANGLES[i]
                    val outerR = crystalR * (if (i % 2 == 0) 2.3f else 1f)
                    val px = x + cos(angle) * outerR
                    val py = y + sin(angle) * outerR
                    if (i == 0) crystalPath.moveTo(px, py) else crystalPath.lineTo(px, py)
                }
                crystalPath.close()
                drawPath(crystalPath, PaintBucket.white)
                drawPath(crystalPath, Color(Palette.withAlpha(primaryColor, 130)), style = stroke)
            }
        }
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        IceLaunch.spawnImpact(position.x, position.y, renderer.radius * .4f, renderer.bakedPrimary(theme.main.primary))
    }

    override fun onShieldedCollision(position: Point) {
        IceLaunch.spawnImpact(position.x, position.y, renderer.radius * .6f, renderer.bakedPrimary(theme.main.primary))
    }

    override fun buildBrush(radius: Float): Brush {
        val midColor = Color(Palette.lerpColor(lastColors.primary, Palette.WHITE, 0.55f))
        return Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(lastColors.primary),
                0.5f to midColor,
                1f to PaintBucket.white
            ),
            center = Offset.Zero,
            radius = radius
        )
    }

    override fun DrawScope.drawBody() {
        val colors = responsiveGroup
        if (colors != lastColors) {
            lastColors = colors
            invalidateBrush()
        }
        ensureBrush(renderer.radius)

        if (cachedRadius != renderer.radius) {
            cachedRadius = renderer.radius
            rimStrokeWidth = renderer.strokeWidth * 0.7f
            rimStroke = Stroke(width = rimStrokeWidth)
        }

        // Gradient brush is built in local space (center = Offset.Zero); the translate places
        // that local origin at the puck center. Kept as withTransform to preserve gradient
        // positioning — the radius-keyed brush cache can't be center-baked per moving frame.
        withTransform({ translate(renderer.x, renderer.y) }) {
            drawCircle(brush = cachedBrush!!, radius = renderer.radius, center = Offset.Zero)
        }
        drawCircle(
            color = PaintBucket.white,
            radius = renderer.radius,
            center = Offset(renderer.x, renderer.y),
            style = rimStroke
        )
    }
}
