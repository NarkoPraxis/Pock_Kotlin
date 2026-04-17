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
            BallType.Spiral  -> BallStyle(SpiralSkin(theme),  SpiralTail(theme),  SpiralLaunch(theme))
            BallType.Metal   -> BallStyle(MetalSkin(theme),   MetalTail(theme),   MetalLaunch(theme))
            BallType.Pixel   -> BallStyle(PixelSkin(theme),   PixelTail(theme),   PixelLaunch(theme))
            BallType.Rainbow -> BallStyle(RainbowSkin(theme), RainbowTail(theme), RainbowLaunch(theme))
            BallType.Prism   -> BallStyle(PrismSkin(theme),   PrismTail(theme),   PrismLaunch(theme))
            BallType.Plasma  -> BallStyle(PlasmaSkin(theme),  PlasmaTail(theme),  PlasmaLaunch(theme))
        }
    }

    fun displayName(type: BallType): String = type.name

    fun isUnlocked(type: BallType, unlockProgress: Int): Boolean {
        return when (type) {
            BallType.Classic -> true
            BallType.Prism, BallType.Plasma -> unlockProgress >= 100
            else -> unlockProgress >= type.ordinal * 10
        }
    }

    fun all(): List<BallType> = BallType.values().toList()
}
