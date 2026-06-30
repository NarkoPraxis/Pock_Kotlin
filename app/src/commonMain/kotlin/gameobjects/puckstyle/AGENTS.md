# AGENTS.md — Ball Styles (gameobjects/puckstyle)

Scope: how a puck is rendered — skin + tail + paddle composition, the
per-component unlock model, color themes, and the paddle/charge-visual system.
This is the most nuanced rendering area; read it before touching anything in
`skins/`, `tails/`, or `paddles/`.

## Composition

`PuckRenderer` owns a `PuckSkin` (fill/stroke), a `TailRenderer` (trail), and a `PaddleLaunchEffect` (paddle + strike animation). `BallStyleFactory.buildRenderer()` creates the full renderer for a given type. Supporting files: `CachedBrushSkin`, `StaticTailPath`, `ZIndexTailWrapper`, `ColorTheme`, `Palette`, `ChargePhase`, `RandomRoll`, `CustomBallConfig`, `RainbowOverride`, `EggSplat`, `ScoredPaddle`.

Directory layout: `skins/` (one file per type), `tails/` (one file per type), `paddles/` (one file per type, named `*Launch.kt`).

## BallType enum

Values: Classic, Neon, Ghost, Fire, Ice, Galaxy, Spinner, Metal, Pixel, Rainbow, Prism, Plasma, PokPok, Dragon, Axolotl, Cat, Random.
- `Chicken` is a deprecated skin — `PokPok` replaces it, composed as `PokPokSkin` + `ChickenTail` + `ChickenLaunch`.
- `Spiral` is migrated to `Spinner` on read (`Storage`).

## Per-component unlock model (Monetization Overhaul)

Every skin, tail, and paddle is individually unlockable by watching a rewarded ad.
- `BallStyleFactory.tierOf(type)` returns `Free` (Classic, PokPok) or `Ad` (every other skin/tail/paddle, incl. Prism/Plasma/Random/Dragon/Axolotl/Cat). No skin/tail/paddle is gated behind 100%. `PREMIUM_TYPES` is intentionally empty.
- Per-component state lives in `Storage` (`unlocked_skins`/`tails`/`paddles` CSV sets) via `Storage.isSkinUnlocked` / `isTailUnlocked` / `isPaddleUnlocked`.
- **Blanket rule:** all helpers return true when `unlockProgress >= 100`. `Storage.unlockProgress` reads the real persisted value (0–100); the meter economy is `recordAdWatched` +2, share +10, `unlock_all` IAP→100. Each individual ad unlock also calls `recordAdWatched()`, so it bumps the meter.
- **New styles ship as `Ad`-unlockable** (the only 100%-completion gate left is the custom "any color" in the CCP).
- **Persistence**: `Storage.saveHighBallType` / `saveLowBallType`, loaded in `Settings.initializeForScreen`.

The ad-watching UI (CBC/CCP, `StyleOptionButton`, `AdUnlock`, popups) lives in `com/runoutzone/pockpock/` — see its AGENTS.md. Tap a locked option to unlock; scrolling never triggers anything.

## Color themes

`ColorTheme.getTheme(isHigh)` — warm theme for high player, cold for low. `RainbowOverride` toggles a per-target rainbow strobe, injected at `PuckRenderer.responsiveColorGroup`.

## Paddle System

Paddles live in `paddles/` (files named `*Launch.kt`). The directory `launcheffects/` referenced in old docs was renamed to `paddles/`.

- **`LaunchEffect` interface** + **`PaddleLaunchEffect` base class** define the paddle kinematics and strike animation. Subclasses override only visual drawing.
- **`ChargePhase`** (`Idle`, `Building`, `SweetSpot`, `Overcharged`) is computed from `Settings` constants in `PaddleLaunchEffect.updateState()`. Never hardcode charge thresholds in subclasses. (Charge mechanic itself: root `AGENTS.md`.)
- **`chargePaint` in `PuckRenderer`** is deprecated (replaced by paddle). Do not add logic to it.
- On a score, `ScoredPaddle.kt` flies the loser's paddle along a Bézier into the winner's score number.

## Critical drawing rules (apply to every skin/tail/paddle)

- **Tail length is fixed**: never vary tail length by charge or shield state — always `baseCount * Settings.tailLengthMultiplier`.
- **DrawScope rotation — use direct coordinates**: `withTransform { rotate(...) }` does not reliably rotate paths around the translated origin. Compute final screen coordinates with trig and draw in absolute screen space:
  ```kotlin
  val rad = angleDegrees * (Math.PI.toFloat() / 180f)
  val cosA = cos(rad); val sinA = sin(rad)
  fun sx(lx: Float, ly: Float) = renderer.x + lx * cosA - ly * sinA
  fun sy(lx: Float, ly: Float) = renderer.y + lx * sinA + ly * cosA
  ```
- **`screenRatio` is the unit** — never hardcode pixel sizes.
- **Preview convention**: `previewPuck.setFill(theme.primary); previewPuck.setStroke(theme.secondary)` before `drawTo()`.
- **Per-frame allocations**: skin/tail/paddle `draw` runs every frame. `Offset`/`Size`/`Color` are free value classes; hoist `Path`/`Brush`/`List`/lambda/`Random`/text allocations out of the draw loop. (The `ball-optimize` / `render-dealloc` agents handle single-file passes.)

For new cute-animal skins driven by SVG part files, use the `svg-animal-skin` skill rather than hand-computing paths.
