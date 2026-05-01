package gameobjects.puckstyle

import android.graphics.Canvas
import physics.Point

interface PuckSkin {
    val renderer: PuckRenderer

    /** Local z-index within a PuckRenderer composition. Default 0 (body layer). */
    val zIndex: Int get() = 0

    val theme: ColorTheme
        get() = renderer.theme

    val responsivePrimary: Int
        get() = responsiveGroup.primary

    val responsiveSecondary: Int
        get() = responsiveGroup.secondary

    val responsiveGroup: ColorGroup
        get() = renderer.responsiveColorGroup
    fun drawBody(canvas: Canvas)

    /** Frames between each firework burst during victory celebration. */
    val explosionFrequency: Int get() = 20

    /** Multiplier on the random scatter radius for victory fireworks (1.0 = full play area). */
    val scatterDensity: Float get() = 1f

    fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {}
    fun onVictory(x: Float, y: Float) {}
    fun onCollisionWin(position: Point, speed: Float) {}
    fun onShieldedCollision(position: Point) {}
    fun onPhaseChanged(phase: ChargePhase) {}

    /** Called by Logic when this puck is within radius*2 of a wall or the opponent. threatX/Y are world coords of the nearest threat point. */
    fun onDangerNear(threatX: Float, threatY: Float) {}

    /** Called by Logic when the danger condition from onDangerNear is no longer active. */
    fun onDangerClear() {}

    /** Called by Logic when this puck hits a wall or loses a puck collision (unshielded only). */
    fun onHit() {}
}
