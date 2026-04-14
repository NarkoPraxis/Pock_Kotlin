package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckSkin

class PixelSkin(override val theme: ColorTheme) : PuckSkin {
    private val fill = Paint().apply { color = theme.primary; isAntiAlias = false; style = Paint.Style.FILL }
    private val stroke = Paint().apply { color = theme.secondary; isAntiAlias = false; style = Paint.Style.STROKE }

    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        val side = radius * 1.7f
        val left = puck.x - side / 2f
        val top = puck.y - side / 2f
        canvas.drawRect(left, top, left + side, top + side, fill)
        stroke.strokeWidth = puck.strokePaint.strokeWidth
        canvas.drawRect(left, top, left + side, top + side, stroke)
    }
}
