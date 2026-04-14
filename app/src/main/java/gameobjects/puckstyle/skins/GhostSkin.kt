package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckSkin

class GhostSkin(override val theme: ColorTheme) : PuckSkin {

    private val fill = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val stroke = Paint().apply {
        color = Color.argb(200, 255, 255, 255)
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val glow = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        val glowColor = if (puck.currentCharge > 0) Palette.cyclingPurple(puck.frame) else theme.primary
        glow.color = Palette.withAlpha(glowColor, 90)
        glow.strokeWidth = puck.strokePaint.strokeWidth * 3.2f
        canvas.drawCircle(puck.x, puck.y, radius * 1.25f, glow)
        glow.color = Palette.withAlpha(glowColor, 150)
        glow.strokeWidth = puck.strokePaint.strokeWidth * 1.8f
        canvas.drawCircle(puck.x, puck.y, radius * 1.1f, glow)
        canvas.drawCircle(puck.x, puck.y, radius, fill)
        stroke.strokeWidth = puck.strokePaint.strokeWidth * 0.7f
        canvas.drawCircle(puck.x, puck.y, radius, stroke)
        puck.chargePaint.color = glowColor
    }
}
