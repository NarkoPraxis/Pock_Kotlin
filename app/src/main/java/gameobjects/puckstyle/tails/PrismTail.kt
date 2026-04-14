package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.TailRenderer

class PrismTail(override val theme: ColorTheme) : TailRenderer {

    private class Pos(var x: Float = 0f, var y: Float = 0f)
    private var points: MutableList<Pos> = MutableList(Settings.tailLength) { Pos() }

    private val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    private val channelA = if (theme.isWarm) android.graphics.Color.rgb(255, 80, 60) else android.graphics.Color.rgb(60, 200, 255)
    private val channelB = if (theme.isWarm) android.graphics.Color.rgb(255, 180, 40) else android.graphics.Color.rgb(80, 120, 255)
    private val channelC = if (theme.isWarm) android.graphics.Color.rgb(255, 240, 80) else android.graphics.Color.rgb(180, 80, 255)

    override fun render(canvas: Canvas, puck: Puck, shielded: Boolean, launched: Boolean, baseFillColor: Int) {
        if (points.size == 0) points = MutableList(if (shielded) 80 else 24) { Pos() }
        for (i in points.size - 1 downTo 0) {
            if (i - 1 >= 0) { points[i].x = points[i - 1].x; points[i].y = points[i - 1].y }
            else { points[i].x = puck.x; points[i].y = puck.y }
            val ratio = i.toFloat() / (points.size - 1).coerceAtLeast(1)
            val size = puck.radius * 1.0f - puck.radius * ratio
            val alpha = (200f * (1 - ratio)).toInt()
            val offset = Settings.screenRatio * 0.15f

            paint.color = Palette.withAlpha(channelA, alpha)
            canvas.drawCircle(points[i].x - offset, points[i].y, size, paint)
            paint.color = Palette.withAlpha(channelB, alpha)
            canvas.drawCircle(points[i].x + offset, points[i].y, size, paint)
            paint.color = Palette.withAlpha(channelC, alpha)
            canvas.drawCircle(points[i].x, points[i].y - offset * 0.8f, size, paint)
        }
    }

    override fun clear() { points.clear() }
}
