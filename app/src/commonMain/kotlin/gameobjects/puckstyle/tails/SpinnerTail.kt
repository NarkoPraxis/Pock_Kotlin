package gameobjects.puckstyle.tails

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SpinnerTail(override val renderer: PuckRenderer) : TailRenderer {

    private data class Pos(var x: Float = 0f, var y: Float = 0f)

    private var history: MutableList<Pos>? = null
    private var tailRotation = 0f
    private val spinDir = if (theme.isWarm) -1f else 1f

    private val tailLen = (40 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)

    private val piF       = PI.toFloat()
    private val angleStep = 18f * piF / 180f
    private val degToRad  = piF / 180f

    override fun render(scope: DrawScope) {
        // Static UI collapses to the shared list-tail density; live keeps its longer trail.
        val len = if (renderer.staticUiMode) staticPointCount else tailLen
        if (history == null || history!!.size != len) history = MutableList(len) { Pos(renderer.x, renderer.y) }
        val history = history!!

        if (renderer.staticUiMode) {
            // Static screenshot: freeze the rotation and pose the blades along the swoosh.
            val last = (history.size - 1).coerceAtLeast(1)
            for (i in history.indices) {
                val p = staticSwooshPoint(i.toFloat() / last)
                history[i].x = p.x; history[i].y = p.y
            }
        } else {
            tailRotation += 10f
            for (i in history.size - 1 downTo 0) {
                if (i - 1 >= 0) { history[i].x = history[i - 1].x; history[i].y = history[i - 1].y }
                else             { history[i].x = renderer.x;       history[i].y = renderer.y       }
            }
        }

        val lineLen   = renderer.radius * 1.3f
        val halfLen   = lineLen / 2f
        val tipLen    = lineLen / 6f
        val sw        = renderer.strokeWidth

        val colors = responsiveGroup
        val color  = colors.primary
        val hilite = colors.secondary

        val tipSW    = sw * 3f
        val centerSW = sw * 2f

        val holdCount = 5
        val fadeDenom = (history.size - 1 - holdCount).coerceAtLeast(1).toFloat()
        val baseAngRad = tailRotation * degToRad

        for (i in 0 until history.size) {
            val alpha = if (i < holdCount) {
                255
            } else {
                val fadeRatio = (i - holdCount).toFloat() / fadeDenom
                (255f * (1f - fadeRatio)).toInt()
            }
            if (alpha <= 0) continue

            val ang = (baseAngRad - i * angleStep) * spinDir
            val cx  = history[i].x
            val cy  = history[i].y
            val ca  = cos(ang)
            val sa  = sin(ang)
            val caHalf = ca * halfLen
            val saHalf = sa * halfLen
            val caTip  = ca * (halfLen - tipLen)
            val saTip  = sa * (halfLen - tipLen)

            scope.drawLine(
                color = Color(Palette.withAlpha(hilite, alpha)),
                start = Offset(cx - caHalf, cy - saHalf),
                end = Offset(cx - caTip, cy - saTip),
                strokeWidth = tipSW,
                cap = StrokeCap.Round
            )
            scope.drawLine(
                color = Color(Palette.withAlpha(hilite, alpha)),
                start = Offset(cx + caHalf, cy + saHalf),
                end = Offset(cx + caTip, cy + saTip),
                strokeWidth = tipSW,
                cap = StrokeCap.Round
            )
            scope.drawLine(
                color = Color(Palette.withAlpha(color, alpha)),
                start = Offset(cx - caHalf, cy - saHalf),
                end = Offset(cx + caHalf, cy + saHalf),
                strokeWidth = centerSW,
                cap = StrokeCap.Round
            )
        }
    }

    override fun clear() { history = null; tailRotation = 0f }

    override fun fillTo(x: Float, y: Float) {
        history?.forEach { it.x = x; it.y = y }
    }
}
