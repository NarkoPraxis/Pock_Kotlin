package gameobjects.puckstyle

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.withSaveLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.LayoutDirection

interface TailRenderer {
    val renderer: PuckRenderer

    val zIndex: Int get() = -1

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
