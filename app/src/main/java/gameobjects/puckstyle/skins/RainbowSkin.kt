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
    private val hueOffset = Palette.themeHue(theme)

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val baseHue = renderer.frame * 4f + hueOffset
        fill.color = if (renderer.currentCharge > 0) Palette.cyclingPurple(renderer.frame)
                     else Palette.hsvThemed(baseHue)
        stroke.color = Palette.hsvHighlight(-(renderer.frame * 4f) + hueOffset + 180f)
        stroke.strokeWidth = renderer.strokePaint.strokeWidth
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, fill)
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, stroke)
        renderer.chargePaint.color = fill.color
    }
}
