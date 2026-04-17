package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
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

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val glowColor = if (renderer.currentCharge > 0) Palette.cyclingPurple(renderer.frame) else theme.primary
        glow.color = Palette.withAlpha(glowColor, 90)
        glow.strokeWidth = renderer.strokePaint.strokeWidth * 3.2f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius * 1.25f, glow)
        glow.color = Palette.withAlpha(glowColor, 150)
        glow.strokeWidth = renderer.strokePaint.strokeWidth * 1.8f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius * 1.1f, glow)
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, fill)
        stroke.strokeWidth = renderer.strokePaint.strokeWidth * 0.7f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, stroke)
        renderer.chargePaint.color = glowColor
    }
}
