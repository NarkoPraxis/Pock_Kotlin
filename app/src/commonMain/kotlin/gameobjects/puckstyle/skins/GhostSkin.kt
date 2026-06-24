package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.GhostLaunch
import physics.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import utility.Effects

class GhostSkin(override val renderer: PuckRenderer) : PuckSkin {

    private data class AuraRing(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)

    // Array (not List) so the per-frame draw loop iterates by index without allocating an Iterator.
    private val auraRings = arrayOf(
        AuraRing(.6f,   0.2f, 1.0f, 80, .5f),
        AuraRing(.8f,   0.1f, 2.0f, 50, 1f),
        AuraRing(.95f,  0.3f, 3.0f, 30, 2.0f),
        AuraRing(1.10f, 0.2f, 4.0f, 20, 4f)
    )

    // Hoisted body strokes for the live skin draw. Widths derive from renderer.strokeWidth, which is
    // effectively immutable after setup; rebuild only if it actually changes. Avoids ~5 Stroke
    // allocations per puck per frame in drawBody().
    private var cachedBodySw = -1f
    private lateinit var bodyAuraStrokes: Array<Stroke>
    private lateinit var bodyInnerStroke: Stroke

    private fun ensureBodyStrokes(sw: Float) {
        if (cachedBodySw != sw) {
            cachedBodySw = sw
            bodyAuraStrokes = Array(auraRings.size) { Stroke(width = sw * auraRings[it].strokeMult) }
            bodyInnerStroke = Stroke(width = sw * 0.7f)
        }
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        GhostLaunch.spawnImpact(position.x, position.y, renderer.radius * .4f, renderer.bakedPrimary(theme.main.primary), renderer)
    }

    override fun onShieldedCollision(position: Point) {
        GhostLaunch.spawnImpact(position.x, position.y, renderer.radius * .6f, renderer.bakedPrimary(theme.shield.primary), renderer)
    }

    override val explosionFrequency get() = 35
    override val scatterDensity get() = 1.2f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(SpiritCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false, color = renderer.bakedSecondary(theme.main.secondary)))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(SpiritCelebration(x, y, renderer.radius, highGoal = true, fullCircle = true, color = renderer.bakedSecondary(theme.main.secondary)))
    }

    private class SpiritCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean,
        private val color: Int
    ) : Effects.PersistentEffect {

        private data class AuraConfig(val baseMult: Float, val amp: Float, val phase: Float, val alpha: Int, val strokeMult: Float)
        // Array (not List) so the per-frame effect draw loop iterates by index without allocating an Iterator.
        private val auraRings = arrayOf(
            AuraConfig(1.10f, 0.06f, 0.0f, 55, 1.6f),
            AuraConfig(1.20f, 0.08f, 1.0f, 35, 1.2f),
            AuraConfig(1.35f, 0.10f, 2.2f, 20, 2.0f)
        )

        // Built once in init, then drawn from each frame: an immutable Array<Point> iterated by index
        // (no per-frame Iterator). The temp list is a local in init, so nothing is retained after setup.
        private val directions: Array<Point>
        private val step = 10f
        private var currentDistance = 0f
        private var alphaF = 0f
        private var fadingIn = true
        private var frame = 0
        private var done = false
        override val isDone: Boolean get() = done

        private val baseSw = Settings.strokeWidth * 0.7f
        private val orbR = radius * 0.7f

        // Hoisted strokes: widths are constant (baseSw * constant ring/body multipliers). Previously a
        // new Stroke was allocated per ring AND per body circle, for EVERY direction, EVERY frame —
        // up to ~60 Stroke allocations/frame during a celebration, a direct GC-pressure spike at score time.
        private val auraStrokes = Array(auraRings.size) { Stroke(width = baseSw * auraRings[it].strokeMult) }
        private val bodyStroke = Stroke(width = baseSw)
        private val innerStroke = Stroke(width = baseSw * 0.7f)

        init {
            val list = mutableListOf<Point>()
            if (fullCircle) {
                for (i in 0 until 12) {
                    val a = (i * 2.0 * PI / 12).toFloat()
                    list.add(Point(cos(a), sin(a)))
                }
            } else {
                val angles = doubleArrayOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, PI)
                for (angle in angles) {
                    val a = angle.toFloat()
                    if (highGoal) list.add(Point(cos(a), sin(a)))
                    else list.add(-Point(cos(a), sin(a)))
                }
            }
            directions = list.toTypedArray()
        }

        override fun step() {}

        override fun draw(scope: DrawScope) {
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
            val auraFramePhase = frameF * 0.04f
            val innerR = r * 0.75f + r * 0.1f * sin(frameF * 0.025f + 5f)

            for (di in directions.indices) {
                val direction = directions[di]
                val ox = cx + direction.x * currentDistance
                val oy = cy + direction.y * currentDistance

                for (ri in auraRings.indices) {
                    val ring = auraRings[ri]
                    val auraR = r * ring.baseMult + r * ring.amp * sin(auraFramePhase + ring.phase)
                    scope.drawCircle(
                        Color(Palette.withAlpha(color, (ring.alpha * a).toInt())),
                        auraR,
                        Offset(ox, oy),
                        style = auraStrokes[ri]
                    )
                }

                scope.drawCircle(
                    Color(Palette.argb((120 * a).toInt(), 255, 255, 255)),
                    r,
                    Offset(ox, oy)
                )

                scope.drawCircle(
                    Color(Palette.argb((200 * a).toInt(), 255, 255, 255)),
                    r,
                    Offset(ox, oy),
                    style = bodyStroke
                )

                scope.drawCircle(
                    Color(Palette.argb((160 * a).toInt(), 255, 255, 255)),
                    innerR,
                    Offset(ox, oy),
                    style = innerStroke
                )
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

    override fun DrawScope.drawBody() {
        val glowColor = responsivePrimary
        val r = renderer.radius
        val sw = renderer.strokeWidth
        val radiusOffset = radiusOffset(renderer)
        val center = Offset(renderer.x, renderer.y)
        ensureBodyStrokes(sw)

        // Strobe (not frame) so the aura keeps breathing in a static UI preview, where geometry is
        // frozen but the strobe clock keeps ticking; in live play strobe == frame, so play is unchanged.
        val framePhase = renderer.strobe * 0.04f
        val innerFramePhase = renderer.strobe * 0.025f

        for (ri in auraRings.indices) {
            val ring = auraRings[ri]
            val sinVal = sin(framePhase + ring.phase)
            val ringR = r * ring.baseMult + r * ring.amp * sinVal
            val alpha = ring.alpha + (ring.alpha * ring.amp * sinVal).toInt()
            drawCircle(
                Color(Palette.withAlpha(glowColor, alpha)),
                ringR,
                center,
                style = bodyAuraStrokes[ri]
            )
        }

        drawCircle(Color(Palette.argb(120, 255, 255, 255)), r * radiusOffset, center)

        val innerR = r * 0.75f + r * 0.1f * sin(innerFramePhase + 5.0f)
        drawCircle(
            Color(Palette.argb(200, 255, 255, 255)),
            innerR * radiusOffset,
            center,
            style = bodyInnerStroke
        )

        renderer.chargeColor = glowColor
    }
}
