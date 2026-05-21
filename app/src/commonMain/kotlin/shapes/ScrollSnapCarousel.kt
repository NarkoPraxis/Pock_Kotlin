package shapes

import gameobjects.Settings
import kotlin.math.abs
import kotlin.math.roundToInt

open class ScrollSnapCarousel {

    protected var scrollX: Float = 0f
    var snapIndex: Int = 0
        protected set

    private var dragging: Boolean = false
    private var lastLogicalX: Float = 0f
    private var dragDistance: Float = 0f

    open val itemCount: Int get() = 0
    open val slotW: Float get() = Settings.screenRatio * 4f
    open val cx: Float get() = Settings.middleX

    open fun toLogicalX(screenX: Float): Float = screenX

    open fun clampScroll() {
        val max = ((itemCount - 1).coerceAtLeast(0)) * slotW
        if (scrollX < 0f) scrollX = 0f
        if (scrollX > max) scrollX = max
    }

    open fun onSnappedTo(index: Int) {}

    fun scrollToIndex(index: Int) {
        snapIndex = index.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
        scrollX = snapIndex * slotW
    }

    fun cancelDrag() { dragging = false }

    fun handleScrollTouchEvent(action: Int, x: Float, y: Float): Boolean {
        val masked = action and 0xff
        val logicalX = toLogicalX(x)

        return when (masked) {
            ACTION_DOWN, ACTION_POINTER_DOWN -> {
                dragging = true
                lastLogicalX = logicalX
                dragDistance = 0f
                true
            }
            ACTION_MOVE -> {
                if (!dragging) return true
                val dx = logicalX - lastLogicalX
                lastLogicalX = logicalX
                scrollX -= dx
                dragDistance += abs(dx)
                clampScroll()
                true
            }
            ACTION_UP, ACTION_POINTER_UP, ACTION_CANCEL -> {
                if (!dragging) return true
                dragging = false
                val snap = if (dragDistance < Settings.screenRatio * 0.6f) {
                    val slotLogicalX = logicalX - (cx - scrollX)
                    (slotLogicalX / slotW).roundToInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                } else {
                    (scrollX / slotW).roundToInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                }
                scrollX = snap * slotW
                if (snap != snapIndex) {
                    snapIndex = snap
                    onSnappedTo(snap)
                } else {
                    onSnappedTo(snapIndex)
                }
                true
            }
            else -> false
        }
    }

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
        const val ACTION_CANCEL = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP = 6
    }
}
