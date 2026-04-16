package gameobjects.puckstyle

import android.graphics.Canvas
import gameobjects.Player

enum class ChargePhase { Idle, Building, SweetSpot, Overcharged }

interface LaunchEffect {
    val theme: ColorTheme
    fun draw(canvas: Canvas, player: Player)
    fun onRelease(player: Player, sweetSpotHit: Boolean)
    fun reset()
}
