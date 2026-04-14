package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Puck
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket

class PixelTail(override val theme: ColorTheme) : TailRenderer {

    private class Block(var x: Float = 0f, var y: Float = 0f)
    private var blocks: MutableList<Block> = MutableList(Settings.tailLength) { Block() }
    private val paint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

    override fun render(canvas: Canvas, puck: Puck, shielded: Boolean, launched: Boolean, baseFillColor: Int) {
        if (blocks.size == 0) blocks = MutableList(if (shielded) 80 else 20) { Block() }
        for (i in blocks.size - 1 downTo 0) {
            if (i - 1 >= 0) { blocks[i].x = blocks[i - 1].x; blocks[i].y = blocks[i - 1].y }
            else { blocks[i].x = puck.x; blocks[i].y = puck.y }
            val ratio = i.toFloat() / (blocks.size - 1).coerceAtLeast(1)
            val color = if (shielded) PaintBucket.effectColor else if (launched) puck.fillColor else baseFillColor
            paint.color = Palette.withAlpha(color, (255f * (1 - ratio)).toInt())
            val size = puck.radius * 1.7f - puck.radius * ((i - 1).coerceAtLeast(0).toFloat() / (blocks.size - 1)) * 1.7f
            val half = size / 2f
            canvas.drawRect(blocks[i].x - half, blocks[i].y - half, blocks[i].x + half, blocks[i].y + half, paint)
        }
    }

    override fun clear() { blocks.clear() }
}
