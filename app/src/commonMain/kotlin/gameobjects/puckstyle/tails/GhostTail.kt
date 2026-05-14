package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import gameobjects.puckstyle.skins.GhostSkin.Companion.radiusOffset

class GhostTail(override val renderer: PuckRenderer) : TailRenderer {

    private var xs: FloatArray? = null
    private var ys: FloatArray? = null

    private var cachedGhostLen = -1
    private val baseCount = 30

    override val zIndex: Int
        get() = 2

    override fun render(scope: DrawScope) {
        val ghostLen = (baseCount * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)

        if (ghostLen != cachedGhostLen) {
            xs = FloatArray(ghostLen) { renderer.x }
            ys = FloatArray(ghostLen) { renderer.y }
            cachedGhostLen = ghostLen
        }
        val xs = xs!!
        val ys = ys!!

        val glowColor = responsivePrimary
        val radiusOffset = radiusOffset(renderer)
        val r = renderer.radius
        val sw = renderer.strokeWidth * 1.2f
        val lastIdx = ghostLen - 1

        for (i in lastIdx downTo 1) {
            xs[i] = xs[i - 1]
            ys[i] = ys[i - 1]
        }
        xs[0] = renderer.x
        ys[0] = renderer.y

        for (i in lastIdx downTo 0) {
            val ratio = i.toFloat() / lastIdx.coerceAtLeast(1)
            val size = r - Settings.strokeWidth - r * (i.coerceAtLeast(1).toFloat() / lastIdx)
            val alpha = (255f * (1f - ratio)).toInt()
            scope.drawCircle(
                color = Color(Palette.withAlpha(glowColor, (alpha * 0.45f).toInt())),
                radius = size * 1.15f * radiusOffset,
                center = Offset(xs[i], ys[i]),
                style = Stroke(sw)
            )
        }

        for (i in lastIdx downTo 0) {
            val ratio = i.toFloat() / lastIdx.coerceAtLeast(1)
            val size = r * 1.1f - Settings.strokeWidth - r * (i.coerceAtLeast(1).toFloat() / lastIdx)
            val alpha = (255f * (1f - ratio)).toInt()
            scope.drawCircle(
                color = Color(1f, 1f, 1f, (alpha * 0.75f) / 255f),
                radius = size * radiusOffset,
                center = Offset(xs[i], ys[i])
            )
        }
    }

    override fun clear() {
        xs = null
        ys = null
        cachedGhostLen = -1
    }

    override fun fillTo(x: Float, y: Float) {
        xs?.fill(x)
        ys?.fill(y)
    }
}
