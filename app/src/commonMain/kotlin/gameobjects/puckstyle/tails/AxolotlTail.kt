package gameobjects.puckstyle.tails

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class AxolotlTail(override val renderer: PuckRenderer) : TailRenderer {

    private val segCount = 16
    private val xs = FloatArray(segCount)
    private val ys = FloatArray(segCount)
    private val angles = FloatArray(segCount)
    private val stiffness = FloatArray(segCount) { i ->
        lerp(0.12f, 0.08f, i.toFloat() / (segCount - 1))
    }
    private val maxBend = 40f * (PI.toFloat() / 180f)

    private var prevX = Float.NaN
    private var prevY = Float.NaN

    private val bodyPath = Path()

    override val zIndex: Int get() = -1

    override fun render(scope: DrawScope) {
        val r = renderer.radius
        val colors = responsiveGroup
        val spacing = r * 0.25f

        if (prevX.isNaN()) {
            initSpine(renderer.x, renderer.y, r, spacing)
            prevX = renderer.x; prevY = renderer.y
            return
        }

        val headAngle = atan2(renderer.y - prevY, renderer.x - prevX)
        prevX = renderer.x; prevY = renderer.y

        xs[0] = renderer.x - cos(headAngle) * r * 0.8f
        ys[0] = renderer.y - sin(headAngle) * r * 0.8f
        angles[0] = headAngle + PI.toFloat()

        for (i in 1 until segCount) {
            val targetAngle = atan2(ys[i] - ys[i - 1], xs[i] - xs[i - 1])
            var diff = targetAngle - angles[i - 1]
            while (diff > PI.toFloat()) diff -= 2f * PI.toFloat()
            while (diff < -PI.toFloat()) diff += 2f * PI.toFloat()
            diff = diff.coerceIn(-maxBend, maxBend)
            angles[i] = lerp(angles[i], angles[i - 1] + diff, stiffness[i])
            xs[i] = xs[i - 1] + cos(angles[i]) * spacing
            ys[i] = ys[i - 1] + sin(angles[i]) * spacing
        }

        val fadeStep = (3f / Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
        drawTailBody(scope, r, colors.secondary, colors.primary, fadeStep)
    }

    private fun drawTailBody(scope: DrawScope, r: Float, secondaryColor: Int, primaryColor: Int, fadeStep: Int) {
        bodyPath.reset()

        val leftXs = FloatArray(segCount)
        val leftYs = FloatArray(segCount)
        val rightXs = FloatArray(segCount)
        val rightYs = FloatArray(segCount)

        for (i in 0 until segCount) {
            val t = i.toFloat() / (segCount - 1)
            val w = widthAtRatio(t, r) * 0.5f
            val perpAngle = angles[i] + PI.toFloat() * 0.5f
            val cosP = cos(perpAngle)
            val sinP = sin(perpAngle)
            leftXs[i] = xs[i] + cosP * w
            leftYs[i] = ys[i] + sinP * w
            rightXs[i] = xs[i] - cosP * w
            rightYs[i] = ys[i] - sinP * w
        }

        bodyPath.moveTo(leftXs[0], leftYs[0])
        for (i in 1 until segCount) {
            bodyPath.lineTo(leftXs[i], leftYs[i])
        }
        bodyPath.lineTo(xs[segCount - 1], ys[segCount - 1])
        for (i in segCount - 1 downTo 0) {
            bodyPath.lineTo(rightXs[i], rightYs[i])
        }
        bodyPath.close()

        val baseAlpha = (200f * Settings.tailLengthMultiplier).toInt().coerceIn(80, 220)
        scope.drawPath(bodyPath, Color(Palette.withAlpha(secondaryColor, baseAlpha)))

        // Dorsal fin ridge along the top edge (last 60% of segments)
        val finStartIdx = (segCount * 0.4f).toInt()
        for (i in finStartIdx until segCount - 1) {
            val t = (i - finStartIdx).toFloat() / (segCount - 1 - finStartIdx)
            val finH = r * 0.15f * (1f - t)
            if (finH < r * 0.01f) continue
            val perpAngle = angles[i] + PI.toFloat() * 0.5f
            val tipX = leftXs[i] + cos(perpAngle) * finH
            val tipY = leftYs[i] + sin(perpAngle) * finH
            val alpha = (baseAlpha * (1f - t * 0.5f)).toInt().coerceIn(0, 255)
            scope.drawLine(
                Color(Palette.withAlpha(primaryColor, alpha)),
                start = androidx.compose.ui.geometry.Offset(leftXs[i], leftYs[i]),
                end = androidx.compose.ui.geometry.Offset(tipX, tipY),
                strokeWidth = r * 0.06f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
    }

    private fun widthAtRatio(t: Float, r: Float): Float {
        if (t < 0.25f) {
            return lerp(r * 0.8f, r * 0.5f, t / 0.25f)
        }
        val tailT = (t - 0.25f) / 0.75f
        val base = lerp(r * 0.5f, r * 0.15f, tailT)
        val wave = sin(tailT * PI.toFloat() * 3f) * r * 0.08f
        return base + wave
    }

    private fun initSpine(cx: Float, cy: Float, r: Float, spacing: Float) {
        val dirY = if (renderer.isHigh) -1f else 1f
        for (i in 0 until segCount) {
            xs[i] = cx
            ys[i] = cy + dirY * (r + spacing * i)
            angles[i] = if (renderer.isHigh) (-PI / 2.0).toFloat() else (PI / 2.0).toFloat()
        }
    }

    override fun clear() {
        prevX = Float.NaN
        prevY = Float.NaN
    }

    override fun fillTo(x: Float, y: Float) {
        val r = renderer.radius
        val spacing = r * 0.25f
        initSpine(x, y, r, spacing)
        prevX = x; prevY = y
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
