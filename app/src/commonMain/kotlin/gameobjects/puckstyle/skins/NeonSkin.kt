package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import physics.Point
import utility.Effects

class NeonSkin(override val renderer: PuckRenderer) : PuckSkin {

    // Cache for stroke-width-derived values — updated only when strokeWidth changes
    private var cachedStrokeWidth = -1f
    private var sw5 = 0f
    private var sw32 = 0f
    private var sw18 = 0f
    private var sw1 = 0f

    // Cache for primary-color-derived glow colors — updated only when primary changes
    private var cachedPrimary = Int.MIN_VALUE
    private var glowColor25 = 0
    private var glowColor45 = 0
    private var glowColor110 = 0
    private var glowColor220 = 0

    override fun onCollisionWin(position: Point, speed: Float) {
        Effects.addPersistentEffect(NeonRingScar(renderer.x, renderer.y, renderer.radius, responsivePrimary))
    }

    override fun onShieldedCollision(position: Point) {
        Effects.addPersistentEffect(NeonRingScar(renderer.x, renderer.y, renderer.radius, theme.shield.primary))
    }

    override val explosionFrequency get() = 10
    override val scatterDensity get() = 1.3f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(NeonRingCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false, theme.main.primary))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(NeonRingCelebration(x, y, renderer.radius, highGoal = true, fullCircle = true, theme.main.primary))
    }

    private class NeonRingCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val highGoal: Boolean,
        private val fullCircle: Boolean,
        private val color: Int
    ) : Effects.PersistentEffect {
        private val maxDistance = radius * 5f
        private val growthRate = maxDistance / 78f
        private val emitEvery = 20

        private val totalEmitFrames = 55
        private val strokeWidth = radius * 0.3f
        private val startAngle = if (!fullCircle && !highGoal) 180f else 0f
        private val sweepAngle = if (fullCircle) 360f else 180f
        private var frame = 0
        private val ringBirths = mutableListOf<Int>()
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        private fun neonAlpha(ratio: Float): Int {
            val blendWidth = 0.04f
            val b1 = 0.45f; val b2 = 0.83f
            val v1 = 150f
            val v2 = 150f + (40f - 150f) * ((ratio - b1) / (b2 - b1)).coerceIn(0f, 1f)
            val v3 = 40f + (0f - 40f) * ((ratio - b2) / (1f - b2)).coerceIn(0f, 1f)
            val t1 = ((ratio - (b1 - blendWidth)) / (2f * blendWidth)).coerceIn(0f, 1f)
            val blended12 = v1 + (v2 - v1) * t1
            val t2 = ((ratio - (b2 - blendWidth)) / (2f * blendWidth)).coerceIn(0f, 1f)
            return (blended12 + (v3 - blended12) * t2).toInt().coerceIn(0, 255)
        }

        override fun step() {
            frame++
            if (frame % emitEvery == 0 && frame <= totalEmitFrames) ringBirths.add(frame)
            if (frame > totalEmitFrames && ringBirths.all { (frame - it) * growthRate >= maxDistance }) _isDone = true
        }

        override fun draw(scope: DrawScope) {
            for (birth in ringBirths) {
                val age = frame - birth
                val r = age * growthRate
                if (r > maxDistance || r <= 0f) continue
                val ratio = r / maxDistance
                val alpha = neonAlpha(ratio)
                if (alpha <= 0) continue
                val drawColor = Color(Palette.withAlpha(color, alpha))
                if (fullCircle) {
                    scope.drawCircle(drawColor, r, Offset(cx, cy), style = Stroke(width = strokeWidth))
                } else {
                    scope.drawArc(
                        color = drawColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
        }
    }

    private class NeonRingScar(
        private val x: Float, private val y: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(scope: DrawScope) {
            val t = (frame / 300f).coerceIn(0f, 1f)
            val alpha = (150 * (1f - t * 0.8f)).toInt().coerceIn(100, 255)
            // Outer glow
            scope.drawCircle(
                Color(Palette.withAlpha(color, (alpha * 0.5f).toInt())),
                radius,
                Offset(x, y),
                style = Stroke(width = radius * 0.7f)
            )
            // Bright inner core
            scope.drawCircle(
                Color(Palette.withAlpha(color, alpha)),
                radius,
                Offset(x, y),
                style = Stroke(width = radius * 0.35f)
            )
        }
    }

    override fun DrawScope.drawBody() {
        val sw = renderer.strokeWidth
        if (cachedStrokeWidth != sw) {
            cachedStrokeWidth = sw
            sw5  = sw * 5.0f
            sw32 = sw * 3.2f
            sw18 = sw * 1.8f
            sw1  = sw * 1.0f
        }

        if (cachedPrimary != responsivePrimary) {
            cachedPrimary = responsivePrimary
            glowColor25  = Palette.withAlpha(responsivePrimary, 25)
            glowColor45  = Palette.withAlpha(responsivePrimary, 45)
            glowColor110 = Palette.withAlpha(responsivePrimary, 110)
            glowColor220 = Palette.withAlpha(responsivePrimary, 220)
        }

        val center = Offset(renderer.x, renderer.y)
        val r = renderer.radius
        // 4 glow rings, outermost first — body always stays theme color
        drawCircle(Color(glowColor25),  r, center, style = Stroke(width = sw5))
        drawCircle(Color(glowColor45),  r, center, style = Stroke(width = sw32))
        drawCircle(Color(glowColor110), r, center, style = Stroke(width = sw18))
        drawCircle(Color(glowColor220), r, center, style = Stroke(width = sw1))
    }
}
