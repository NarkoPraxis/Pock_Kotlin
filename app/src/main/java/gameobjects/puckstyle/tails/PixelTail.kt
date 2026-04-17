package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket

class PixelTail(override val theme: ColorTheme) : TailRenderer {

    private class Block(var x: Float = 0f, var y: Float = 0f)
    private var blocks: MutableList<Block>? = null
    private val paint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        if (blocks == null) blocks = MutableList(if (renderer.shielded) 80 else 20) { Block(renderer.x, renderer.y) }
        val blocks = blocks!!
        for (i in blocks.size - 1 downTo 0) {
            if (i - 1 >= 0) { blocks[i].x = blocks[i - 1].x; blocks[i].y = blocks[i - 1].y }
            else { blocks[i].x = renderer.x; blocks[i].y = renderer.y }
            val ratio = i.toFloat() / (blocks.size - 1).coerceAtLeast(1)
            val color = if (renderer.shielded) PaintBucket.effectColor
                        else if (renderer.launched) renderer.fillColor
                        else renderer.baseFillColor
            paint.color = Palette.withAlpha(color, (255f * (1 - ratio)).toInt())
            val size = renderer.radius * 1.7f - renderer.radius * ((i - 1).coerceAtLeast(0).toFloat() / (blocks.size - 1)) * 1.7f
            val half = size / 2f
            canvas.drawRect(blocks[i].x - half, blocks[i].y - half, blocks[i].x + half, blocks[i].y + half, paint)
        }
    }

    override fun clear() { blocks = null }

    override fun fillTo(x: Float, y: Float) {
        blocks?.forEach { it.x = x; it.y = y }
    }
}
