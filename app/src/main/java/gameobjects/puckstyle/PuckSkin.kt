package gameobjects.puckstyle

import android.graphics.Canvas
import physics.Point

interface PuckSkin {
    val theme: ColorTheme

    /** Local z-index within a PuckRenderer composition. Default 0 (body layer). */
    val zIndex: Int get() = 0

    fun drawBody(canvas: Canvas, renderer: PuckRenderer)

    fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {}
    fun onCollisionWin(position: Point, speed: Float) {}
    fun onShieldedCollision(position: Point) {}
    fun onPhaseChanged(phase: ChargePhase) {}
}
