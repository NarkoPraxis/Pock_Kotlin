package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.GhostLaunch
import physics.Point
import kotlin.math.cos
import kotlin.math.sin
import utility.Effects

class GhostSkin( override val renderer: PuckRenderer) : PuckSkin {

    private data class AuraRing(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)

    private val auraRings = listOf(
        AuraRing(.6f, 0.2f, 1.0f, 80,  .5f),
        AuraRing(.8f, 0.1f, 2.0f, 50,  1f),
        AuraRing(.95f, 0.3f, 3.0f, 30,  2.0f),
        AuraRing(1.10f, 0.2f, 4.0f, 20,  4f)
    )

    override fun onCollisionWin(position: Point, speed: Float) {
        GhostLaunch.spawnImpact(position.x, position.y, renderer.radius * .4f, theme.main.primary, renderer)
    }

    override fun onShieldedCollision(position: Point) {
        GhostLaunch.spawnImpact(position.x, position.y, renderer.radius * .6f, theme.shield.primary, renderer)
    }

    private val fill = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        isAntiAlias = true; style = Paint.Style.FILL
    }
    private val stroke = Paint().apply {
        color = Color.argb(200, 255, 255, 255)
        isAntiAlias = true; style = Paint.Style.STROKE
    }
    private val glow = Paint().apply {
        isAntiAlias = true; isDither = true; style = Paint.Style.STROKE
    }

    override val explosionFrequency get() = 35
    override val scatterDensity get() = 1.2f

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(SpiritCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false, color = theme.main.secondary))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(SpiritCelebration(x, y, renderer.radius, highGoal = true, fullCircle = true, color = theme.main.secondary))
    }

    private class SpiritCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean,
        private val color: Int
    ) : Effects.PersistentEffect {

        private data class AuraConfig(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)
        private val auraRings = listOf(
            AuraConfig(1.10f, 0.06f, 0.0f, 55, 1.6f),
            AuraConfig(1.20f, 0.08f, 1.0f, 35, 1.2f),
            AuraConfig(1.35f, 0.10f, 2.2f, 20, 2.0f)
        )

        private val directions = mutableListOf<Point>()
        private val step = 10f
        private var currentDistance = 0f
        private var alphaF = 0f
        private var fadingIn = true
        private var frame = 0
        private var done = false
        override val isDone: Boolean get() = done

        private val bodyPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val glowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        private val baseSw = Settings.strokeWidth * 0.7f
        private val orbR = radius * 0.7f

        init {
            if (fullCircle) {
                for (i in 0 until 12) {
                    val a = (i * 2.0 * Math.PI / 12).toFloat()
                    directions.add(Point(cos(a), sin(a)))
                }
            } else {
                for (angle in listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, Math.PI)) {
                    val a = angle.toFloat()
                    if (highGoal) directions.add(Point(cos(a), sin(a)))
                    else directions.add(-Point(cos(a), sin(a)))
                }
            }
        }

        override fun step() {}

        override fun draw(canvas: Canvas) {
            if (done) return
            frame++
            if (fadingIn) {
                currentDistance += step
                alphaF = (alphaF + step).coerceAtMost(255f)
                if (alphaF >= 255f) fadingIn = false
            } else {
                currentDistance += step / 2f
                alphaF -= step
                if (alphaF <= 0f) { done = true; return }
            }

            val a = alphaF / 255f
            val frameF = frame.toFloat()
            val pulse = 1f + 0.1f * sin(frameF * 0.3f)
            val r = orbR * pulse
            val sw = baseSw
            val auraFramePhase = frameF * 0.04f

            for (direction in directions) {
                val ox = cx + direction.x * currentDistance
                val oy = cy + direction.y * currentDistance

                for (ring in auraRings) {
                    val auraR = r * ring.baseMult + r * ring.amp * sin(auraFramePhase + ring.phase)
                    glowPaint.color = Palette.withAlpha(color, (ring.alpha * a).toInt())
                    glowPaint.strokeWidth = sw * ring.strokeMult
                    canvas.drawCircle(ox, oy, auraR, glowPaint)
                }

                bodyPaint.color = Color.argb((120 * a).toInt(), 255, 255, 255)
                canvas.drawCircle(ox, oy, r, bodyPaint)

                glowPaint.color = Color.argb((200 * a).toInt(), 255, 255, 255)
                glowPaint.strokeWidth = sw
                canvas.drawCircle(ox, oy, r, glowPaint)

                val innerR = r * 0.75f + r * 0.1f * sin(frameF * 0.025f + 5f)
                glowPaint.strokeWidth = sw * 0.7f
                glowPaint.color = Color.argb((160 * a).toInt(), 255, 255, 255)
                canvas.drawCircle(ox, oy, innerR, glowPaint)
            }
        }
    }

    companion object {
        fun radiusOffset(renderer: PuckRenderer): Float {
            if (renderer.currentCharge <= 0f) return 1f
            val halfRange = (Settings.sweetSpotMax - Settings.chargeStart) * 0.5f
            val t = ((renderer.currentCharge - Settings.chargeStart) / halfRange).coerceIn(0f, 1f)
            return 1f - 0.3f * t
        }
    }

    override fun drawBody(canvas: Canvas) {
        val glowColor = responsivePrimary
        val r = renderer.radius
        val sw = renderer.strokePaint.strokeWidth
        val radiusOffset = radiusOffset(renderer)

        // Hoist per-frame oscillator base values shared across aura rings
        val framePhase = renderer.frame * 0.04f
        val innerFramePhase = renderer.frame * 0.025f

        // Animated aura rings drawn behind the orb — each has its own oscillation phase
        for (ring in auraRings) {
            val sinVal = sin(framePhase + ring.phase)
            val ringR = r * ring.baseMult + r * ring.amp * sinVal
            val alpha = ring.alpha + (ring.alpha * ring.amp * sinVal).toInt()
            glow.color = Palette.withAlpha(glowColor, alpha)
            glow.strokeWidth = sw * ring.strokeMult
            canvas.drawCircle(renderer.x, renderer.y, ringR, glow)
        }

        // White orb at exact radius — not oversized
        canvas.drawCircle(renderer.x, renderer.y, r * radiusOffset, fill)

        // Inner ring pulses between 50% and 100% of radius, slowly
        val innerR = r * 0.75f + r * 0.1f * sin(innerFramePhase + 5.0f)
        stroke.strokeWidth = sw * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, innerR * radiusOffset, stroke)

        renderer.chargePaint.color = glowColor
    }
}
