package shapes

import enums.BallType
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.skins.InvisibleSkin
import gameobjects.puckstyle.tails.InvisibleTail

class PaddleCarousel : ComponentCarousel() {

    override val animateBounce: Boolean = false

    override fun isComponentUnlocked(type: BallType): Boolean = utility.Storage.isPaddleUnlocked(type)

    override fun buildRendererForType(type: BallType, renderer: PuckRenderer): PuckRenderer {
        val skin = InvisibleSkin(renderer)
        val tail = InvisibleTail(renderer)
        val paddle = BallStyleFactory.buildPaddle(type, renderer)
        renderer.attach(skin, tail, paddle)
        // cbcCarouselMode draws the idle paddle, so no charge priming is needed. Priming with
        // increaseCharge() would inject a non-zero fill (chargeStart == 0), making the frozen
        // preview look partially charged — keep it at phase Idle / zero charge.
        renderer.effect.frozen = true
        renderer.effect.cbcCarouselMode = true
        return renderer
    }

    override fun prepareForDraw(renderer: PuckRenderer, frame: Int) {
        renderer.effectEnabled = true
    }
}
