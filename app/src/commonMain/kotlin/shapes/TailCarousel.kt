package shapes

import enums.BallType
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.skins.InvisibleSkin
import gameobjects.puckstyle.paddles.ClassicLaunch

class TailCarousel : ComponentCarousel() {

    override fun isComponentUnlocked(type: BallType): Boolean = utility.Storage.isTailUnlocked(type)

    override fun buildRendererForType(type: BallType, renderer: PuckRenderer): PuckRenderer {
        val skin = InvisibleSkin(renderer)
        val tail = BallStyleFactory.buildTail(type, renderer)
        val paddle = ClassicLaunch(renderer)
        renderer.attach(skin, tail, paddle)
        return renderer
    }

    override fun prepareForDraw(renderer: PuckRenderer, frame: Int) {
        renderer.effectEnabled = false
    }
}
