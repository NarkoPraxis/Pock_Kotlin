package gameobjects.puckstyle

import android.graphics.Canvas

class ZIndexTailWrapper(
    private val inner: TailRenderer,
    override val zIndex: Int
) : TailRenderer {
    override val theme: ColorTheme get() = inner.theme
    override fun render(canvas: Canvas, renderer: PuckRenderer) = inner.render(canvas, renderer)
    override fun clear() = inner.clear()
    override fun fillTo(x: Float, y: Float) = inner.fillTo(x, y)
}
