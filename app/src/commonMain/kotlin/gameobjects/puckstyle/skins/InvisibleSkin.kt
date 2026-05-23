package gameobjects.puckstyle.skins

import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin

class InvisibleSkin(override val renderer: PuckRenderer) : PuckSkin {
    override fun DrawScope.drawBody() {}
}
