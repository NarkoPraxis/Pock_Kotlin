package gameobjects.puckstyle

import android.graphics.Paint
import android.graphics.Shader

/**
 * Base class for skins that use a cached gradient shader.
 * Subclasses implement [createShader]; the base class checks whether the radius
 * has changed and only rebuilds when necessary.
 *
 * Eliminates the copy-pasted lastRadius / ensureShader() pattern in
 * FireSkin, IceSkin, PlasmaSkin, and MetalSkin.
 */
abstract class CachedShaderSkin(override val theme: ColorTheme) : PuckSkin {

    protected val fill = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private var lastRadius = -1f

    protected fun ensureShader(radius: Float) {
        if (radius != lastRadius) {
            fill.shader = createShader(radius)
            lastRadius = radius
        }
    }

    /** Called only when radius changes. Build and return the new shader. */
    protected abstract fun createShader(radius: Float): Shader
}
