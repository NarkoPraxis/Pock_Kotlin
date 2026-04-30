package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.GalaxyLaunch
import gameobjects.puckstyle.paddles.GhostLaunch
import physics.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import utility.Effects

class GhostSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

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

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(SpiritCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false, color = responsivePrimary, renderer = renderer))
    }

    private class SpiritCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean,
        private val color: Int,
        private val renderer: PuckRenderer
    ) : Effects.PersistentEffect {
        private enum class Phase { Expanding, Hovering, Returning }

        private class Spirit(
            var x: Float, var y: Float,
            val dirX: Float, val dirY: Float,
            val speed: Float,
            val maxDist: Float,
            val returnSpeed: Float
        ) {
            var phase = Phase.Expanding
            var hoverFrame = 0
            var done = false
        }

        private val spirits: List<Spirit>
        private val bodyPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val glowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
        private val maxDistance = radius * 3f
        private val HOVER_DURATION = 15
        private var frame = 0
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        init {
            val baseAngles = listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, Math.PI)
            val fullAngles = List(12) { i -> i * (2.0 * Math.PI / 12) }
            val srcAngles = if (fullCircle) fullAngles else baseAngles
            spirits = srcAngles.map { a ->
                val adj = if (!fullCircle && !highGoal) a + Math.PI else a
                Spirit(cx, cy, cos(adj.toFloat()), sin(adj.toFloat()), maxDistance / 45f, maxDistance, Settings.screenRatio * 0.15f)
            }
        }

        override fun step() {
            frame++
            var allDone = true
            for (s in spirits) {
                if (s.done) continue
                allDone = false
                when (s.phase) {
                    Phase.Expanding -> {
                        s.x += s.dirX * s.speed; s.y += s.dirY * s.speed
                        val dx = s.x - cx; val dy = s.y - cy
                        if (sqrt(dx * dx + dy * dy) >= s.maxDist) {
                            s.x = cx + s.dirX * s.maxDist; s.y = cy + s.dirY * s.maxDist
                            s.phase = Phase.Hovering
                        }
                    }
                    Phase.Hovering -> {
                        s.hoverFrame++
                        if (s.hoverFrame >= HOVER_DURATION) s.phase = Phase.Returning
                    }
                    Phase.Returning -> {
                        val tx = renderer.x; val ty = renderer.y
                        val dx = tx - s.x; val dy = ty - s.y
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist < s.returnSpeed) { s.done = true } else {
                            s.x += (dx / dist) * s.returnSpeed; s.y += (dy / dist) * s.returnSpeed
                        }
                    }
                }
            }
            if (allDone) _isDone = true
        }

        override fun draw(canvas: Canvas) {
            val r = radius * 0.45f
            val sw = Settings.strokeWidth * 0.7f
            for (s in spirits) {
                if (s.done) continue
                glowPaint.color = Palette.withAlpha(color, 55)
                glowPaint.strokeWidth = sw * 1.4f
                canvas.drawCircle(s.x, s.y, r * 1.2f, glowPaint)
                bodyPaint.color = Color.argb(120, 255, 255, 255)
                canvas.drawCircle(s.x, s.y, r, bodyPaint)
                glowPaint.color = Color.argb(180, 255, 255, 255)
                glowPaint.strokeWidth = sw
                canvas.drawCircle(s.x, s.y, r, glowPaint)
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

        val sw = renderer.strokePaint.strokeWidth

        val radiusOffset = radiusOffset(renderer)

        // Animated aura rings drawn behind the orb — each has its own oscillation phase
        for (ring in auraRings) {
            val r = renderer.radius * ring.baseMult +
                    renderer.radius * ring.amp * sin(renderer.frame * 0.04f + ring.phase)
            val alpha = ring.alpha + ring.alpha * (ring.amp * sin(renderer.frame * 0.04f + ring.phase)).toInt()
            glow.color = Palette.withAlpha(glowColor, alpha)
            glow.strokeWidth = sw * ring.strokeMult
            canvas.drawCircle(renderer.x, renderer.y, r , glow)
        }

        // White orb at exact radius — not oversized
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius * radiusOffset, fill)

        // Inner ring pulses between 50% and 100% of radius, slowly
        val innerR = renderer.radius * 0.75f +
                renderer.radius * 0.1f * sin(renderer.frame * 0.025f + 5.0f)
        stroke.strokeWidth = sw * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, innerR * radiusOffset, stroke)

        renderer.chargePaint.color = glowColor
    }
}
