package gameobjects.puckstyle

import android.graphics.Canvas
import gameobjects.Puck

interface TailRenderer {
    val theme: ColorTheme
    fun render(
        canvas: Canvas,
        puck: Puck,
        shielded: Boolean,
        launched: Boolean,
        baseFillColor: Int
    )
    fun clear()
}
