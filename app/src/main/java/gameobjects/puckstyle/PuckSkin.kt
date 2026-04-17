package gameobjects.puckstyle

import android.graphics.Canvas

interface PuckSkin {
    val theme: ColorTheme

    /** Local z-index within a PuckRenderer composition. Default 0 (body layer). */
    val zIndex: Int get() = 0

    fun drawBody(canvas: Canvas, renderer: PuckRenderer)
}
