package shapes

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import enums.Direction
import physics.Point
import physics.Ticker

class Explosion(
    var firstColor: Int,
    var secondColor: Int,
    var backgroundColor: Int,
    var position: Point,
    var radius: Float,
    var persistant: Boolean,
    var angle: Direction = Direction.FULL,
    var alpha: Int = 255
) {
    constructor() : this(0, 0, 0, Point(), 0f, false)

    private var imploding = false
    val speed = 9
    var firstTicker = Ticker(speed, true)
    var secondTicker = Ticker(speed, true)
    var thirdTicker = Ticker(speed, true)
    var spacer1 = 2
    var spacer2 = 10
    var doneExploding = false
    private var currentAlpha = 230

    private val outerRing = Path()
    private val innerRing = Path()
    private val eraseRing = Path()

    val finished: Boolean get() = doneExploding

    fun getColor(): Int = firstColor

    fun setColor(newColor: Int) { firstColor = newColor }

    fun DrawScope.drawTo() {
        if (doneExploding) return

        // ── Outer ring ────────────────────────────────────────────────────────
        outerRing.reset()
        val outerR: Float
        if (firstTicker.finished) {
            if (persistant) if (currentAlpha > alpha) currentAlpha--
            outerR = radius
        } else {
            firstTicker.tick
            outerR = (radius * firstTicker.ratio).coerceAtMost(radius)
        }
        addExplosionPath(outerRing, outerR)

        // ── Inner ring (delayed by spacer1) ───────────────────────────────────
        val hasInner: Boolean
        val innerR: Float
        if (spacer1 <= 0) {
            innerRing.reset()
            if (secondTicker.finished) {
                if (persistant) if (currentAlpha > alpha) currentAlpha--
                if (imploding) doneExploding = true
                innerR = radius
            } else {
                secondTicker.tick
                innerR = (radius * secondTicker.ratio).coerceAtMost(radius)
            }
            addExplosionPath(innerRing, innerR)
            hasInner = true
        } else {
            spacer1--
            hasInner = false
            innerR = 0f
        }

        // ── Erase ring (clip-out, delayed by spacer2, non-persistant only) ───
        val hasErase: Boolean
        if (!persistant && !imploding && spacer2 <= 0) {
            eraseRing.reset()
            if (thirdTicker.finished) {
                doneExploding = true
                addExplosionPath(eraseRing, radius)
            } else {
                thirdTicker.tick
                addExplosionPath(eraseRing, (radius * thirdTicker.ratio).coerceAtMost(radius))
            }
            hasErase = true
        } else {
            spacer2--
            hasErase = false
        }

        val alphaF = currentAlpha / 255f
        val firstC = Color(firstColor).copy(alpha = alphaF)
        val secondC = Color(secondColor).copy(alpha = alphaF)
        val canvas = drawContext.canvas

        // Draw outer ring, optionally clipping out the erase ring
        canvas.save()
        canvas.clipPath(outerRing, ClipOp.Intersect)
        if (hasErase) canvas.clipPath(eraseRing, ClipOp.Difference)
        drawPath(outerRing, firstC)
        canvas.restore()

        // Draw inner ring
        if (hasInner) {
            canvas.save()
            canvas.clipPath(innerRing, ClipOp.Intersect)
            if (hasErase) canvas.clipPath(eraseRing, ClipOp.Difference)
            drawPath(innerRing, secondC)
            canvas.restore()
        }
    }

    fun implode() {
        firstTicker.accending = false
        firstTicker.reset(100)
        secondTicker.accending = false
        secondTicker.reset(100)
        thirdTicker.accending = false
        thirdTicker.reset(100)
        persistant = false
        imploding = true
    }

    private fun addExplosionPath(path: Path, r: Float) {
        val box = Rect(position.x - r, position.y - r, position.x + r, position.y + r)
        when (angle) {
            Direction.LEFT   -> path.addArc(box, 90f, 180f)
            Direction.TOP    -> path.addArc(box, -180f, 180f)
            Direction.RIGHT  -> path.addArc(box, 270f, 180f)
            Direction.BOTTOM -> path.addArc(box, 0f, 180f)
            else             -> path.addOval(box)
        }
    }
}
