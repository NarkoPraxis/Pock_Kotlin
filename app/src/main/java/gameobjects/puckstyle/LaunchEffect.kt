package gameobjects.puckstyle

import android.graphics.Canvas

interface LaunchEffect {
    val theme: ColorTheme

    /** Local z-index within a PuckRenderer composition. Default 3 (in front of body and tail). */
    val zIndex: Int get() = 3

    fun draw(canvas: Canvas, renderer: PuckRenderer)
    fun onRelease(x: Float, y: Float, radius: Float, sweetSpotHit: Boolean)
    fun reset()
}
