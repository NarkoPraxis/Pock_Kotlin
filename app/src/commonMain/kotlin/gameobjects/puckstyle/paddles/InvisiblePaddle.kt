package gameobjects.puckstyle.paddles

import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer

/**
 * A no-op paddle. Draws nothing, ever — and is never `alwaysVisible`, so it can't leak into a
 * preview the way real paddles (Galaxy/Spinner) do. Used by the skin-only and tail-only carousel
 * renderers so those previews show ONLY their one component, never a stray paddle.
 */
class InvisiblePaddle(renderer: PuckRenderer) : PaddleLaunchEffect(renderer) {
    override fun draw(scope: DrawScope) {}
}
