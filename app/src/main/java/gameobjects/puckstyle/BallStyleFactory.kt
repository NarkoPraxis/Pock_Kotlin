package gameobjects.puckstyle

import enums.BallType
import gameobjects.puckstyle.skins.*
import gameobjects.puckstyle.tails.*

object BallStyleFactory {

    fun build(type: BallType, theme: ColorTheme): Pair<PuckSkin, TailRenderer> {
        return when (type) {
            BallType.Classic -> ClassicSkin(theme) to ClassicTail(theme)
            BallType.Neon    -> NeonSkin(theme)    to NeonTail(theme)
            BallType.Ghost   -> GhostSkin(theme)   to GhostTail(theme)
            BallType.Fire    -> FireSkin(theme)    to FireTail(theme)
            BallType.Ice     -> IceSkin(theme)     to IceTail(theme)
            BallType.Galaxy  -> GalaxySkin(theme)  to GalaxyTail(theme)
            BallType.Spiral  -> SpiralSkin(theme)  to SpiralTail(theme)
            BallType.Metal   -> MetalSkin(theme)   to MetalTail(theme)
            BallType.Pixel   -> PixelSkin(theme)   to PixelTail(theme)
            BallType.Rainbow -> RainbowSkin(theme) to RainbowTail(theme)
            BallType.Prism   -> PrismSkin(theme)   to PrismTail(theme)
            BallType.Plasma  -> PlasmaSkin(theme)  to PlasmaTail(theme)
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
