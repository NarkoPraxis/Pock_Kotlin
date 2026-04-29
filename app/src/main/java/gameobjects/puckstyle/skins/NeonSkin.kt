package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import physics.Point
import utility.Effects

class NeonSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

    // Subtle dark fill so the hollow center reads as a translucent tube, not empty space
    private val subtleFill = Paint().apply {
        color = Color.argb(35, 0, 0, 0)
        isAntiAlias = true; isDither = true; style = Paint.Style.FILL
    }

    private val glowPaint = Paint().apply {
        isAntiAlias = true; isDither = true; style = Paint.Style.STROKE
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        Effects.addPersistentEffect(NeonRingScar(renderer.x, renderer.y, renderer.radius, responsivePrimary))
    }

    override fun onShieldedCollision(position: Point) {
        Effects.addPersistentEffect(NeonRingScar(renderer.x, renderer.y, renderer.radius, theme.shield.primary))
    }

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(NeonRingCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false, responsivePrimary))
    }

    private class NeonRingCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val highGoal: Boolean,
        private val fullCircle: Boolean,
        private val color: Int
    ) : Effects.PersistentEffect {
        private val maxDistance = radius * 3f
        private val growthRate = maxDistance / 55f
        private val emitEvery = 6
        private val totalEmitFrames = 55
        private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        private val rect = RectF()
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

        override fun draw(canvas: Canvas) {
            paint.strokeWidth = radius * 0.3f
            for (birth in ringBirths) {
                val age = frame - birth
                val r = age * growthRate
                if (r > maxDistance || r <= 0f) continue
                val ratio = r / maxDistance
                val alpha = neonAlpha(ratio)
                if (alpha <= 0) continue
                paint.color = Palette.withAlpha(color, alpha)
                if (fullCircle) {
                    canvas.drawCircle(cx, cy, r, paint)
                } else {
                    rect.set(cx - r, cy - r, cx + r, cy + r)
                    canvas.drawArc(rect, startAngle, sweepAngle, false, paint)
                }
            }
        }
    }

    private class NeonRingScar(
        private val x: Float, private val y: Float,
        private val radius: Float, private val color: Int
    ) : Effects.PersistentEffect {
        private val paint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val len = radius * 1.5f
        private var frame = 0
        override val isDone = false

        override fun step() { frame++ }

        override fun draw(canvas: Canvas) {
            val t = (frame / 300f).coerceIn(0f, 1f)
            val alpha = (150 * (1f - t * 0.8f)).toInt().coerceIn(100, 255)
            // Outer glow
            paint.color = color
            paint.alpha = (alpha * 0.5f).toInt()
            paint.strokeWidth = radius * 0.7f
            canvas.drawCircle(x, y, radius, paint)
            // Bright inner core
            paint.alpha = alpha
            paint.strokeWidth = radius * 0.35f
            canvas.drawCircle(x, y, radius, paint)
            paint.alpha = 255
        }
    }

    override fun drawBody(canvas: Canvas) {
        val sw = renderer.strokePaint.strokeWidth
        val primary = resolvedColors().primary

        // 4 glow rings, outermost first — body always stays theme color, charging shown via chargePaint
        glowPaint.color = Palette.withAlpha(primary, 25);  glowPaint.strokeWidth = sw * 5.0f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(primary, 45);  glowPaint.strokeWidth = sw * 3.2f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(primary, 110); glowPaint.strokeWidth = sw * 1.8f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(primary, 220); glowPaint.strokeWidth = sw
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)

        renderer.chargePaint.color = theme.shield.primary
    }
}
