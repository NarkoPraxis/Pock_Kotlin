package gameobjects.puckstyle

import android.graphics.Canvas

class ZIndexTailWrapper(
    private val inner: TailRenderer,
    override val zIndex: Int
) : TailRenderer {
    override val theme: ColorTheme get() = inner.theme
    override val renderer: PuckRenderer get() = inner.renderer
    override fun render(canvas: Canvas) = inner.render(canvas)
    override fun clear() = inner.clear()
    override fun fillTo(x: Float, y: Float) = inner.fillTo(x, y)
}
