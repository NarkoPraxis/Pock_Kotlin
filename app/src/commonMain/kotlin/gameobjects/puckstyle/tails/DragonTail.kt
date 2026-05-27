package gameobjects.puckstyle.tails

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import gameobjects.Settings
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class DragonTail(override val renderer: PuckRenderer) : TailRenderer {

    private val SEGMENT_COUNT = 14
    private val SEGMENT_SPACING_K = 0.3f
    private val BASE_STIFFNESS = 0.18f
    private val TIP_STIFFNESS = 0.10f
    private val MAX_ANGLE_DIFF = 30f * (PI.toFloat() / 180f)

    private val spineX = FloatArray(SEGMENT_COUNT)
    private val spineY = FloatArray(SEGMENT_COUNT)
    private var initialized = false

    private val bodyPath = Path()
    private val spikePath = Path()

    override val zIndex: Int get() = -1

    override fun render(scope: DrawScope) {
        val r = renderer.radius
        val colors = responsiveGroup

        if (!initialized) {
            for (i in 0 until SEGMENT_COUNT) {
                spineX[i] = renderer.x
                spineY[i] = renderer.y + r * SEGMENT_SPACING_K * (i + 1)
            }
            initialized = true
        }

        val headX = renderer.x
        val headY = renderer.y

        var prevX = headX
        var prevY = headY
        for (i in 0 until SEGMENT_COUNT) {
            val t = i.toFloat() / (SEGMENT_COUNT - 1)
            val stiffness = lerp(BASE_STIFFNESS, TIP_STIFFNESS, t)
            spineX[i] = lerp(spineX[i], prevX, stiffness)
            spineY[i] = lerp(spineY[i], prevY, stiffness)

            val spacing = r * SEGMENT_SPACING_K
            val dx = spineX[i] - prevX
            val dy = spineY[i] - prevY
            val dist = hypot(dx, dy).coerceAtLeast(0.001f)
            spineX[i] = prevX + dx / dist * spacing
            spineY[i] = prevY + dy / dist * spacing

            if (i > 0) {
                val prevAngle = atan2(spineY[i - 1] - if (i > 1) spineY[i - 2] else headY,
                    spineX[i - 1] - if (i > 1) spineX[i - 2] else headX)
                val curAngle = atan2(spineY[i] - spineY[i - 1], spineX[i] - spineX[i - 1])
                var angleDiff = curAngle - prevAngle
                while (angleDiff > PI) angleDiff -= 2f * PI.toFloat()
                while (angleDiff < -PI) angleDiff += 2f * PI.toFloat()
                if (angleDiff > MAX_ANGLE_DIFF) {
                    val clamped = prevAngle + MAX_ANGLE_DIFF
                    spineX[i] = spineX[i - 1] + cos(clamped) * spacing
                    spineY[i] = spineY[i - 1] + sin(clamped) * spacing
                } else if (angleDiff < -MAX_ANGLE_DIFF) {
                    val clamped = prevAngle - MAX_ANGLE_DIFF
                    spineX[i] = spineX[i - 1] + cos(clamped) * spacing
                    spineY[i] = spineY[i - 1] + sin(clamped) * spacing
                }
            }

            prevX = spineX[i]
            prevY = spineY[i]
        }

        val leftX = FloatArray(SEGMENT_COUNT + 1)
        val leftY = FloatArray(SEGMENT_COUNT + 1)
        val rightX = FloatArray(SEGMENT_COUNT + 1)
        val rightY = FloatArray(SEGMENT_COUNT + 1)

        for (i in 0..SEGMENT_COUNT - 1) {
            val sx: Float
            val sy: Float
            val ex: Float
            val ey: Float
            if (i == 0) {
                sx = headX; sy = headY
                ex = spineX[0]; ey = spineY[0]
            } else {
                sx = spineX[i - 1]; sy = spineY[i - 1]
                ex = spineX[i]; ey = spineY[i]
            }
            val dx = ex - sx
            val dy = ey - sy
            val len = hypot(dx, dy).coerceAtLeast(0.001f)
            val nx = -dy / len
            val ny = dx / len
            val t = i.toFloat() / SEGMENT_COUNT
            val w = widthAtRatio(t, r) * 0.5f * Settings.tailLengthMultiplier.coerceAtMost(1.5f)
            if (i == 0) {
                leftX[0] = headX + nx * w
                leftY[0] = headY + ny * w
                rightX[0] = headX - nx * w
                rightY[0] = headY - ny * w
            }
            leftX[i + 1] = if (i < SEGMENT_COUNT) spineX[i] + nx * widthAtRatio((i + 1f) / SEGMENT_COUNT, r) * 0.5f else ex + nx * w
            leftY[i + 1] = if (i < SEGMENT_COUNT) spineY[i] + ny * widthAtRatio((i + 1f) / SEGMENT_COUNT, r) * 0.5f else ey + ny * w
            rightX[i + 1] = if (i < SEGMENT_COUNT) spineX[i] - nx * widthAtRatio((i + 1f) / SEGMENT_COUNT, r) * 0.5f else ex - nx * w
            rightY[i + 1] = if (i < SEGMENT_COUNT) spineY[i] - ny * widthAtRatio((i + 1f) / SEGMENT_COUNT, r) * 0.5f else ey - ny * w
        }

        bodyPath.reset()
        bodyPath.moveTo(leftX[0], leftY[0])
        for (i in 1..SEGMENT_COUNT) {
            bodyPath.lineTo(leftX[i], leftY[i])
        }
        for (i in SEGMENT_COUNT downTo 0) {
            bodyPath.lineTo(rightX[i], rightY[i])
        }
        bodyPath.close()
        scope.drawPath(bodyPath, Color(colors.primary), style = Fill)

        val tipIdx = SEGMENT_COUNT - 1
        val tipX = spineX[tipIdx]
        val tipY = spineY[tipIdx]
        val preTipX = spineX[tipIdx - 1]
        val preTipY = spineY[tipIdx - 1]
        val tipDx = tipX - preTipX
        val tipDy = tipY - preTipY
        val tipLen = hypot(tipDx, tipDy).coerceAtLeast(0.001f)
        val tipNx = tipDx / tipLen
        val tipNy = tipDy / tipLen
        val perpNx = -tipNy
        val perpNy = tipNx

        val spikeLen = r * 0.6f
        val spikeSpread = r * 0.3f

        spikePath.reset()
        spikePath.moveTo(tipX, tipY)
        spikePath.lineTo(
            tipX + tipNx * spikeLen + perpNx * spikeSpread,
            tipY + tipNy * spikeLen + perpNy * spikeSpread
        )
        spikePath.lineTo(tipX + tipNx * spikeLen * 0.4f, tipY + tipNy * spikeLen * 0.4f)
        spikePath.lineTo(
            tipX + tipNx * spikeLen - perpNx * spikeSpread,
            tipY + tipNy * spikeLen - perpNy * spikeSpread
        )
        spikePath.close()
        scope.drawPath(spikePath, Color(colors.secondary), style = Fill)
    }

    private fun widthAtRatio(t: Float, r: Float): Float {
        val baseWidth = r * 0.3f
        val maxWidth = r * 0.7f
        return if (t < 0.5f) {
            lerp(baseWidth, maxWidth, t * 2f)
        } else {
            lerp(maxWidth, baseWidth * 0.6f, (t - 0.5f) * 2f)
        }
    }

    override fun clear() {
        initialized = false
    }

    override fun fillTo(x: Float, y: Float) {
        for (i in 0 until SEGMENT_COUNT) {
            spineX[i] = x
            spineY[i] = y
        }
        initialized = true
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
