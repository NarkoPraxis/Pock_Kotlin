package gameobjects.puckstyle

import android.graphics.Canvas
import gameobjects.Puck

interface PuckSkin {
    val theme: ColorTheme
    fun drawBody(canvas: Canvas, puck: Puck, radius: Float)
}
