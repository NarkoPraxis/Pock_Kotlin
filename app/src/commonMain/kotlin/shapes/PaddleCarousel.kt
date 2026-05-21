package shapes

import enums.BallType
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.skins.InvisibleSkin
import gameobjects.puckstyle.tails.InvisibleTail

class PaddleCarousel : ComponentCarousel() {

    override val animateBounce: Boolean = false

    override fun buildRendererForType(type: BallType, renderer: PuckRenderer): PuckRenderer {
        val skin = InvisibleSkin(renderer)
        val tail = InvisibleTail(renderer)
        val paddle = BallStyleFactory.buildPaddle(type, renderer)
        renderer.attach(skin, tail, paddle)
        // Prime to Building at chargeStart (paddle bar visible), then freeze so it never advances.
        renderer.effect.increaseCharge()
        renderer.effect.frozen = true
        return renderer
    }

    override fun prepareForDraw(renderer: PuckRenderer, frame: Int) {
        renderer.effectEnabled = true
    }
}
