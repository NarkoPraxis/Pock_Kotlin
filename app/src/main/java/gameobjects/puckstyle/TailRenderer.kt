package gameobjects.puckstyle

import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

interface TailRenderer {
    val theme: ColorTheme

    val renderer: PuckRenderer

    /** Local z-index within a PuckRenderer composition. Default -1 (behind body). */
    val zIndex: Int get() = -1

    /** Returns the ColorGroup this tail should use for the current frame based on renderer state. */
    fun resolvedColors(): ColorGroup = renderer.resolveColorGroup(theme)

    fun render(canvas: Canvas)
    fun clear()

    /**
     * Snap all history/particle positions to (x, y) without advancing state.
     * Called for unselected balls in menus so their tail doesn't trail as the ball moves.
     * Default no-op; override in each tail implementation.
     */
    fun fillTo(x: Float, y: Float) {}
    fun onPhaseChanged(phase: ChargePhase) {}

    /**
     * Routing wrapper called by PuckRenderer.draw(). Delegates to render() for unlocked balls;
     * for preview (placeholder) balls, wraps render() in a greyscale saveLayer so particle
     * fade effects remain intact while all colours map to near-black.
     */
    fun renderWithPreview(canvas: Canvas) {
        if (!renderer.preview) {
            render(canvas)
            return
        }
        val cm = ColorMatrix(floatArrayOf(
            0.12f, 0f,    0f,    0f, 0f,
            0f,    0.12f, 0f,    0f, 0f,
            0f,    0f,    0.12f, 0f, 0f,
            0f,    0f,    0f,    1f, 0f
        ))
        @Suppress("DEPRECATION")
        canvas.saveLayer(null, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
        render(canvas)
        canvas.restore()
    }
}
