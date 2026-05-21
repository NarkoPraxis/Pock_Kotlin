package shapes

import enums.BallType
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.skins.InvisibleSkin
import gameobjects.puckstyle.tails.InvisibleTail

class PaddleCarousel : ComponentCarousel() {

    override fun buildRendererForType(type: BallType, renderer: PuckRenderer): PuckRenderer {
        val skin = InvisibleSkin(renderer)
        val tail = InvisibleTail(renderer)
        val paddle = BallStyleFactory.buildPaddle(type, renderer)
        renderer.attach(skin, tail, paddle)
        return renderer
    }

    override fun prepareForDraw(renderer: PuckRenderer, frame: Int) {
        renderer.effectEnabled = true
        // Slowly cycle through charge so the paddle is visible and animated.
        // Reset from Inert so the cycle repeats continuously.
        if (renderer.effect.phase == ChargePhase.Inert) {
            renderer.effect.reset()
        }
        if (renderer.effect.phase == ChargePhase.Idle || renderer.effect.phase == ChargePhase.Building) {
            if (frame % 2 == 0) renderer.effect.increaseCharge()
        }
    }
}
