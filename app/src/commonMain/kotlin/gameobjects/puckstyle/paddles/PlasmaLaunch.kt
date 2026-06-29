package gameobjects.puckstyle.paddles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class PlasmaLaunch(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {

    private var lastChargeRatio = -1f
    private var cachedBrush: Brush? = null
    private var cachedBrushCx = Float.NaN
    private var cachedBrushCy = Float.NaN

    private var cachedBodyRadius = -1f
    private var smallRadius = 0f
    private var medRadius = 0f
    private var largeRadius = 0f
    private var arcStrokeWidth = 0f
    private var arcStroke: Stroke? = null

    private fun ensureBodyCache() {
        if (cachedBodyRadius != renderer.radius) {
            cachedBodyRadius = renderer.radius
            smallRadius = renderer.radius * 0.2f
            medRadius = renderer.radius * 0.7f
            largeRadius = medRadius * 0.9f
            arcStrokeWidth = renderer.strokeWidth * 0.5f
            arcStroke = Stroke(width = arcStrokeWidth, cap = StrokeCap.Round)
        }
    }

    override fun drawChargingPaddle(scope: DrawScope) = drawBody(scope, paddleX, paddleY)

    override fun drawStrikingPaddle(
        scope: DrawScope,
        cx: Float, cy: Float, aX: Float, aY: Float,
        sweet: Boolean, fatigued: Boolean, progress: Float
    ) {
        drawBody(scope, cx, cy)
    }

    private fun ensureBrush(cx: Float, cy: Float, radius: Float) {
        if (chargeFillRatio != lastChargeRatio || cx != cachedBrushCx || cy != cachedBrushCy ||
            renderer.isInert || phase == ChargePhase.Inert) {
            cachedBrush = createBrush(cx, cy, radius)
            lastChargeRatio = chargeFillRatio
            cachedBrushCx = cx
            cachedBrushCy = cy
        }
    }

    private fun createBrush(cx: Float, cy: Float, radius: Float): Brush {
        // Under a rainbow override main and shield are the same strobing hue, so the charge lerp
        // collapses to that single hue (the body strobes); otherwise keep the main→shield charge blend.
        val primary = when {
            phase == ChargePhase.Inert || renderer.isInert -> theme.inert.primary
            renderer.responsiveIsRainbow -> renderer.responsiveColorGroup.primary
            else -> Palette.lerpColor(theme.main.primary, theme.shield.primary, chargeFillRatio)
        }
        val secondary = when {
            phase == ChargePhase.Inert || renderer.isInert -> theme.inert.secondary
            renderer.responsiveIsRainbow -> renderer.responsiveColorGroup.secondary
            else -> Palette.lerpColor(theme.main.secondary, theme.shield.secondary, chargeFillRatio)
        }
        return Brush.radialGradient(
            colorStops = arrayOf(0f to Color(Palette.WHITE), 0.4f to Color(primary), 1f to Color(secondary)),
            center = Offset(cx, cy),
            radius = radius
        )
    }

    fun drawBody(scope: DrawScope, cx: Float, cy: Float) {
        ensureBodyCache()
        ensureBrush(cx, cy, medRadius)
        val brush = cachedBrush ?: return
        scope.drawCircle(brush, medRadius, Offset(cx, cy))
        // Static UI: reseed the arcs off the strobe clock each tick so the lightning keeps crackling
        // in place (frame is frozen); live play keeps the global per-frame randomness. The seeded
        // Random must restart its sequence on every draw call (a single frozen frame may draw the
        // paddle more than once), so it is allocated here for identical visuals.
        val arcRng = if (renderer.staticUiMode) Random(animFrame * 2654435761L) else Random
        repeat(3) {
            val a1 = arcRng.nextFloat() * TWO_PI
            val a2 = a1 + (arcRng.nextFloat() - 0.5f) * 2f
            scope.drawLine(
                color = Color(Palette.WHITE),
                start = Offset(cx + cos(a1) * smallRadius, cy + sin(a1) * smallRadius),
                end = Offset(cx + cos(a2) * largeRadius, cy + sin(a2) * largeRadius),
                strokeWidth = arcStrokeWidth,
                cap = StrokeCap.Round
            )
        }
        scope.drawCircle(
            color = Color(Palette.WHITE),
            radius = medRadius,
            center = Offset(cx, cy),
            style = arcStroke ?: Stroke(width = arcStrokeWidth, cap = StrokeCap.Round)
        )
    }

    override fun onSpawnResidual(rx: Float, ry: Float, aX: Float, aY: Float) {
        Effects.addPersistentEffect(
            PlasmaLightningBurst(rx, ry, renderer.radius, responsivePrimary, responsiveSecondary)
        )
    }

    companion object {
        internal val TWO_PI = PI.toFloat() * 2f

        fun spawnLighting(cx: Float, cy: Float, puckRadius: Float, primary: Int, secondary: Int) {
            Effects.addPersistentEffect(PlasmaLightningBurst(cx, cy, puckRadius, primary, secondary))
        }

        fun spawnCelebration(cx: Float, cy: Float, puckRadius: Float, primary: Int, secondary: Int, highGoal: Boolean, fullCircle: Boolean) {
            Effects.addPersistentEffect(PlasmaLightningBurst(cx, cy, puckRadius, primary, secondary, highGoal = highGoal, fullCircle = fullCircle))
        }
    }

    private class PlasmaLightningBurst(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val primary: Int,
        private val secondary: Int,
        private val highGoal: Boolean = false,
        private val fullCircle: Boolean = true
    ) : Effects.PersistentEffect {

        private val boltStrokeWidth = Settings.strokeWidth * 0.7f
        private val boltPath = Path()
        private val boltStroke = Stroke(width = boltStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        private var frame = 0
        private val totalFrames = 80
        private val totalFramesInv = 1f / totalFrames

        // The bolts fade to alpha 0 at totalFrames for every variant (score/victory/collision), so the
        // burst is finished once the fade completes regardless of shape — no need to keep an invisible
        // full-circle burst alive.
        override val isDone: Boolean get() = frame >= totalFrames

        private val anchors: Array<FloatArray>
        init {
            val rng = Random(cx.toLong() xor cy.toLong())
            anchors = if (fullCircle) {
                Array(20) {
                    val angle = rng.nextFloat() * TWO_PI
                    val dist = radius * (rng.nextFloat() * 3f)
                    floatArrayOf(cx + cos(angle) * dist, cy + sin(angle) * dist)
                }
            } else {
                val maxDist = radius * 5f
                val arcRange = PI.toFloat()
                val arcOffset = if (highGoal) 0f else PI.toFloat()
                Array(20) { i ->
                    val angle = arcOffset + (i.toFloat() / 20f) * arcRange + rng.nextFloat() * 0.3f
                    floatArrayOf(cx + cos(angle) * maxDist * (0.5f + rng.nextFloat() * 0.5f),
                                 cy + sin(angle) * maxDist * (0.5f + rng.nextFloat() * 0.5f))
                }
            }
        }

        override fun step() { frame++ }

        override fun draw(scope: DrawScope) {
            val life = (1f - frame.toFloat() * totalFramesInv).coerceAtLeast(0f)
            val alpha = (230f * life * life).toInt().coerceIn(0, 255)
            if (alpha > 0) {
                // Must restart the same deterministic sequence on every draw within a (frame/3)
                // bucket so repeated frames replay identical bolts; Random has no reseed, so the
                // allocation here is required for identical visuals.
                val rand = Random((frame / 3).toLong())
                val boltCount = (10 * life).toInt().coerceAtLeast(3)
                repeat(boltCount) { idx ->
                    val aIdx = rand.nextInt(anchors.size)
                    val bIdx = (aIdx + 1 + rand.nextInt(anchors.size - 1)) % anchors.size
                    val a = anchors[aIdx]; val b = anchors[bIdx]
                    val color = Palette.withAlpha(if (idx % 2 == 0) primary else secondary, alpha)
                    drawBolt(scope, a[0], a[1], b[0], b[1], rand, color)
                }
            }
        }

        private fun drawBolt(scope: DrawScope, x1: Float, y1: Float, x2: Float, y2: Float, rand: Random, color: Int) {
            val segments = 4
            val dx = x2 - x1; val dy = y2 - y1
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val nx = -dy / len; val ny = dx / len
            boltPath.reset()
            boltPath.moveTo(x1, y1)
            for (i in 1 until segments) {
                val t = i.toFloat() / segments
                val mx = x1 + dx * t; val my = y1 + dy * t
                val jag = radius * 2f * (rand.nextFloat() - 0.5f)
                boltPath.lineTo(mx + nx * jag, my + ny * jag)
            }
            boltPath.lineTo(x2, y2)
            scope.drawPath(
                boltPath,
                Color(color),
                style = boltStroke
            )
        }
    }

    private class PlasmaBurn(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        private val primary: Int,
        private val secondary: Int
    ) : Effects.PersistentEffect {

        override val isDone = false

        private val burnBoltStrokeWidth = Settings.strokeWidth * 0.38f
        private val boltPath = Path()
        private val burnBoltStroke = Stroke(width = burnBoltStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        private val scorchColor = Palette.argb(170, 15, 5, 20)

        private data class BoltStub(
            val x1: Float, val y1: Float,
            val x2: Float, val y2: Float,
            val midX: Float, val midY: Float,
            val color: Int
        )
        // Array (not List) so the per-frame draw loop iterates by index without allocating an Iterator.
        private val stubs: Array<BoltStub>

        init {
            val rng = Random(cx.toInt() xor cy.toInt())
            val count = 12
            stubs = Array(count) { i ->
                val angle = (i.toFloat() / count) * TWO_PI + rng.nextFloat() * 0.4f
                val reach = radius * (0.7f + rng.nextFloat() * 0.9f)
                val ex = cx + cos(angle) * reach; val ey = cy + sin(angle) * reach
                val dx = ex - cx; val dy = ey - cy
                val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                val nx = -dy / len; val ny = dx / len
                val jag = radius * 0.22f * (rng.nextFloat() - 0.5f)
                val mx = cx + dx * 0.55f + nx * jag; val my = cy + dy * 0.55f + ny * jag
                BoltStub(cx, cy, ex, ey, mx, my, if (i % 2 == 0) primary else secondary)
            }
        }

        override fun step() { /* permanent */ }

        override fun draw(scope: DrawScope) {
            scope.drawCircle(Color(scorchColor), radius * 0.65f, Offset(cx, cy))
            for (i in stubs.indices) {
                val stub = stubs[i]
                boltPath.reset()
                boltPath.moveTo(stub.x1, stub.y1)
                boltPath.lineTo(stub.midX, stub.midY)
                boltPath.lineTo(stub.x2, stub.y2)
                scope.drawPath(
                    boltPath,
                    Color(Palette.withAlpha(stub.color, 120)),
                    style = burnBoltStroke
                )
            }
            scope.drawCircle(Color(Palette.withAlpha(secondary, 160)), radius * 0.18f, Offset(cx, cy))
        }
    }
}
