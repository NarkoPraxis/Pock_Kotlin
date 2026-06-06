package gameobjects.puckstyle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.LayoutDirection
import gameobjects.Settings

interface TailRenderer {
    val renderer: PuckRenderer

    val zIndex: Int get() = -1

    /**
     * Shared point count for list/historical static tails. In [PuckRenderer.staticUiMode] every
     * list-structured tail (Classic, Neon, Metal, Pixel, Prism, Plasma, Rainbow, Spinner, Ice) poses
     * this many points along the [StaticTailPath] swoosh — the one sanctioned deviation from the live
     * "screenshot." The live ball has room for longer trails (30–40+ points), but the static UI ball is
     * space-constrained, so they all collapse to ClassicTail's density to avoid squished, overlapping
     * indices. ClassicTail's live length is the chosen reference (`20 * tailLengthMultiplier`).
     */
    val staticPointCount: Int
        get() = 20

    val theme: ColorTheme
        get() = renderer.theme

    val responsivePrimary: Int
        get() = responsiveGroup.primary

    val responsiveSecondary: Int
        get() = responsiveGroup.secondary

    val responsiveGroup: ColorGroup
        get() = renderer.responsiveColorGroup

    fun render(scope: DrawScope)
    fun clear()

    /**
     * Absolute screen position for a trailing tail point when [PuckRenderer.staticUiMode] is set,
     * expressed as [ratio] along the swoosh (0 = ball head, 1 = tail tip). List tails call this to
     * pose their points along the shared [StaticTailPath] instead of trailing live motion.
     */
    fun staticSwooshPoint(ratio: Float): Offset =
        StaticTailPath.worldByFraction(ratio, renderer.radius, renderer.x, renderer.y)

    fun fillTo(x: Float, y: Float) {}
    fun onPhaseChanged(phase: ChargePhase) {}

    fun renderWithPreview(scope: DrawScope) {
        if (!renderer.preview) {
            render(scope)
            return
        }
        val self = this
        scope.drawIntoCanvas { composeCanvas ->
            composeCanvas.withSaveLayer(Rect(0f, 0f, scope.size.width, scope.size.height), previewLayerPaint) {
                helperScope.draw(scope, scope.layoutDirection, composeCanvas, scope.size) {
                    self.render(this)
                }
            }
        }
    }

    companion object {
        private val helperScope = CanvasDrawScope()
        val previewLayerPaint = Paint().apply {
            colorFilter = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                0.12f, 0f,    0f,    0f, 0f,
                0f,    0.12f, 0f,    0f, 0f,
                0f,    0f,    0.12f, 0f, 0f,
                0f,    0f,    0f,    1f, 0f
            )))
        }
    }
}
