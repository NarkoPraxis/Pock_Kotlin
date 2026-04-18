package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import utility.PaintBucket
import kotlin.math.exp

class PixelTail(override val theme: ColorTheme) : TailRenderer {

    private class Block(var x: Float = 0f, var y: Float = 0f)
    private class Ring(val x: Float, val y: Float, var size: Float, var alpha: Int, val isFront: Boolean)

    private var blocks: MutableList<Block>? = null
    private val rings = mutableListOf<Ring>()
    private val paint     = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val ringPaint = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE }

    private var wasLaunched  = false
    private var pulseFade    = 0f
    private var shiftCounter = 0

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        val justHit = renderer.launched && !wasLaunched
        wasLaunched = renderer.launched
        if (justHit) pulseFade = 1f
        pulseFade *= 0.82f

        // shift every 3rd frame → wide gaps between squares; 15 blocks gives locked tail after index ~10
        val len = if (renderer.shielded) 60 else 15
        if (blocks == null) blocks = MutableList(len) { Block(renderer.x, renderer.y) }
        val blocks = blocks!!

        shiftCounter = (shiftCounter + 1) % 3
        if (shiftCounter == 0) {
            for (i in blocks.size - 1 downTo 1) {
                blocks[i].x = blocks[i-1].x; blocks[i].y = blocks[i-1].y
            }
        }
        blocks[0].x = renderer.x; blocks[0].y = renderer.y

        if (justHit) {
            rings.clear()
            blocks.forEachIndexed { i, b ->
                rings += Ring(b.x, b.y, computeSize(i, renderer.radius), 200, isFront = (i == 0))
            }
        }

        // rings drawn first — behind all blocks
        ringPaint.strokeWidth = renderer.radius * 0.3f
        val growRate = renderer.radius * 0.09f
        val iter = rings.iterator()
        while (iter.hasNext()) {
            val r = iter.next()
            r.size  += growRate
            r.alpha -= if (r.isFront) 6 else 12
            if (r.alpha <= 0) { iter.remove(); continue }
            val half = r.size / 2f
            ringPaint.color = Palette.withAlpha(renderer.strokeColor, r.alpha)
            canvas.drawRect(r.x - half, r.y - half, r.x + half, r.y + half, ringPaint)
        }

        // main blocks drawn on top of rings
        for (i in blocks.indices) {
            val baseSize = computeSize(i, renderer.radius)
            val side     = baseSize + pulseFade * 0.25f * baseSize
            val half     = side / 2f
            val alpha    = computeAlpha(i, blocks.size)
            val color    = if (renderer.shielded) PaintBucket.effectColor
                           else if (renderer.launched) renderer.fillColor
                           else renderer.baseFillColor
            paint.color = Palette.withAlpha(color, alpha)
            canvas.drawRect(blocks[i].x - half, blocks[i].y - half, blocks[i].x + half, blocks[i].y + half, paint)
        }
    }

    // smooth exponential decay; settled at ~index 10, locked there for the rest
    private fun computeSize(i: Int, radius: Float): Float =
        radius * (0.95f + 0.85f * exp(-i.toFloat() * 0.3f))

    private fun computeAlpha(i: Int, totalSize: Int): Int = when {
        i == 0 -> 255
        i == 1 -> 230
        i == 2 -> 205
        else   -> (180f * (1f - (i - 3).toFloat() / (totalSize - 3).coerceAtLeast(1))).toInt().coerceAtLeast(0)
    }

    override fun clear() { blocks = null; rings.clear(); wasLaunched = false; pulseFade = 0f; shiftCounter = 0 }

    override fun fillTo(x: Float, y: Float) {
        blocks?.forEach { it.x = x; it.y = y }
    }
}
