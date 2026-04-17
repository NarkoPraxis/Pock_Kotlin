package gameobjects.puckstyle

import android.graphics.Canvas

interface LaunchEffect {
    val theme: ColorTheme

    /** Local z-index within a PuckRenderer composition. Default 1 (in front of body). */
    val zIndex: Int get() = 1

    fun draw(canvas: Canvas, renderer: PuckRenderer)
    fun onRelease(x: Float, y: Float, radius: Float, sweetSpotHit: Boolean)
    fun reset()
}
