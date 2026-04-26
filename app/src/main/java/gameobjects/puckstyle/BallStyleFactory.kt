package gameobjects.puckstyle

import enums.BallType
import gameobjects.puckstyle.paddles.*
import gameobjects.puckstyle.skins.*
import gameobjects.puckstyle.tails.*
import utility.Logic.highPlayer

data class BallStyle(val skin: PuckSkin, val tail: TailRenderer, val effect: LaunchEffect)

object BallStyleFactory {

    fun buildStyle(type: BallType, theme: ColorTheme, renderer: PuckRenderer): BallStyle {
        val ballStyle = when (type) {
            BallType.Classic -> BallStyle(ClassicSkin(theme, renderer), ClassicTail(theme, renderer), ClassicLaunch(theme, renderer))
            BallType.Neon    -> BallStyle(NeonSkin(theme, renderer),    NeonTail(theme, renderer),    NeonLaunch(theme, renderer))
            BallType.Ghost   -> BallStyle(GhostSkin(theme, renderer),   GhostTail(theme, renderer),   GhostLaunch(theme, renderer))
            BallType.Fire    -> BallStyle(FireSkin(theme, renderer),    FireTail(theme, renderer),    FireLaunch(theme, renderer))
            BallType.Ice     -> BallStyle(IceSkin(theme, renderer),     IceTail(theme, renderer),     IceLaunch(theme, renderer))
            BallType.Galaxy  -> BallStyle(GalaxySkin(theme, renderer),  GalaxyTail(theme, renderer),  GalaxyLaunch(theme, renderer))
            BallType.Spinner -> BallStyle(SpinnerSkin(theme, renderer), SpinnerTail(theme, renderer), SpinnerLaunch(theme, renderer))
            BallType.Metal   -> BallStyle(MetalSkin(theme, renderer),   MetalTail(theme, renderer),   MetalLaunch(theme, renderer))
            BallType.Pixel   -> BallStyle(PixelSkin(theme, renderer),   PixelTail(theme, renderer),   PixelLaunch(theme, renderer))
            BallType.Rainbow -> BallStyle(RainbowSkin(theme, renderer), RainbowTail(theme, renderer), RainbowLaunch(theme, renderer))
            BallType.Prism   -> BallStyle(PrismSkin(theme, renderer),   PrismTail(theme, renderer),   PrismLaunch(theme, renderer))
            BallType.Plasma   -> BallStyle(PlasmaSkin(theme, renderer),   PlasmaTail(theme, renderer),   PlasmaLaunch(theme, renderer))
            BallType.Chicken  -> BallStyle(ChickenSkin(theme, renderer),  ChickenTail(theme, renderer),  ChickenLaunch(theme, renderer))
            BallType.Random   -> buildRandomStyle(theme, renderer)
        }
        renderer.skin = ballStyle.skin
        renderer.tail = ballStyle.tail
        renderer.effect = ballStyle.effect
        return ballStyle
    }

    private fun buildRandomStyle(theme: ColorTheme, renderer: PuckRenderer): BallStyle {
        val pool = BallType.entries.filter { it != BallType.Random }
        val skin   = buildStyle(pool.random(), theme, renderer).skin
        val tail   = buildStyle(pool.random(), theme, renderer).tail
        val effect = buildStyle(pool.random(), theme, renderer).effect
        val tailZ  = if (kotlin.random.Random.nextBoolean()) -1 else 1
        return BallStyle(skin, ZIndexTailWrapper(tail, tailZ), effect)
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
