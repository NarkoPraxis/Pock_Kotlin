package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import androidx.core.graphics.withTranslation
import gameobjects.Settings
import gameobjects.puckstyle.Palette.hsv
import gameobjects.puckstyle.paddles.PrismLaunch
import physics.Point
import utility.Effects
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PrismSkin( override val renderer: PuckRenderer) : PuckSkin {

    private val facet = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val edge = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val path = Path()

    private val baseHue = Palette.themeHue(theme)
    private val hues = floatArrayOf(
        baseHue,
        baseHue + 40f,
        baseHue - 30f,
        baseHue + 20f,
        baseHue + 60f,
        baseHue - 15f
    )

    override fun drawBody(canvas: Canvas) {
        val sides = 6
        val angleOffset = renderer.frame * 0.8f
        val osc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        canvas.withTranslation(renderer.x, renderer.y) {
            rotate(angleOffset)

            for (i in 0 until sides) {
                val a1 = (i * 360f / sides) * Math.PI.toFloat() / 180f
                val a2 = ((i + 1) * 360f / sides) * Math.PI.toFloat() / 180f
                path.reset()
                path.moveTo(0f, 0f)
                path.lineTo(
                    kotlin.math.cos(a1) * renderer.radius,
                    kotlin.math.sin(a1) * renderer.radius
                )
                path.lineTo(
                    kotlin.math.cos(a2) * renderer.radius,
                    kotlin.math.sin(a2) * renderer.radius
                )
                path.close()
                facet.color = Palette.hsvThemed(hues[i % hues.size] + osc)

                if (renderer.shielded) {
                    val purpleCenter = if (baseHue > 180f) 290f else 270f
                    val purpleHue = purpleCenter + osc * 0.5f + (i - 2.5f) * 6f
                    facet.color = Palette.hsvThemed(purpleHue)
                } else if (renderer.isInert) {
                    facet.color = Palette.withAlpha(hsv( hues[i % hues.size], .10f, .90f), 255)
                }
                drawPath(path, facet)
            }
            edge.strokeWidth = renderer.strokePaint.strokeWidth
            edge.color = Palette.hsvHighlight(baseHue - osc)

            if (renderer.isInert) {
                edge.color = theme.inert.secondary
            }
            if (renderer.shielded) {
                edge.color = theme.shield.secondary
            }
            path.reset()
            for (i in 0 until sides) {
                val a = (i * 360f / sides) * Math.PI.toFloat() / 180f
                val px = kotlin.math.cos(a) * renderer.radius
                val py = kotlin.math.sin(a) * renderer.radius
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            drawPath(path, edge)
        }
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        val spawnRotDeg = renderer.frame * 0.8f
        val spawnOsc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        val baseHue = Palette.themeHue(theme)
        PrismLaunch.scatterTriangles(renderer.x, renderer.y, renderer.radius, spawnRotDeg, spawnOsc, baseHue)
    }

    override fun onShieldedCollision(position: Point) {
        val spawnRotDeg = renderer.frame * 0.8f
        val spawnOsc = kotlin.math.sin(renderer.frame * 0.04).toFloat() * 30f
        val baseHue = Palette.themeHue(theme)
        PrismLaunch.scatterTriangles(renderer.x, renderer.y, renderer.radius, spawnRotDeg, spawnOsc, baseHue)
    }

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        Effects.addPersistentEffect(PrismTriangleCelebration(position.x, position.y, renderer.radius, highGoal, fullCircle = false, baseHue))
    }

    private class PrismTriangleCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        fullCircle: Boolean,
        private val baseHue: Float
    ) : Effects.PersistentEffect {
        private val maxDistance = radius * 3f
        private val speed = maxDistance / 55f
        private val hueOffsets = floatArrayOf(0f, 40f, -30f, 20f, 60f, -15f, 10f, -20f, 50f, -10f, 35f, -40f)
        private val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        private val edgePaint = Paint().apply {
            isAntiAlias = true; style = Paint.Style.STROKE
            strokeWidth = Settings.strokeWidth * 0.4f; strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()
        private var frame = 0
        private var _isDone = false
        override val isDone: Boolean get() = _isDone

        private class Tri(val dirX: Float, val dirY: Float, val hue: Float, val size: Float) {
            var x = 0f; var y = 0f; var traveled = 0f; var alpha = 255; var done = false
        }
        private val tris: List<Tri>

        init {
            val baseAngles = listOf(0.0, .523599, 1.0472, 1.5708, 2.0944, 2.61799, Math.PI)
            val fullAngles = List(12) { i -> i * (2.0 * Math.PI / 12) }
            val srcAngles = if (fullCircle) fullAngles else baseAngles
            tris = srcAngles.mapIndexed { idx, a ->
                val adj = if (!fullCircle && !highGoal) a + Math.PI else a
                val af = adj.toFloat()
                Tri(cos(af), sin(af), baseHue + hueOffsets[idx % hueOffsets.size], radius * 0.42f)
            }.also { list -> list.forEach { it.x = cx; it.y = cy } }
        }

        override fun step() {
            frame++
            var allDone = true
            for (t in tris) {
                if (t.done) continue
                t.x += t.dirX * speed; t.y += t.dirY * speed; t.traveled += speed
                val ratio = (t.traveled / maxDistance).coerceIn(0f, 1f)
                t.alpha = (255 * (1f - ratio)).toInt().coerceIn(0, 255)
                if (t.traveled >= maxDistance) { t.alpha = 0; t.done = true }
                allDone = false
            }
            if (allDone) _isDone = true
        }

        override fun draw(canvas: Canvas) {
            for (t in tris) {
                if (t.done || t.alpha <= 0) continue
                val hue = t.hue + sin(frame * 0.04f) * 30f
                fillPaint.color = Palette.hsvThemed(hue); fillPaint.alpha = t.alpha
                edgePaint.color = Palette.hsvHighlight(hue); edgePaint.alpha = t.alpha
                val perpX = -t.dirY; val perpY = t.dirX; val s = t.size
                path.reset()
                path.moveTo(t.x + t.dirX * s, t.y + t.dirY * s)
                path.lineTo(t.x + perpX * s * 0.65f - t.dirX * s * 0.5f, t.y + perpY * s * 0.65f - t.dirY * s * 0.5f)
                path.lineTo(t.x - perpX * s * 0.65f - t.dirX * s * 0.5f, t.y - perpY * s * 0.65f - t.dirY * s * 0.5f)
                path.close()
                canvas.drawPath(path, fillPaint)
                canvas.drawPath(path, edgePaint)
            }
            fillPaint.alpha = 255; edgePaint.alpha = 255
        }
    }
}
