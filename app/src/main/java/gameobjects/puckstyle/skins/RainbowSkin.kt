package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckSkin

class RainbowSkin(override val theme: ColorTheme) : PuckSkin {
    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val stroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val hueOffset = if (theme.isWarm) 0f else 200f

    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        fill.color = if (puck.currentCharge > 0) Palette.cyclingPurple(puck.frame)
                     else Palette.hsv(puck.frame * 4f + hueOffset, 1f, 1f)
        stroke.color = Palette.hsv(puck.frame * 4f + hueOffset + 30f, 1f, 0.8f)
        stroke.strokeWidth = puck.strokePaint.strokeWidth
        canvas.drawCircle(puck.x, puck.y, radius, fill)
        canvas.drawCircle(puck.x, puck.y, radius, stroke)
        puck.chargePaint.color = fill.color
    }
}
