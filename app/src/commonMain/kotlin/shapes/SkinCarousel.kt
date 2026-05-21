package shapes

import enums.BallType
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.tails.InvisibleTail
import gameobjects.puckstyle.paddles.ClassicLaunch

class SkinCarousel : ComponentCarousel() {

    override fun buildRendererForType(type: BallType, renderer: PuckRenderer): PuckRenderer {
        val skin = BallStyleFactory.buildSkin(type, renderer)
        val tail = InvisibleTail(renderer)
        val paddle = ClassicLaunch(renderer)
        renderer.attach(skin, tail, paddle)
        return renderer
    }

    override fun prepareForDraw(renderer: PuckRenderer, frame: Int) {
        renderer.effectEnabled = false
    }
}
