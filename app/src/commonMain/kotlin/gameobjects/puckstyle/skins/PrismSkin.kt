package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.Palette.hsv
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.PrismLaunch
import physics.Point
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PrismSkin(override val renderer: PuckRenderer) : PuckSkin {

    private val path = Path()

    // Cached Stroke for the prism edge. renderer.strokeWidth is effectively immutable
    // after setup, but rebuild defensively if it ever changes.
    private var cachedEdgeSw = -1f
    private var edgeStroke = Stroke(width = 0f)
    private fun edgeStrokeFor(width: Float): Stroke {
        if (cachedEdgeSw != width) {
            cachedEdgeSw = width
            edgeStroke = Stroke(width = width)
        }
        return edgeStroke
    }

    private val baseHue = Palette.themeHue(theme)
    private val hues = floatArrayOf(
        baseHue,
        baseHue + 40f,
        baseHue - 30f,
        baseHue + 20f,
        baseHue + 60f,
        baseHue - 15f
    )

    override fun DrawScope.drawBody() {
        val sides = 6
        // Geometry (facet rotation) tracks frame and is frozen in a static UI preview so the prism
        // holds still; the hue oscillation tracks the strobe clock so the colors keep cycling there.
        // In live play strobe == frame, so the two are identical and gameplay is unchanged.
        val angleOffset = if (renderer.staticUiMode) 0f else renderer.frame * 0.8f
        val osc = kotlin.math.sin(renderer.strobe * 0.04).toFloat() * 30f
        val edgeSw = renderer.strokeWidth

        val angleOffsetRad = angleOffset * (PI.toFloat() / 180f)
        for (i in 0 until sides) {
            val a1 = (i * 360f / sides) * PI.toFloat() / 180f + angleOffsetRad
            val a2 = ((i + 1) * 360f / sides) * PI.toFloat() / 180f + angleOffsetRad
            path.reset()
            path.moveTo(renderer.x, renderer.y)
            path.lineTo(renderer.x + cos(a1) * renderer.radius, renderer.y + sin(a1) * renderer.radius)
            path.lineTo(renderer.x + cos(a2) * renderer.radius, renderer.y + sin(a2) * renderer.radius)
            path.close()

            val facetColor = when {
                renderer.shielded -> {
                    val purpleCenter = if (baseHue > 180f) 290f else 270f
                    Palette.hsvThemed(purpleCenter + osc * 0.5f + (i - 2.5f) * 6f)
                }
                renderer.isInert -> Palette.withAlpha(hsv(hues[i % hues.size], .10f, .90f), 255)
                else -> Palette.hsvThemed(hues[i % hues.size] + osc)
            }
            drawPath(path, Color(facetColor))
        }

        val edgeColor = when {
            renderer.isInert -> theme.inert.secondary
            renderer.shielded -> theme.shield.secondary
            else -> Palette.hsvHighlight(baseHue - osc)
        }
        path.reset()
        for (i in 0 until sides) {
            val a = (i * 360f / sides) * PI.toFloat() / 180f + angleOffsetRad
            val px = renderer.x + cos(a) * renderer.radius
            val py = renderer.y + sin(a) * renderer.radius
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        drawPath(path, Color(edgeColor), style = edgeStrokeFor(edgeSw))
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        val spawnRotDeg = renderer.frame * 0.8f
        val spawnOsc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        PrismLaunch.scatterTriangles(renderer.x, renderer.y, renderer.radius, spawnRotDeg, spawnOsc, baseHue)
    }

    override fun onShieldedCollision(position: Point) {
        val spawnRotDeg = renderer.frame * 0.8f
        val spawnOsc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        PrismLaunch.scatterTriangles(renderer.x, renderer.y, renderer.radius, spawnRotDeg, spawnOsc, baseHue)
    }

    override val explosionFrequency get() = 25
    override val scatterDensity get() = 1.1f

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(PrismTriangleCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = true, baseHue))
    }

    override fun onVictory(x: Float, y: Float) {
        Effects.addPersistentEffect(PrismTriangleCelebration(x, y, renderer.radius, highGoal = true, fullCircle = true, baseHue))
    }

    private class PrismTriangleCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean,
        private val baseHue: Float
    ) : Effects.PersistentEffect {
        private val maxDistance = radius * 5f
        private val speed = maxDistance / 40f
        private val hueOffsets = floatArrayOf(0f, 40f, -30f, 20f, 60f, -15f, 10f, -20f, 50f, -10f, 35f, -40f)
        private val edgeStrokeWidth = Settings.strokeWidth * 0.4f
        // edgeStrokeWidth is a fixed field, so the edge Stroke can be allocated once.
        private val edgeStroke = Stroke(width = edgeStrokeWidth)
        private val path = Path()
        private var frame = 0
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        private class Tri(val dirX: Float, val dirY: Float, val hue: Float, val size: Float) {
            var x = 0f; var y = 0f; var traveled = 0f; var alpha = 255; var done = false
        }
        // Stored as an Array so per-frame index loops in step()/draw() don't allocate an Iterator.
        private val tris: Array<Tri>

        init {
            val baseAngles = listOf(0.0, .523599, 1.0472, 2.0944, 2.61799, PI)
            val fullAngles = List(6) { i -> i * (2.0 * PI / 6) }
            val srcAngles = if (fullCircle) fullAngles else baseAngles
            tris = Array(srcAngles.size) { idx ->
                val a = srcAngles[idx]
                val adj = if (!fullCircle && !highGoal) a + PI else a
                val af = adj.toFloat()
                Tri(cos(af), sin(af), baseHue + hueOffsets[idx % hueOffsets.size], radius * 0.577f).also {
                    it.x = cx; it.y = cy
                }
            }
        }

        override fun step() {
            frame++
            var allDone = true
            for (i in tris.indices) {
                val t = tris[i]
                if (t.done) continue
                t.x += t.dirX * speed; t.y += t.dirY * speed; t.traveled += speed
                val ratio = (t.traveled / maxDistance).coerceIn(0f, 1f)
                t.alpha = (255 * (1f - ratio)).toInt().coerceIn(0, 255)
                if (t.traveled >= maxDistance) { t.alpha = 0; t.done = true }
                allDone = false
            }
            if (allDone) _isDone = true
        }

        override fun draw(scope: DrawScope) {
            for (i in tris.indices) {
                val t = tris[i]
                if (t.done || t.alpha <= 0) continue
                val hue = t.hue + sin(frame * 0.04f) * 30f
                val fillColor = Color(Palette.withAlpha(Palette.hsvThemed(hue), t.alpha))
                val edgeColor = Color(Palette.withAlpha(Palette.hsvHighlight(hue), t.alpha))
                val perpX = -t.dirY; val perpY = t.dirX; val s = t.size
                path.reset()
                path.moveTo(t.x - t.dirX * s, t.y - t.dirY * s)
                path.lineTo(t.x + perpX * s * 0.866f + t.dirX * s * 0.5f, t.y + perpY * s * 0.866f + t.dirY * s * 0.5f)
                path.lineTo(t.x - perpX * s * 0.866f + t.dirX * s * 0.5f, t.y - perpY * s * 0.866f + t.dirY * s * 0.5f)
                path.close()
                scope.drawPath(path, fillColor)
                scope.drawPath(path, edgeColor, style = edgeStroke)
            }
        }
    }
}
