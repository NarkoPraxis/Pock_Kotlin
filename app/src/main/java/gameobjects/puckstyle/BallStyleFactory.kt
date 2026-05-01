package gameobjects.puckstyle

import enums.BallType
import gameobjects.puckstyle.paddles.*
import gameobjects.puckstyle.skins.*
import gameobjects.puckstyle.tails.*

data class BallStyle(val skin: PuckSkin, val tail: TailRenderer, val effect: PaddleLaunchEffect)

data class RandomRoll(val skinType: BallType, val tailType: BallType, val effectType: BallType, val tailZIndex: Int)

object BallStyleFactory {


    fun buildBallStyle(type: BallType, renderer: PuckRenderer): BallStyle {
        return when (type) {
            BallType.Classic -> BallStyle(ClassicSkin(renderer), ClassicTail(renderer), ClassicLaunch(renderer))
            BallType.Neon    -> BallStyle(NeonSkin(renderer),    NeonTail(renderer),    NeonLaunch(renderer))
            BallType.Ghost   -> BallStyle(GhostSkin(renderer),   GhostTail(renderer),   GhostLaunch(renderer))
            BallType.Fire    -> BallStyle(FireSkin(renderer),    FireTail(renderer),    FireLaunch(renderer))
            BallType.Ice     -> BallStyle(IceSkin(renderer),     IceTail(renderer),     IceLaunch(renderer))
            BallType.Galaxy  -> BallStyle(GalaxySkin(renderer),  GalaxyTail(renderer),  GalaxyLaunch(renderer))
            BallType.Spinner -> BallStyle(SpinnerSkin(renderer), SpinnerTail(renderer), SpinnerLaunch(renderer))
            BallType.Metal   -> BallStyle(MetalSkin(renderer),   MetalTail(renderer),   MetalLaunch(renderer))
            BallType.Pixel   -> BallStyle(PixelSkin(renderer),   PixelTail(renderer),   PixelLaunch(renderer))
            BallType.Rainbow -> BallStyle(RainbowSkin(renderer), RainbowTail(renderer), RainbowLaunch(renderer))
            BallType.Prism   -> BallStyle(PrismSkin(renderer),   PrismTail(renderer),   PrismLaunch(renderer))
            BallType.Plasma   -> BallStyle(PlasmaSkin(renderer),   PlasmaTail(renderer),   PlasmaLaunch(renderer))
            BallType.Chicken  -> BallStyle(ChickenSkin(renderer),  ChickenTail(renderer),  ChickenLaunch(renderer))
            BallType.Random   -> buildRandom(renderer)
        }
    }

    fun rollRandom(): RandomRoll {
        val pool = BallType.entries.filter { it != BallType.Random }
        return RandomRoll(pool.random(), pool.random(), pool.random(), if (kotlin.random.Random.nextBoolean()) -1 else 1)
    }

    fun buildRandom(renderer: PuckRenderer, existingRoll: RandomRoll? = null): BallStyle {
        val roll = existingRoll ?: rollRandom()
        val wrappedTail = ZIndexTailWrapper(buildTail(roll.tailType, renderer), roll.tailZIndex)

        return BallStyle(buildSkin(roll.skinType, renderer), wrappedTail, buildPaddle(roll.effectType, renderer))
    }

    fun buildSkin(type: BallType, renderer: PuckRenderer): PuckSkin {
        return when (type) {
            BallType.Classic -> ClassicSkin(renderer)
            BallType.Neon    -> NeonSkin(renderer)
            BallType.Ghost   -> GhostSkin(renderer)
            BallType.Fire    -> FireSkin(renderer)
            BallType.Ice     -> IceSkin(renderer)
            BallType.Galaxy  -> GalaxySkin(renderer)
            BallType.Spinner -> SpinnerSkin(renderer)
            BallType.Metal   -> MetalSkin(renderer)
            BallType.Pixel   -> PixelSkin(renderer)
            BallType.Rainbow -> RainbowSkin(renderer)
            BallType.Prism   -> PrismSkin(renderer)
            BallType.Plasma   -> PlasmaSkin(renderer)
            BallType.Chicken  -> ChickenSkin(renderer)
            BallType.Random -> buildSkin(BallType.entries.random(), renderer)
        }
    }

    fun buildTail(type: BallType, renderer: PuckRenderer): TailRenderer {
        return when (type) {
            BallType.Classic -> ClassicTail(renderer)
            BallType.Neon    -> NeonTail(renderer)
            BallType.Ghost   -> GhostTail(renderer)
            BallType.Fire    -> FireTail(renderer)
            BallType.Ice     -> IceTail(renderer)
            BallType.Galaxy  -> GalaxyTail(renderer)
            BallType.Spinner -> SpinnerTail(renderer)
            BallType.Metal   -> MetalTail(renderer)
            BallType.Pixel   -> PixelTail(renderer)
            BallType.Rainbow -> RainbowTail(renderer)
            BallType.Prism   -> PrismTail(renderer)
            BallType.Plasma   -> PlasmaTail(renderer)
            BallType.Chicken  -> ChickenTail(renderer)
            BallType.Random   -> buildTail(BallType.entries.random(), renderer)
        }
    }

    fun buildPaddle(type: BallType, renderer: PuckRenderer): PaddleLaunchEffect {
        return when (type) {
            BallType.Classic -> ClassicLaunch(renderer)
            BallType.Neon    -> NeonLaunch(renderer)
            BallType.Ghost   -> GhostLaunch(renderer)
            BallType.Fire    -> FireLaunch(renderer)
            BallType.Ice     -> IceLaunch(renderer)
            BallType.Galaxy  -> GalaxyLaunch(renderer)
            BallType.Spinner -> SpinnerLaunch(renderer)
            BallType.Metal   -> MetalLaunch(renderer)
            BallType.Pixel   -> PixelLaunch(renderer)
            BallType.Rainbow -> RainbowLaunch(renderer)
            BallType.Prism   -> PrismLaunch(renderer)
            BallType.Plasma   -> PlasmaLaunch(renderer)
            BallType.Chicken  -> ChickenLaunch(renderer)
            BallType.Random   -> buildPaddle(BallType.entries.random(), renderer)
        }
    }

    fun buildRenderer(type: BallType, theme: ColorTheme, existingRoll: RandomRoll? = null): PuckRenderer {
        val renderer = PuckRenderer(theme)
        val style = if (type == BallType.Random && existingRoll != null)
            buildRandom(renderer, existingRoll)
        else
            buildBallStyle(type, renderer)
        renderer.attach(style.skin, style.tail, style.effect)
        return renderer
    }

    fun displayName(type: BallType): String = type.name

    /** Returns the unlock threshold percentage, or null if always free. */
    fun unlockThreshold(type: BallType): Int? = when (type) {
        BallType.Classic, BallType.Chicken -> null
        BallType.Neon    -> 10
        BallType.Ghost   -> 20
        BallType.Fire    -> 30
        BallType.Ice     -> 40
        BallType.Galaxy  -> 50
        BallType.Spinner -> 60
        BallType.Metal   -> 70
        BallType.Pixel   -> 80
        BallType.Rainbow -> 90
        BallType.Prism, BallType.Plasma, BallType.Random -> 100
    }

    fun isUnlocked(type: BallType, unlockProgress: Int): Boolean {
        val threshold = unlockThreshold(type) ?: return true
        return unlockProgress >= threshold
    }

    fun all(): List<BallType> = BallType.entries
}
