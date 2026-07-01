# AGENTS.md — commonMain (shared game logic)

Scope: everything that runs on both platforms. Project-wide context (KMP layout,
key singletons, conventions, charge/physics) is in the root `AGENTS.md`. This
file covers the cross-cutting commonMain systems and points to the more specific
nested files.

## What lives here

```
commonMain/kotlin/
  com/runoutzone/pockpock/  ← AppRoot, all screen composables, UI    → see its AGENTS.md
  enums/                    ← all state enums (GameState, TouchState, MotionStates,
                              Direction, MenuSelection, TutorialState, BallType)
  gameobjects/              ← Player, Puck, Settings, PauseMenu, BotBrain, BotConfig
    puckstyle/              ← ball rendering composition + unlock model → see its AGENTS.md
  physics/                  ← Force, Point, Ticker, TutorialTicker
  shapes/                   ← Circle, DrawablePoint, Explosion, ScoreExplosion,
                              FlashBurst, FlashTuning, BallSelectionPopup, TutorialBox
  utility/                  ← Logic, Drawing, ScoreDial, Effects, PaintBucket, Sounds,
                              SoundSpatializer, Storage, PlatformStorage, GameLoop,
                              GameEvents, Signal, PurchaseManager, ShareHelper
composeResources/           ← strings (values + locale variants), fonts, drawables, files
```

`Logic`, `Drawing`, `Settings`, `PaintBucket`, `Storage`, `Sounds`, `GameEvents`
are all object singletons — see root `AGENTS.md` for their responsibilities. The
`expect` declarations (`DrawingBridge`, `TouchBridge`, `GameLoop`,
`PlatformStorage`, `Sounds`) are declared here; their `actual`s live in
`androidMain`/`iosMain`.

## Score Dial & Pause Menu

The on-screen score is a **half-disc "score dial"** on a side edge (not an in-goal readout), owned by `utility/ScoreDial.kt`. It draws from `Drawing.drawFrame` as two passes — normal (behind the pucks) and lifted (above the score cinematic) — plus a top-most `drawPauseMenu`. Top quarter = high score, bottom = low; fills use the player's `theme.main.primary`→`secondary`. Hidden during `BallSelection` and demo mode. Side is `Settings.scoreMenuSide` (`enums.ScoreMenuSide`, default Left, set in Settings → Graphics). Tapping the dial expands it into a Return/Restart pause menu (freezes via `Logic.paused`). On a score, `gameobjects/puckstyle/ScoredPaddle.kt` flies the loser's paddle along a Bézier into the winner's number, which spins only on arrival (`Logic.tossHigh`/`tossLow`, gated in `Logic.scored()`). The old in-goal `drawScores` and the Score Placement screen are gone.

## Sound Design

Sounds are spatialized via `SoundSpatializer` (shared): a 6-zone pitch grid (`rates` array). `getXRate(x)` / `getYRate(y)` map screen position to a pitch rate. Both platform `Sounds` implementations call these. (Platform audio engines: see `androidMain`/`iosMain` AGENTS.md.)

## GameEvents signal bus

`GameEvents` (`utility`) is a simple signal bus: `canScore`, `cantScore`, `gameOver`, `gameReset`. Use `Signal<T>.connect/disconnect/emit`. Prefer this over ad-hoc callbacks for game-state notifications.
