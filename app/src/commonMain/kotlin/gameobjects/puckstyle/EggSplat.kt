package gameobjects.puckstyle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class EggSplat(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    // Yolk colour, baked at spawn (rainbow-resolved when the puck was strobing).
    val yolkColor: Int
) {
    private var frame = 0
    val isDone get() = false

    private data class Blob(val dx: Float, val dy: Float, val rx: Float, val ry: Float, val rot: Float)
    private val blobs: List<Blob>
    private val whitePath: Path

    private val yolkDx: Float
    private val yolkDy: Float
    private val yolkRx: Float
    private val yolkRy: Float
    private val yolkRot: Float

    init {
        blobs = List(3) { i ->
            val baseAngle = i * 120f + Random.nextFloat() * 50f - 25f
            val angleRad  = (baseAngle * PI / 180.0).toFloat()
            Blob(
                dx  = cos(angleRad) * radius * 0.56f,
                dy  = sin(angleRad) * radius * 0.56f,
                rx  = radius * (1.16f + Random.nextFloat() * 0.28f),
                ry  = radius * (0.80f + Random.nextFloat() * 0.24f),
                rot = Random.nextFloat() * 180f
            )
        }

        whitePath = Path()
        for ((i, blob) in blobs.withIndex()) {
            val temp = Path().apply {
                addOval(Rect(-blob.rx, -blob.ry, blob.rx, blob.ry))
            }
            val m = Matrix().apply {
                rotateZ(blob.rot)
            }
            temp.transform(m)
            temp.translate(Offset(cx + blob.dx, cy + blob.dy))
            if (i == 0) {
                whitePath.addPath(temp)
            } else {
                whitePath.op(whitePath, temp, PathOperation.Union)
            }
        }

        val baseR = radius * 0.61f
        val offsetMax = baseR * 0.18f
        yolkDx = (Random.nextFloat() * 2f - 1f) * offsetMax
        yolkDy = (Random.nextFloat() * 2f - 1f) * offsetMax
        yolkRx = baseR * (0.92f + Random.nextFloat() * 0.16f)
        yolkRy = baseR * (0.92f + Random.nextFloat() * 0.16f)
        yolkRot = Random.nextFloat() * 360f
    }

    fun step() { frame++ }

    fun draw(scope: DrawScope) {
        val alpha = when {
            frame <= 35 -> 255
            else -> (255 * (1f - (frame - 35f) / 30f)).toInt().coerceIn(100, 255)
        }
        drawSplat(scope, alpha)
    }

    private fun drawSplat(scope: DrawScope, alpha: Int) {
        scope.drawPath(
            path = whitePath,
            color = Color(Palette.withAlpha(Palette.WHITE, alpha))
        )

        val yolkCx = cx + yolkDx
        val yolkCy = cy + yolkDy
        scope.withTransform({ rotate(yolkRot, pivot = Offset(yolkCx, yolkCy)) }) {
            drawOval(
                color = Color(Palette.withAlpha(yolkColor, alpha)),
                topLeft = Offset(yolkCx - yolkRx, yolkCy - yolkRy),
                size = Size(yolkRx * 2, yolkRy * 2)
            )
        }
    }
}
