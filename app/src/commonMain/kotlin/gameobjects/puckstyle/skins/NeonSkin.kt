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
    // Cached Stroke objects (Stroke is a heap class, not a value class) — rebuilt only
    // when strokeWidth changes, never per frame.
    private var stroke5: Stroke = Stroke(width = 1f)
    private var stroke32: Stroke = Stroke(width = 1f)
    private var stroke18: Stroke = Stroke(width = 1f)
    private var stroke1: Stroke = Stroke(width = 1f)

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
        Effects.addPersistentEffect(NeonRingScar(renderer.x, renderer.y, renderer.radius, renderer.bakedPrimary(theme.shield.primary)))
    }

    override val explosionFrequency get() = 10
    override val scatterDensity get() = 1.3f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        // The burst now spawns at the ball's pop position (mid-zone), not flush on the goal edge, so a
        // half ring would float with its flat side disconnected — ripple a full circle around the pop.
        Effects.addPersistentEffect(NeonRingCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = true, renderer.bakedPrimary(theme.main.primary)))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(NeonRingCelebration(x, y, renderer.radius, highGoal = true, fullCircle = true, renderer.bakedPrimary(theme.main.primary)))
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
        private val emitEvery = 10

        private val totalEmitFrames = 80
        private val strokeWidth = radius * 0.3f
        private val startAngle = if (!fullCircle && !highGoal) 180f else 0f
        private val sweepAngle = if (fullCircle) 360f else 180f
        private var frame = 0
        // Fixed-capacity birth buffer: one birth on frame 1 plus one per emitEvery within
        // the emit window. Iterated by index (no Iterator alloc per frame).
        private val ringBirths = IntArray(totalEmitFrames / emitEvery + 2)
        private var ringCount = 0
        private var _isDone = false
        override val isDone: Boolean get() = _isDone
        // Stroke is a heap class; strokeWidth is constant, so build it once.
        private val ringStroke = Stroke(width = strokeWidth)

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
            // Emit the first ring on frame 1 so the echo bursts the instant the ball begins
            // to vanish, then keep emitting on the cadence.
            if ((frame - 1) % emitEvery == 0 && frame <= totalEmitFrames && ringCount < ringBirths.size) {
                ringBirths[ringCount++] = frame
            }
            if (frame > totalEmitFrames) {
                var allDone = true
                for (i in 0 until ringCount) {
                    if ((frame - ringBirths[i]) * growthRate < maxDistance) { allDone = false; break }
                }
                if (allDone) _isDone = true
            }
        }

        override fun draw(scope: DrawScope) {
            for (i in 0 until ringCount) {
                val birth = ringBirths[i]
                val age = frame - birth
                val r = age * growthRate
                if (r > maxDistance || r <= 0f) continue
                val ratio = r / maxDistance
                val alpha = neonAlpha(ratio)
                if (alpha <= 0) continue
                val drawColor = Color(Palette.withAlpha(color, alpha))
                if (fullCircle) {
                    scope.drawCircle(drawColor, r, Offset(cx, cy), style = ringStroke)
                } else {
                    scope.drawArc(
                        color = drawColor,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(cx - r, cy - r),
                        size = Size(r * 2f, r * 2f),
                        style = ringStroke
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
        // radius is constant for this scar; build the two Strokes once.
        private val outerStroke = Stroke(width = radius * 0.7f)
        private val innerStroke = Stroke(width = radius * 0.35f)

        override fun step() { frame++ }

        override fun draw(scope: DrawScope) {
            val t = (frame / 300f).coerceIn(0f, 1f)
            val alpha = (150 * (1f - t * 0.8f)).toInt().coerceIn(100, 255)
            // Outer glow
            scope.drawCircle(
                Color(Palette.withAlpha(color, (alpha * 0.5f).toInt())),
                radius,
                Offset(x, y),
                style = outerStroke
            )
            // Bright inner core
            scope.drawCircle(
                Color(Palette.withAlpha(color, alpha)),
                radius,
                Offset(x, y),
                style = innerStroke
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
            stroke5  = Stroke(width = sw5)
            stroke32 = Stroke(width = sw32)
            stroke18 = Stroke(width = sw18)
            stroke1  = Stroke(width = sw1)
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
        drawCircle(Color(glowColor25),  r, center, style = stroke5)
        drawCircle(Color(glowColor45),  r, center, style = stroke32)
        drawCircle(Color(glowColor110), r, center, style = stroke18)
        drawCircle(Color(glowColor220), r, center, style = stroke1)
    }
}
