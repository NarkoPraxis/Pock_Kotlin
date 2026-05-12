package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer

class NeonTail(override val renderer: PuckRenderer) : TailRenderer {
    private data class Ring(var x: Float = 0f, var y: Float = 0f)
    private var rings: MutableList<Ring>? = null

    private val tailLen = (30 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)

    private fun neonAlpha(ratio: Float): Int {
        val blendWidth = 0.04f
        val b1 = 0.45f
        val b2 = 0.83f
        val v1 = 150f
        val v2 = 150f + (40f - 150f) * ((ratio - b1) / (b2 - b1)).coerceIn(0f, 1f)
        val v3 = 40f + (0f - 40f) * ((ratio - b2) / (1f - b2)).coerceIn(0f, 1f)
        val t1 = ((ratio - (b1 - blendWidth)) / (2f * blendWidth)).coerceIn(0f, 1f)
        val blended12 = v1 + (v2 - v1) * t1
        val t2 = ((ratio - (b2 - blendWidth)) / (2f * blendWidth)).coerceIn(0f, 1f)
        return (blended12 + (v3 - blended12) * t2).toInt().coerceIn(0, 255)
    }

    override fun render(scope: DrawScope) {
        val len = tailLen
        if (rings == null || rings!!.size != len) rings = MutableList(len) { Ring(renderer.x, renderer.y) }
        val rings = rings!!
        val color = responsivePrimary
        val sw = renderer.strokeWidth
        val lastIndex = (rings.size - 1).coerceAtLeast(1)

        for (i in rings.size - 1 downTo 0) {
            if (i - 1 >= 0) { rings[i].x = rings[i - 1].x; rings[i].y = rings[i - 1].y }
            else             { rings[i].x = renderer.x;     rings[i].y = renderer.y     }

            val ratio = i.toFloat() / lastIndex
            scope.drawCircle(
                color = Color(Palette.withAlpha(color, neonAlpha(ratio))),
                radius = renderer.radius,
                center = Offset(rings[i].x, rings[i].y),
                style = Stroke(sw)
            )
        }
    }

    override fun clear() { rings = null }

    override fun fillTo(x: Float, y: Float) {
        rings?.forEach { it.x = x; it.y = y }
    }
}
