package gameobjects.puckstyle.tails

import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer

class InvisibleTail(override val renderer: PuckRenderer) : TailRenderer {
    override fun render(scope: DrawScope) {}
    override fun clear() {}
}
