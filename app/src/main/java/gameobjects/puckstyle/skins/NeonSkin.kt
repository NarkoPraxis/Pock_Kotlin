package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class NeonSkin(override val theme: ColorTheme) : PuckSkin {

    // Subtle dark fill so the hollow center reads as a translucent tube, not empty space
    private val subtleFill = Paint().apply {
        color = Color.argb(35, 0, 0, 0)
        isAntiAlias = true; isDither = true; style = Paint.Style.FILL
    }

    private val glowPaint = Paint().apply {
        isAntiAlias = true; isDither = true; style = Paint.Style.STROKE
    }

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val sw = renderer.strokePaint.strokeWidth
//        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, subtleFill)

        // 4 glow rings, outermost first — body always stays theme color, charging shown via chargePaint
        glowPaint.color = Palette.withAlpha(theme.primary, 25);  glowPaint.strokeWidth = sw * 5.0f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(theme.primary, 45);  glowPaint.strokeWidth = sw * 3.2f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(theme.primary, 110); glowPaint.strokeWidth = sw * 1.8f
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)
        glowPaint.color = Palette.withAlpha(theme.primary, 220); glowPaint.strokeWidth = sw
        canvas.drawCircle(renderer.x, renderer.y, renderer.radius, glowPaint)

        renderer.chargePaint.color = theme.accent
    }
}
