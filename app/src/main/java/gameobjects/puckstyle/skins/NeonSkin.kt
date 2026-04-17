package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class NeonSkin(override val theme: ColorTheme) : PuckSkin {

    private val darkFill = Paint().apply {
        color = Color.rgb(12, 8, 20)
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val glowColor = if (renderer.currentCharge > 0) Palette.cyclingPurple(renderer.frame) else theme.primary
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, darkFill)
        glowPaint.color = Palette.withAlpha(glowColor, 60)
        glowPaint.strokeWidth = renderer.strokePaint.strokeWidth * 3f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(glowColor, 130)
        glowPaint.strokeWidth = renderer.strokePaint.strokeWidth * 1.8f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = glowColor
        glowPaint.strokeWidth = renderer.strokePaint.strokeWidth
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        renderer.chargePaint.color = glowColor
    }
}
