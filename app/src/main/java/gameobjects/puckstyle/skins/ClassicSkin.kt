package gameobjects.puckstyle.skins

import android.graphics.Canvas
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckSkin

class ClassicSkin(override val theme: ColorTheme) : PuckSkin {
    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        canvas.drawCircle(puck.x, puck.y, radius, puck.fillPaint)
        canvas.drawCircle(puck.x, puck.y, radius, puck.strokePaint)
    }
}
