package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class SpinnerSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

    private val baseFill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val rim = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    override fun drawBody(canvas: Canvas) {
        val colors = resolvedColors()
        baseFill.color = colors.primary
        rim.color = colors.secondary
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, baseFill)
        rim.strokeWidth = renderer.strokePaint.strokeWidth * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, rim)
    }
}
