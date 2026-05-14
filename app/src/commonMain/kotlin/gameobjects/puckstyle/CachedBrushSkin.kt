package gameobjects.puckstyle

import androidx.compose.ui.graphics.Brush

/**
 * Base class for skins that use a cached gradient brush.
 * Subclasses implement [buildBrush]; the base class checks whether the radius
 * has changed and only rebuilds when necessary.
 */
abstract class CachedBrushSkin(override val renderer: PuckRenderer) : PuckSkin {

    protected var cachedBrush: Brush? = null
    private var lastRadius = -1f

    protected fun ensureBrush(radius: Float) {
        if (radius != lastRadius) {
            cachedBrush = buildBrush(radius)
            lastRadius = radius
        }
    }

    /** Forces the brush to rebuild on the next ensureBrush call, e.g. when theme state changes. */
    protected fun invalidateBrush() { lastRadius = -1f }

    /** Called only when radius changes (or after invalidateBrush). Build and return the new brush. */
    protected abstract fun buildBrush(radius: Float): Brush
}
