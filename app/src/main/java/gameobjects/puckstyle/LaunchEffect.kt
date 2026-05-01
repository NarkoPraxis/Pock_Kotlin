package gameobjects.puckstyle

import android.graphics.Canvas

interface LaunchEffect {

    val renderer: PuckRenderer

    /** Local z-index within a PuckRenderer composition. Default 3 (in front of body and tail). */
    val zIndex: Int get() = 3


    fun draw(canvas: Canvas)
    fun onRelease(x: Float, y: Float, radius: Float, sweetSpotHit: Boolean)
    fun reset()

    /**
     * Register a callback to fire when the paddle's strike animation completes.
     * The default implementation fires the callback immediately (for non-animated effects).
     */
    fun registerStrikeCallback(onStrike: () -> Unit) { onStrike() }

    // --- readable charge/phase state ---
    val phase: ChargePhase get() = ChargePhase.Idle
    val chargeFillRatio: Float get() = 0f
    val currentCharge: Float get() = 0f
    val chargePowerLocked: Boolean get() = false

    // --- charge control ---
    fun increaseCharge() {}
    fun clearCharge() {}

    // --- phase change events ---
    fun registerPhaseCallback(onPhaseChanged: (ChargePhase) -> Unit) {}
    fun unregisterAllPhaseCallbacks() {}
}
