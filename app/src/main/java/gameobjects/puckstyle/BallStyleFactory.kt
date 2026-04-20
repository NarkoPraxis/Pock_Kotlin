package gameobjects.puckstyle

import enums.BallType
import gameobjects.puckstyle.launcheffects.*
import gameobjects.puckstyle.skins.*
import gameobjects.puckstyle.tails.*

data class BallStyle(val skin: PuckSkin, val tail: TailRenderer, val effect: LaunchEffect)

object BallStyleFactory {

    fun buildStyle(type: BallType, theme: ColorTheme): BallStyle {
        return when (type) {
            BallType.Classic -> BallStyle(ClassicSkin(theme), ClassicTail(theme), ClassicLaunch(theme))
            BallType.Neon    -> BallStyle(NeonSkin(theme),    NeonTail(theme),    NeonLaunch(theme))
            BallType.Ghost   -> BallStyle(GhostSkin(theme),   GhostTail(theme),   GhostLaunch(theme))
            BallType.Fire    -> BallStyle(FireSkin(theme),    FireTail(theme),    FireLaunch(theme))
            BallType.Ice     -> BallStyle(IceSkin(theme),     IceTail(theme),     IceLaunch(theme))
            BallType.Galaxy  -> BallStyle(GalaxySkin(theme),  GalaxyTail(theme),  GalaxyLaunch(theme))
            BallType.Spinner -> BallStyle(SpinnerSkin(theme), SpinnerTail(theme), SpinnerLaunch(theme))
            BallType.Metal   -> BallStyle(MetalSkin(theme),   MetalTail(theme),   MetalLaunch(theme))
            BallType.Pixel   -> BallStyle(PixelSkin(theme),   PixelTail(theme),   PixelLaunch(theme))
            BallType.Rainbow -> BallStyle(RainbowSkin(theme), RainbowTail(theme), RainbowLaunch(theme))
            BallType.Prism   -> BallStyle(PrismSkin(theme),   PrismTail(theme),   PrismLaunch(theme))
            BallType.Plasma   -> BallStyle(PlasmaSkin(theme),   PlasmaTail(theme),   PlasmaLaunch(theme))
            BallType.Chicken  -> BallStyle(ChickenSkin(theme),  ChickenTail(theme),  ChickenLaunch(theme))
            BallType.Random   -> buildRandomStyle(theme)
        }
    }

    private fun buildRandomStyle(theme: ColorTheme): BallStyle {
        val pool = BallType.entries.filter { it != BallType.Random }
        val skin   = buildStyle(pool.random(), theme).skin
        val tail   = buildStyle(pool.random(), theme).tail
        val effect = buildStyle(pool.random(), theme).effect
        val tailZ  = if (kotlin.random.Random.nextBoolean()) -1 else 1
        return BallStyle(skin, ZIndexTailWrapper(tail, tailZ), effect)
    }

    fun displayName(type: BallType): String = type.name

    fun isUnlocked(type: BallType, unlockProgress: Int): Boolean {
        return when (type) {
            BallType.Classic, BallType.Chicken -> true
            BallType.Neon    -> unlockProgress >= 10
            BallType.Ghost   -> unlockProgress >= 20
            BallType.Fire    -> unlockProgress >= 30
            BallType.Ice     -> unlockProgress >= 40
            BallType.Galaxy  -> unlockProgress >= 50
            BallType.Spinner -> unlockProgress >= 60
            BallType.Metal   -> unlockProgress >= 70
            BallType.Pixel   -> unlockProgress >= 80
            BallType.Rainbow -> unlockProgress >= 90
            BallType.Prism, BallType.Plasma, BallType.Random -> unlockProgress >= 100
        }
    }

    fun all(): List<BallType> = BallType.values().toList()
}
