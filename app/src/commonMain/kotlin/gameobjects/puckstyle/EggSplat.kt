package gameobjects.puckstyle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    val theme: ColorTheme
) {
    private var frame = 0
    val isDone get() = false

    private data class Blob(val dx: Float, val dy: Float, val rx: Float, val ry: Float, val rot: Float)
    private val blobs: List<Blob>

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
        // Draw three overlapping ovals (no Path.Op.UNION in Compose — overlapping produces same visual)
        val white = Color(Palette.withAlpha(Palette.WHITE, alpha))
        for (blob in blobs) {
            scope.withTransform({
                rotate(blob.rot, pivot = Offset(cx + blob.dx, cy + blob.dy))
            }) {
                drawOval(
                    color = white,
                    topLeft = Offset(cx + blob.dx - blob.rx, cy + blob.dy - blob.ry),
                    size = Size(blob.rx * 2, blob.ry * 2)
                )
            }
        }

        // Yolk
        val yolkR = radius * 0.61f
        scope.drawCircle(
            color = Color(Palette.withAlpha(theme.main.primary, alpha)),
            radius = yolkR,
            center = Offset(cx, cy)
        )
    }
}
