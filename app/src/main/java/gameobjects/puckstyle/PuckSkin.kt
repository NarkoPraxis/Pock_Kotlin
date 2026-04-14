package gameobjects.puckstyle

import android.graphics.Canvas
import gameobjects.Puck
import utility.PaintBucket

interface PuckSkin {
    val theme: ColorTheme
    fun drawBody(canvas: Canvas, puck: Puck, radius: Float)

    // Plan 03: wrapper that replaces the skin body with a solid dark-grey circle for locked previews
    fun draw(canvas: Canvas, puck: Puck, radius: Float) {
        if (puck.isPlaceholder) {
            canvas.drawCircle(puck.x, puck.y, radius, PaintBucket.placeholderPaint)
        } else {
            drawBody(canvas, puck, radius)
        }
    }
}
