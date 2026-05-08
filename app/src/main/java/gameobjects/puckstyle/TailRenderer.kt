package gameobjects.puckstyle

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.LayoutDirection

interface TailRenderer {
    val renderer: PuckRenderer

    /** Local z-index within a PuckRenderer composition. Default -1 (behind body). */
    val zIndex: Int get() = -1

    val theme: ColorTheme
        get() = renderer.theme

    val responsivePrimary: Int
        get() = responsiveGroup.primary

    val responsiveSecondary: Int
        get() = responsiveGroup.secondary

    /** Returns the ColorGroup this tail should use for the current frame based on renderer state. */
    val responsiveGroup: ColorGroup
        get() = renderer.responsiveColorGroup

    fun render(scope: DrawScope)
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
    fun renderWithPreview(scope: DrawScope) {
        if (!renderer.preview) {
            render(scope)
            return
        }
        val self = this
        scope.drawIntoCanvas { composeCanvas ->
            val nativeCanvas = composeCanvas.nativeCanvas
            nativeCanvas.saveLayer(null, previewLayerPaint)
            helperScope.draw(scope, scope.layoutDirection, composeCanvas, scope.size) {
                self.render(this)
            }
            nativeCanvas.restore()
        }
    }

    companion object {
        private val helperScope = CanvasDrawScope()
        val previewLayerPaint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                0.12f, 0f,    0f,    0f, 0f,
                0f,    0.12f, 0f,    0f, 0f,
                0f,    0f,    0.12f, 0f, 0f,
                0f,    0f,    0f,    1f, 0f
            )))
        }
    }
}
