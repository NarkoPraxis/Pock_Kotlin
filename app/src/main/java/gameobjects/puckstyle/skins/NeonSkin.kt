package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
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

    override fun drawBody(canvas: Canvas, puck: Puck, radius: Float) {
        val glowColor = if (puck.currentCharge > 0) Palette.cyclingPurple(puck.frame) else theme.primary
        canvas.drawCircle(puck.x, puck.y, radius, darkFill)
        glowPaint.color = Palette.withAlpha(glowColor, 60)
        glowPaint.strokeWidth = puck.strokePaint.strokeWidth * 3f
        canvas.drawCircle(puck.x, puck.y, radius, glowPaint)
        glowPaint.color = Palette.withAlpha(glowColor, 130)
        glowPaint.strokeWidth = puck.strokePaint.strokeWidth * 1.8f
        canvas.drawCircle(puck.x, puck.y, radius, glowPaint)
        glowPaint.color = glowColor
        glowPaint.strokeWidth = puck.strokePaint.strokeWidth
        canvas.drawCircle(puck.x, puck.y, radius, glowPaint)
        puck.chargePaint.color = glowColor
    }
}
