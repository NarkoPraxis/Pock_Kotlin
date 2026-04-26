package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class PixelSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {
    private val fill = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val stroke = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE }

    override fun drawBody(canvas: Canvas) {
        val colors = resolvedColors()
        fill.color = colors.primary
        stroke.color = colors.secondary
        val side = renderer.radius * 1.7f
        val left = renderer.x - side / 2f
        val top = renderer.y - side / 2f
        canvas.drawRect(left, top, left + side, top + side, fill)
        stroke.strokeWidth = renderer.strokePaint.strokeWidth
        canvas.drawRect(left, top, left + side, top + side, stroke)
    }
}
