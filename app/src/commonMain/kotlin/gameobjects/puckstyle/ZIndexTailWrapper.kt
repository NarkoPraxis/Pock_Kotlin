package gameobjects.puckstyle

import androidx.compose.ui.graphics.drawscope.DrawScope

class ZIndexTailWrapper(
    private val inner: TailRenderer,
    override val zIndex: Int
) : TailRenderer {
    override val theme: ColorTheme get() = inner.theme
    override val renderer: PuckRenderer get() = inner.renderer
    override fun render(scope: DrawScope) = inner.render(scope)
    override fun clear() = inner.clear()
    override fun fillTo(x: Float, y: Float) = inner.fillTo(x, y)
}
