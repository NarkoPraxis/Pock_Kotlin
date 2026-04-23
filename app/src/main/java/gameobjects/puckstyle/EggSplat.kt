package gameobjects.puckstyle

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import gameobjects.Settings
import utility.PaintBucket
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
    private val blobPath = Path()
    private val tempPath = Path()
    private val matrix   = Matrix()

    init {
        blobs = List(3) { i ->
            val baseAngle = i * 120f + Random.nextFloat() * 50f - 25f
            val angleRad  = (baseAngle * Math.PI / 180.0).toFloat()
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

    fun draw(canvas: Canvas, paint: Paint) {
        when {
            frame <= 35 -> drawSplat(canvas, paint, 255)
            frame > 35 -> {
                val alpha = (255 * (1f - (frame - 35f) / 30f)).toInt().coerceIn(100, 255)
                drawSplat(canvas, paint, alpha)
            }
        }
    }

    private fun drawSplat(canvas: Canvas, paint: Paint, alpha: Int) {
        paint.style = Paint.Style.FILL

        // Union of three randomly-placed overlapping ovals → single organic boundary
        blobPath.reset()
        for ((i, blob) in blobs.withIndex()) {
            tempPath.reset()
            tempPath.addOval(RectF(-blob.rx, -blob.ry, blob.rx, blob.ry), Path.Direction.CW)
            matrix.reset()
            matrix.setRotate(blob.rot)
            matrix.postTranslate(cx + blob.dx, cy + blob.dy)
            tempPath.transform(matrix)
            if (i == 0) blobPath.set(tempPath) else blobPath.op(tempPath, Path.Op.UNION)
        }

        paint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawPath(blobPath, paint)

        // Yolk — large, centered, no growth animation
        val yolkR = radius * 0.61f
        paint.color = Palette.withAlpha(theme.primary, alpha)
        canvas.drawCircle(cx, cy, yolkR, paint)
    }
}
