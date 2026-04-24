package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class FireSkin(override val theme: ColorTheme) : PuckSkin {

    override val zIndex = 0

    private val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val orbRadius = renderer.radius * 0.6f

        fillPaint.color = theme.main.secondary
        fillPaint.strokeWidth = renderer.strokePaint.strokeWidth
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, fillPaint)

        fillPaint.color = theme.main.primary
        canvas.drawCircle(renderer.x, renderer.y, orbRadius, fillPaint)
    }
}
