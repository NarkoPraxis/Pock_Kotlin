package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class ChickenTail(override val renderer: PuckRenderer) : TailRenderer {

    private class Footprint(val x: Float, val y: Float, val angle: Float, var alpha: Int = 180)
    private val footprints = mutableListOf<Footprint>()
    private var stepTimer = 0
    private var stepSide = 1f

    private class Feather(
        var x: Float, var y: Float,
        val driftX: Float, val driftY: Float,
        var angle: Float, val spin: Float,
        var alpha: Int,
        val colorMix: Float
    )
    private val feathers = mutableListOf<Feather>()
    private var featherSpawnTimer = 0

    private var prevX = Float.NaN
    private var prevY = Float.NaN
    private var travelAngle = 0f

    private val footPath = Path()

    private var cachedRadius = -1f
    private var cachedFw = 0f
    private var cachedFh = 0f

    private fun ensureCache(r: Float) {
        if (cachedRadius != r) {
            cachedRadius = r
            cachedFw = r * 0.2f
            cachedFh = r * 0.55f
        }
    }

    private companion object {
        val DEG35_RAD = (35.0 * PI / 180.0).toFloat()
        val PI_F      = PI.toFloat()
    }

    override val zIndex: Int get() = -1

    override fun render(scope: DrawScope) {
        if (renderer.staticUiMode) { renderStatic(scope); return }
        val r = renderer.radius
        ensureCache(r)
        val fw = cachedFw
        val fh = cachedFh
        val colors = responsiveGroup

        var speed = 0f
        if (!prevX.isNaN()) {
            val dx = renderer.x - prevX
            val dy = renderer.y - prevY
            speed = hypot(dx, dy)
            if (speed > 0.001f) travelAngle = atan2(dy, dx)
        }
        prevX = renderer.x
        prevY = renderer.y

        // Layer 1: footsteps
        stepTimer++
        if (stepTimer >= 14 && speed > r * 0.01f) {
            stepTimer = 0
            val perpX = -sin(travelAngle) * r * 0.4f * stepSide
            val perpY =  cos(travelAngle) * r * 0.4f * stepSide
            footprints += Footprint(renderer.x + perpX, renderer.y + perpY, travelAngle)
            stepSide = -stepSide
        }
        val fadeStep = (3f / Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        var fi = footprints.size - 1
        while (fi >= 0) {
            val f = footprints[fi]
            f.alpha -= fadeStep
            if (f.alpha <= 0) { footprints.removeAt(fi); fi--; continue }
            val strokeColor = Color(Palette.withAlpha(colors.secondary, f.alpha))
            val sw = r * 0.13f
            val toeLen = r * 0.55f
            val faceAngle = f.angle
            val rearAngle = faceAngle + PI_F
            footPath.reset()
            footPath.moveTo(f.x, f.y)
            footPath.lineTo(f.x + cos(faceAngle) * toeLen, f.y + sin(faceAngle) * toeLen)
            footPath.moveTo(f.x, f.y)
            footPath.lineTo(f.x + cos(faceAngle + DEG35_RAD) * toeLen, f.y + sin(faceAngle + DEG35_RAD) * toeLen)
            footPath.moveTo(f.x, f.y)
            footPath.lineTo(f.x + cos(faceAngle - DEG35_RAD) * toeLen, f.y + sin(faceAngle - DEG35_RAD) * toeLen)
            footPath.moveTo(f.x, f.y)
            footPath.lineTo(f.x + cos(rearAngle) * toeLen * 0.6f, f.y + sin(rearAngle) * toeLen * 0.6f)
            scope.drawPath(footPath, strokeColor, style = Stroke(width = sw, cap = StrokeCap.Round))
            fi--
        }

        // Layer 2: feather particles
        featherSpawnTimer++
        if (featherSpawnTimer >= 2 && speed > r * 0.005f && feathers.size < (55 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)) {
            featherSpawnTimer = 0
            feathers += Feather(
                x = renderer.x + (Random.nextFloat() - 0.5f) * r * 2.0f,
                y = renderer.y + (Random.nextFloat() - 0.5f) * r * 2.0f,
                driftX = (Random.nextFloat() - 0.5f) * 0.8f,
                driftY = -Random.nextFloat() * 0.6f - 0.1f,
                angle = Random.nextFloat() * 360f,
                spin  = (Random.nextFloat() - 0.5f) * 4f,
                alpha = 220,
                colorMix = Random.nextFloat()
            )
        }
        val featherFadeStep = (5f / Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        var fIdx = feathers.size - 1
        while (fIdx >= 0) {
            val f = feathers[fIdx]
            f.x += f.driftX
            f.y += f.driftY
            f.angle += f.spin
            f.alpha -= featherFadeStep
            if (f.alpha <= 0) { feathers.removeAt(fIdx); fIdx--; continue }
            val c = Palette.lerpColor(colors.primary, colors.secondary, f.colorMix)
            scope.withTransform({
                rotate(f.angle, pivot = Offset(f.x, f.y))
            }) {
                drawOval(
                    color = Color(Palette.withAlpha(c, f.alpha)),
                    topLeft = Offset(f.x - fw, f.y - fh),
                    size = Size(fw * 2, fh * 2)
                )
                drawLine(
                    color = Color(Palette.withAlpha(colors.secondary, f.alpha)),
                    start = Offset(f.x, f.y + fh * 1.3f),
                    end = Offset(f.x, f.y - fh * 0.7f),
                    strokeWidth = fw * 0.35f,
                    cap = StrokeCap.Round
                )
            }
            fIdx--
        }
    }

    /** Frozen "screenshot of motion": footsteps walking the swoosh, with a few drifting feathers. */
    private fun renderStatic(scope: DrawScope) {
        val r = renderer.radius
        ensureCache(r)
        val fw = cachedFw
        val fh = cachedFh
        val colors = responsiveGroup

        // Footprints walking along the swoosh, alternating sides, fading toward the tip.
        val steps = 7
        var side = 1f
        val sw = r * 0.13f
        val toeLen = r * 0.55f
        for (s in 0 until steps) {
            val ratio = (s + 0.5f) / steps
            val base = staticSwooshPoint(ratio)
            val ahead = staticSwooshPoint((ratio + 0.02f).coerceAtMost(1f))
            val ang = atan2(ahead.y - base.y, ahead.x - base.x)
            val fx = base.x + -sin(ang) * r * 0.4f * side
            val fy = base.y + cos(ang) * r * 0.4f * side
            side = -side
            val alpha = (180f * (1f - ratio)).toInt().coerceAtLeast(20)
            val strokeColor = Color(Palette.withAlpha(colors.secondary, alpha))
            val rearAngle = ang + PI_F
            footPath.reset()
            footPath.moveTo(fx, fy); footPath.lineTo(fx + cos(ang) * toeLen, fy + sin(ang) * toeLen)
            footPath.moveTo(fx, fy); footPath.lineTo(fx + cos(ang + DEG35_RAD) * toeLen, fy + sin(ang + DEG35_RAD) * toeLen)
            footPath.moveTo(fx, fy); footPath.lineTo(fx + cos(ang - DEG35_RAD) * toeLen, fy + sin(ang - DEG35_RAD) * toeLen)
            footPath.moveTo(fx, fy); footPath.lineTo(fx + cos(rearAngle) * toeLen * 0.6f, fy + sin(rearAngle) * toeLen * 0.6f)
            scope.drawPath(footPath, strokeColor, style = Stroke(width = sw, cap = StrokeCap.Round))
        }

        // A few drifting feathers near the ball.
        for (f in 0 until 6) {
            val rnd = Random(f + 1)
            val base = staticSwooshPoint(rnd.nextFloat() * 0.6f)
            val fx = base.x + (rnd.nextFloat() - 0.5f) * r * 2f
            val fy = base.y + (rnd.nextFloat() - 0.5f) * r * 2f
            val angle = rnd.nextFloat() * 360f
            val c = Palette.lerpColor(colors.primary, colors.secondary, rnd.nextFloat())
            scope.withTransform({ rotate(angle, pivot = Offset(fx, fy)) }) {
                drawOval(
                    color = Color(Palette.withAlpha(c, 200)),
                    topLeft = Offset(fx - fw, fy - fh),
                    size = Size(fw * 2, fh * 2)
                )
                drawLine(
                    color = Color(Palette.withAlpha(colors.secondary, 200)),
                    start = Offset(fx, fy + fh * 1.3f),
                    end = Offset(fx, fy - fh * 0.7f),
                    strokeWidth = fw * 0.35f,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    override fun clear() {
        footprints.clear()
        feathers.clear()
        prevX = Float.NaN
        prevY = Float.NaN
        stepTimer = 0
        featherSpawnTimer = 0
    }

    override fun fillTo(x: Float, y: Float) {
        prevX = x
        prevY = y
    }
}
