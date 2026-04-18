package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class SpinnerSkin(override val theme: ColorTheme) : PuckSkin {

    private val baseFill = Paint().apply { color = theme.primary; isAntiAlias = true; style = Paint.Style.FILL }
    private val arm = Paint().apply { color = theme.secondary; isAntiAlias = true; style = Paint.Style.FILL }
    private val rim = Paint().apply { color = theme.secondary; isAntiAlias = true; style = Paint.Style.STROKE }

    private val path = Path()
    private var rotation = 0f

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val mag = kotlin.math.sqrt(
            (renderer.movementDirX * renderer.movementPower).let { it * it } +
            (renderer.movementDirY * renderer.movementPower).let { it * it }
        )
        rotation += (mag * 0.5f + 1.5f)
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, baseFill)
        rim.strokeWidth = renderer.strokePaint.strokeWidth * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, rim)

        canvas.save()
        canvas.translate(renderer.x, renderer.y)
        canvas.rotate(rotation)
        val armCount = 4
        for (i in 0 until armCount) {
            canvas.save()
            canvas.rotate(360f / armCount * i)
            path.reset()
            path.moveTo(0f, 0f)
            path.quadTo(renderer.radius * 0.45f, renderer.radius * 0.15f, renderer.radius * 0.9f, 0f)
            path.quadTo(renderer.radius * 0.45f, -renderer.radius * 0.15f, 0f, 0f)
            path.close()
            canvas.drawPath(path, arm)
            canvas.restore()
        }
        canvas.restore()
    }
}
