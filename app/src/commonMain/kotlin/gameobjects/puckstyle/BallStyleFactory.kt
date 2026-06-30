package gameobjects.puckstyle

import enums.BallType
import gameobjects.puckstyle.paddles.*
import gameobjects.puckstyle.skins.*
import gameobjects.puckstyle.tails.*
import utility.Effects

data class BallStyle(val skin: PuckSkin, val tail: TailRenderer, val effect: PaddleLaunchEffect)

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
//            BallType.Chicken  -> BallStyle(ChickenSkin(renderer),  ChickenTail(renderer),  ChickenLaunch(renderer))
            BallType.PokPok   -> BallStyle(PokPokSkin(renderer),   ChickenTail(renderer),  ChickenLaunch(renderer))
            BallType.Dragon   -> BallStyle(DragonSkin(renderer),  DragonTail(renderer),   FireLaunch(renderer))
            BallType.Axolotl  -> BallStyle(AxolotlSkin(renderer), AxolotlTail(renderer),  BubbleLaunch(renderer))
            BallType.Cat      -> BallStyle(CatSkin(renderer),     CatTail(renderer),      RainbowLaunch(renderer))
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
//            BallType.Chicken  -> ChickenSkin(renderer)
            BallType.PokPok   -> PokPokSkin(renderer)
            BallType.Dragon   -> DragonSkin(renderer)
            BallType.Axolotl  -> AxolotlSkin(renderer)
            BallType.Cat      -> CatSkin(renderer)
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
//            BallType.Chicken  -> ChickenTail(renderer)
            BallType.PokPok   -> ChickenTail(renderer)
            BallType.Dragon   -> DragonTail(renderer)
            BallType.Axolotl  -> AxolotlTail(renderer)
            BallType.Cat      -> CatTail(renderer)
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
//            BallType.Chicken  -> ChickenLaunch(renderer)
            BallType.PokPok   -> ChickenLaunch(renderer)
            BallType.Dragon   -> FireLaunch(renderer)
            BallType.Axolotl  -> BubbleLaunch(renderer)
            BallType.Cat      -> RainbowLaunch(renderer)
            BallType.Random   -> buildPaddle(BallType.entries.random(), renderer)
        }
    }

    /**
     * Spawn the score celebration that matches a PADDLE's design (not the live skin) at [position] —
     * used by the score toss ([ScoredPaddle]) when the flung paddle lands on the dial number. The
     * burst a design "releases on teleporting" lives in the skin that naturally pairs with the
     * paddle, so we replay that skin's [PuckSkin.onUsedToScore] built against the paddle's own
     * [PaddleLaunchEffect.renderer]. Paddle classes shared across balls collapse to the design they
     * belong to (FireLaunch → Fire even on Dragon, RainbowLaunch → Rainbow even on Cat, ChickenLaunch
     * → PokPok). [BubbleLaunch] has no 1:1 skin, so it fires its own self-contained companion burst.
     * Only runs on a score (rare), so the one-off skin build is not a per-frame cost.
     */
    fun spawnPaddleScoreCelebration(effect: PaddleLaunchEffect, position: physics.Point, otherColor: Int, highGoal: Boolean) {
        // Divert every burst this spawns into Effects.scoreEffects so the dial draws them in its own
        // layer (in front of the dial face, behind the number). Reset in finally so the next ordinary
        // effect spawn (e.g. the pierced-ball pop burst) routes normally.
        Effects.routeToScoreEffects = true
        try {
            spawnPaddleScoreCelebrationInner(effect, position, otherColor, highGoal)
        } finally {
            Effects.routeToScoreEffects = false
        }
    }

    private fun spawnPaddleScoreCelebrationInner(effect: PaddleLaunchEffect, position: physics.Point, otherColor: Int, highGoal: Boolean) {
        val r = effect.renderer
        if (effect is BubbleLaunch) {
            BubbleLaunch.spawnBubbleBurst(
                position.x, position.y, r.radius,
                r.bakedPrimary(r.theme.main.primary), r.bakedSecondary(r.theme.main.secondary), r.theme.isWarm
            )
            return
        }
        val skinType = when (effect) {
            is NeonLaunch    -> BallType.Neon
            is GhostLaunch   -> BallType.Ghost
            is FireLaunch    -> BallType.Fire
            is IceLaunch     -> BallType.Ice
            is GalaxyLaunch  -> BallType.Galaxy
            is SpinnerLaunch -> BallType.Spinner
            is MetalLaunch   -> BallType.Metal
            is PixelLaunch   -> BallType.Pixel
            is RainbowLaunch -> BallType.Rainbow
            is PrismLaunch   -> BallType.Prism
            is PlasmaLaunch  -> BallType.Plasma
            is ChickenLaunch -> BallType.PokPok
            else             -> BallType.Classic
        }
        buildSkin(skinType, r).onUsedToScore(otherColor, position, highGoal)
    }

    fun buildCustomBall(config: CustomBallConfig, renderer: PuckRenderer): BallStyle {
        val skin = buildSkin(config.skinType, renderer)
        val tail = buildTail(config.tailType, renderer)
        val paddle = buildPaddle(config.paddleType, renderer)
        return BallStyle(skin, tail, paddle)
    }

    /**
     * Composes a [CustomBallConfig] for the given component types, reading each component's own
     * declared [zIndex] as its draw-order rank. The ranks are read live off freshly built
     * components — never hardcoded — so a slot seeded from this renders in exactly the same layer
     * order as the corresponding built-in ball. Used to pre-populate the free default slots.
     */
    fun naturalCustomConfig(skinType: BallType, tailType: BallType, paddleType: BallType): CustomBallConfig {
        val probe = PuckRenderer(ColorTheme.Cold)
        return CustomBallConfig(
            skinType = skinType, tailType = tailType, paddleType = paddleType,
            skinZRank   = buildSkin(skinType, probe).zIndex,
            tailZRank   = buildTail(tailType, probe).zIndex,
            paddleZRank = buildPaddle(paddleType, probe).zIndex
        )
    }

    fun buildCustomRenderer(config: CustomBallConfig, theme: ColorTheme): PuckRenderer {
        val renderer = PuckRenderer(theme)
        val style = buildCustomBall(config, renderer)
        renderer.attach(style.skin, style.tail, style.effect, config.skinZRank, config.tailZRank, config.paddleZRank)
        return renderer
    }

    fun buildSkinOnlyRenderer(skinType: BallType, theme: ColorTheme): PuckRenderer {
        val renderer = PuckRenderer(theme)
        val skin = buildSkin(skinType, renderer)
        val tail = InvisibleTail(renderer)
        // Skin-only previews must draw ONLY the skin. Don't build the skin's real paddle:
        // Galaxy/Spinner paddles are `alwaysVisible`, so PuckRenderer.draw() would render them
        // even with effectEnabled = false, leaking paddle art into the skin carousel.
        val paddle = InvisiblePaddle(renderer)
        renderer.attach(skin, tail, paddle)
        return renderer
    }

    /** Tail-only preview renderer (invisible skin + invisible paddle), for composable carousels. */
    fun buildTailOnlyRenderer(tailType: BallType, theme: ColorTheme): PuckRenderer {
        val renderer = PuckRenderer(theme)
        renderer.attach(InvisibleSkin(renderer), buildTail(tailType, renderer), InvisiblePaddle(renderer))
        return renderer
    }

    /** Paddle-only preview renderer (invisible skin/tail), frozen at zero charge for static preview. */
    fun buildPaddleOnlyRenderer(paddleType: BallType, theme: ColorTheme): PuckRenderer {
        val renderer = PuckRenderer(theme)
        renderer.attach(InvisibleSkin(renderer), InvisibleTail(renderer), buildPaddle(paddleType, renderer))
        // Do NOT prime the charge: cbcCarouselMode already makes the idle paddle draw, and because
        // chargeStart == 0 the first increaseCharge() jumps to a non-zero fill, leaving the static
        // preview looking "a little bit charged". Stay at phase Idle / fill 0.
        renderer.effect.frozen = true
        renderer.effect.cbcCarouselMode = true
        return renderer
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

    // ── Unlock tiers ────────────────────────────────────────────────────────
    // Free: always unlocked.  Ad: each component individually unlockable via a
    // rewarded ad.  Premium: unlocked only when unlockProgress reaches 100%.
    enum class Tier { Free, Ad, Premium }

    val FREE_TYPES    = setOf(BallType.Classic, BallType.PokPok)
    // No skin/tail/paddle is gated behind 100% — every non-free component is individually
    // ad-unlockable. (The only 100%-completion gate left is the custom "any color" in the CCP.)
    val PREMIUM_TYPES = emptySet<BallType>()

    fun tierOf(type: BallType): Tier = when (type) {
        in FREE_TYPES    -> Tier.Free
        in PREMIUM_TYPES -> Tier.Premium
        else             -> Tier.Ad
    }

    fun all(): List<BallType> = BallType.entries
}
