package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class CatTail(override val renderer: PuckRenderer) : TailRenderer {

    private val segmentCount = 15
    private val segmentSpacing get() = renderer.radius * 0.28f

    private val segX = FloatArray(segmentCount)
    private val segY = FloatArray(segmentCount)
    private val segAngle = FloatArray(segmentCount)
    private var initialized = false

    private var curlBias = 0f
    private var curlPhase = 0f

    private val tailPath = Path()

    override val zIndex: Int get() = -1

    override fun render(scope: DrawScope) {
        val r = renderer.radius
        val colors = responsiveGroup
        val spacing = segmentSpacing

        if (!initialized) {
            fillTo(renderer.x, renderer.y)
            initialized = true
        }

        // Curl oscillation
        curlPhase += 0.015f
        curlBias = sin(curlPhase) * 0.35f

        // Target position is behind the ball
        val targetX = renderer.x
        val targetY = renderer.y

        // First segment follows ball center
        segX[0] = targetX
        segY[0] = targetY
        segAngle[0] = atan2(segY[0] - (if (segmentCount > 1) segY[1] else segY[0]),
            segX[0] - (if (segmentCount > 1) segX[1] else segX[0]))

        // Spine physics: each segment follows the previous one
        for (i in 1 until segmentCount) {
            val t = i.toFloat() / (segmentCount - 1)
            val stiffness = lerp(0.14f, 0.08f, t)
            val damping = lerp(0.82f, 0.70f, t)

            val dx = segX[i] - segX[i - 1]
            val dy = segY[i] - segY[i - 1]
            val dist = kotlin.math.hypot(dx, dy).coerceAtLeast(0.001f)
            val targetDist = spacing

            val nx = dx / dist
            val ny = dy / dist

            // Spring toward ideal distance from previous segment
            val diff = dist - targetDist
            segX[i] -= nx * diff * stiffness
            segY[i] -= ny * diff * stiffness

            // Damping
            segX[i] = segX[i - 1] + (segX[i] - segX[i - 1]) * damping + nx * targetDist * (1f - damping)
            segY[i] = segY[i - 1] + (segY[i] - segY[i - 1]) * damping + ny * targetDist * (1f - damping)

            segAngle[i] = atan2(segY[i] - segY[i - 1], segX[i] - segX[i - 1])
        }

        // Apply curl bias to last 4 segments
        for (i in segmentCount - 4 until segmentCount) {
            val curlStrength = (i - (segmentCount - 4)).toFloat() / 4f
            val curlAngle = curlStrength * curlBias
            val prevX = segX[i - 1]
            val prevY = segY[i - 1]
            val curAngle = segAngle[i] + curlAngle
            segX[i] = prevX + cos(curAngle) * spacing
            segY[i] = prevY + sin(curAngle) * spacing
            segAngle[i] = curAngle
        }

        // Build tail shape as a filled path
        val fadeMultiplier = Settings.tailLengthMultiplier.coerceIn(0.1f, 2f)
        val baseWidth = r * 0.35f
        val tipWidth = r * 0.08f

        tailPath.reset()

        // Build left and right edges
        val leftX = FloatArray(segmentCount)
        val leftY = FloatArray(segmentCount)
        val rightX = FloatArray(segmentCount)
        val rightY = FloatArray(segmentCount)

        for (i in 0 until segmentCount) {
            val t = i.toFloat() / (segmentCount - 1)
            val width = lerp(baseWidth, tipWidth, t) * 0.5f
            val perpAngle = segAngle[i] + PI.toFloat() / 2f
            val px = cos(perpAngle) * width
            val py = sin(perpAngle) * width
            leftX[i] = segX[i] + px
            leftY[i] = segY[i] + py
            rightX[i] = segX[i] - px
            rightY[i] = segY[i] - py
        }

        // Draw filled tail
        tailPath.moveTo(leftX[0], leftY[0])
        for (i in 1 until segmentCount) {
            tailPath.lineTo(leftX[i], leftY[i])
        }
        tailPath.lineTo(segX[segmentCount - 1], segY[segmentCount - 1])
        for (i in segmentCount - 1 downTo 0) {
            tailPath.lineTo(rightX[i], rightY[i])
        }
        tailPath.close()

        val alpha = (255f * fadeMultiplier.coerceAtMost(1f)).toInt()
        val tailColor = Color(Palette.withAlpha(colors.secondary, alpha))
        scope.drawPath(tailPath, tailColor, style = Fill)
    }

    override fun clear() {
        initialized = false
    }

    override fun fillTo(x: Float, y: Float) {
        for (i in 0 until segmentCount) {
            segX[i] = x
            segY[i] = y + segmentSpacing * (i + 1)
            segAngle[i] = PI.toFloat() / 2f
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
