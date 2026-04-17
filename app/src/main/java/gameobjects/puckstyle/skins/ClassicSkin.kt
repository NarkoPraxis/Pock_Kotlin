package gameobjects.puckstyle.skins

import android.graphics.Canvas
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class ClassicSkin(override val theme: ColorTheme) : PuckSkin {
    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, renderer.fillPaint)
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, renderer.strokePaint)
    }
}
