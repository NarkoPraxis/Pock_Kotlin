package gameobjects.puckstyle

import android.graphics.Canvas
import physics.Point

interface PuckSkin {
    val theme: ColorTheme

    val renderer: PuckRenderer

    /** Local z-index within a PuckRenderer composition. Default 0 (body layer). */
    val zIndex: Int get() = 0


    /** Returns the ColorGroup this skin should use for the current frame based on renderer state. */
    fun resolvedColors(): ColorGroup = renderer.resolveColorGroup(theme)

    val responsivePrimary: Int
        get() = resolvedColors().primary

    val responsiveSecondary: Int
        get() = resolvedColors().secondary
    fun drawBody(canvas: Canvas)

    fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {}
    fun onCollisionWin(position: Point, speed: Float) {}
    fun onShieldedCollision(position: Point) {}
    fun onPhaseChanged(phase: ChargePhase) {}
}
