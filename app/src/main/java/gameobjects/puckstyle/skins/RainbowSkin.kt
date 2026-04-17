package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class RainbowSkin(override val theme: ColorTheme) : PuckSkin {
    private val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val stroke = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }
    private val hueOffset = if (theme.isWarm) 0f else 200f

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        fill.color = if (renderer.currentCharge > 0) Palette.cyclingPurple(renderer.frame)
                     else Palette.hsv(renderer.frame * 4f + hueOffset, 1f, 1f)
        stroke.color = Palette.hsv(renderer.frame * 4f + hueOffset + 30f, 1f, 0.8f)
        stroke.strokeWidth = renderer.strokePaint.strokeWidth
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, fill)
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, stroke)
        renderer.chargePaint.color = fill.color
    }
}
