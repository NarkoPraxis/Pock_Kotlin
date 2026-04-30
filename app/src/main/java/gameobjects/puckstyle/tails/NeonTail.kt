package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer

class NeonTail(override val theme: ColorTheme, override val renderer: PuckRenderer) : TailRenderer {
    private data class Ring(var x: Float = 0f, var y: Float = 0f)
    private var rings: MutableList<Ring>? = null

    // Tail length is fixed — compute once at construction, never changes after setup
    private val tailLen = (30 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)

    private val paint = Paint().apply {
        isAntiAlias = true; isDither = true; style = Paint.Style.STROKE
    }

    /**
     * Three-zone alpha curve: flat head → slow middle fade → sharp tail drop, smoothed at boundaries.
     *   Zone 1 [0.00, 0.45): flat at 150
     *   Zone 2 [0.45, 0.83): slow linear fade 150 → 40
     *   Zone 3 [0.83, 1.00]: sharp linear fade 40 → 0
     * Each boundary is cross-faded over a ±0.04 blend window to avoid hard edges.
     */
    private fun neonAlpha(ratio: Float): Int {
        val blendWidth = 0.04f
        val b1 = 0.45f  // boundary between zone 1 and 2
        val b2 = 0.83f  // boundary between zone 2 and 3

        val zone1 = 150f
        val zone3Start = 40f
        val zone3End = 0f

        // raw value for each zone
        val v1 = zone1
        val v2 = zone1 + (zone3Start - zone1) * ((ratio - b1) / (b2 - b1)).coerceIn(0f, 1f)
        val v3 = zone3Start + (zone3End - zone3Start) * ((ratio - b2) / (1f - b2)).coerceIn(0f, 1f)

        // blend at boundary 1
        val t1 = ((ratio - (b1 - blendWidth)) / (2f * blendWidth)).coerceIn(0f, 1f)
        val blended12 = v1 + (v2 - v1) * t1

        // blend at boundary 2
        val t2 = ((ratio - (b2 - blendWidth)) / (2f * blendWidth)).coerceIn(0f, 1f)
        val blended = blended12 + (v3 - blended12) * t2

        return blended.toInt().coerceIn(0, 255)
    }

    override fun render(canvas: Canvas) {
        val len = tailLen
        if (rings == null || rings!!.size != len) rings = MutableList(len) { Ring(renderer.x, renderer.y) }
        val rings = rings!!
        val color = resolvedColors().primary
        val sw = renderer.strokePaint.strokeWidth
        val lastIndex = (rings.size - 1).coerceAtLeast(1)

        paint.strokeWidth = sw

        for (i in rings.size - 1 downTo 0) {
            if (i - 1 >= 0) { rings[i].x = rings[i - 1].x; rings[i].y = rings[i - 1].y }
            else             { rings[i].x = renderer.x;     rings[i].y = renderer.y     }

            val ratio = i.toFloat() / lastIndex
            paint.color = Palette.withAlpha(color, neonAlpha(ratio))
            canvas.drawCircle(rings[i].x, rings[i].y, renderer.radius, paint)
        }
    }

    override fun clear() { rings = null }

    override fun fillTo(x: Float, y: Float) {
        rings?.forEach { it.x = x; it.y = y }
    }
}
