# Pock — CLAUDE.md

## What This Is

**Pock** is a 2-player local-multiplayer mobile game for Android. Two players hold the phone together (one at each end, or flat on a table) and each uses one finger to control a circular "puck." The goal is to knock the opponent's puck into one of the score zones (top or bottom edge of screen). First player to 5 points wins.

The game is custom-rendered using Android `Canvas` — there are **no XML layouts for the game screen**, only for the main menu and settings. All game drawing is done frame-by-frame in `onDraw`.

## Plans & Workflow

Plans are stored in `Plans/`. When a plan is completed, **prepend `✅` to the filename and move it to `Plans/Finished/`** immediately — do this automatically without waiting to be asked. Example: `07-move-ads-remaining-label.md` → `Plans/Finished/✅07-move-ads-remaining-label.md`.

---

## Architecture

### Package Structure

| Package | Purpose |
|---|---|
| `com.example.puck` | Activities (`MainActivity`, `PlayView`, `GameActivity`, `TutorialView`, `BallUnlockActivity`) + `GameView` base class |
| `enums` | All state enums (GameState, FingerState, TouchState, MotionStates, Direction, MenuSelection, TutorialState) |
| `gameobjects` | `Player`, `Puck`, `Settings` (global config singleton), `PauseMenu` |
| `gameobjects/puckstyle` | Ball-style composition: `PuckSkin`, `TailRenderer`, `BallStyleFactory`, `ColorTheme`, `Palette`; `skins/` and `tails/` subpackages (one file per ball type) |
| `physics` | `Force` (direction + power vector), `Point` (2D vector/position), `Ticker` (countdown/countup timer), `TutorialTicker` |
| `shapes` | `Shape` (base), `Circle`, `DrawablePoint`, `Explosion`, `ScoreExplosion`, `HandSelection`, `TutorialBox`, `Collision`, `BallSelectionCard`, `BallSelectionPopup` |
| `utility` | `Logic` (game loop logic singleton), `Drawing` (rendering singleton), `Effects` (particle system), `PaintBucket` (all `Paint` objects), `Sounds` (audio singleton), `Storage` (SharedPreferences), `Tutorial` (tutorial flow singleton) |

### Key Singletons

- **`Settings`** (`gameobjects`) — All runtime configuration: screen dimensions, ball size, speed, score state, game phase. Set once from `Storage` in `Logic.initializeSettings()`.
- **`Logic`** (`utility`) — All game logic: player initialization, collision detection, touch routing, game state transitions, pause menu actions.
- **`Drawing`** (`utility`) — All canvas drawing: arena, walls, players, scores, countdown rectangles.
- **`PaintBucket`** (`utility`) — All `Paint` objects. Initialized from `Resources` in `PaintBucket.initialize()` after screen size is known.
- **`Sounds`** (`utility`) — `SoundPool` for SFX + `MediaPlayer` for ambient music.
- **`Tutorial`** (`utility`) — Tutorial state machine. Chains `TutorialBox` objects in a linked list.

### Game Loop

The game loop runs in `PlayView.startPlayers()` via `Handler.postDelayed` at `Settings.refreshRate` ms (default 16 ms ≈ 60 fps). The game state machine (`Settings.gameState`) drives what logic runs each tick:

```
FingerSelection → CountDown → Play → Scored → CountDown → ... → GameOver → FingerSelection
```

### Coordinate System / Screen Layout

```
┌────────────────────┐  y=0
│   HIGH score zone  │  (topGoalBottom = screenRatio * 3)
├────────────────────┤  y=topGoalBottom
│                    │
│   Play area        │
│                    │
├────────────────────┤  y=bottomGoalTop
│   LOW score zone   │
└────────────────────┘  y=screenHeight
```

- **highPlayer** controls the top half; **lowPlayer** controls the bottom half.
- Screen is mirrored 180° for the high player — all drawing for high player uses `canvas.scale(-1, -1)` around screen center. This means the high player's view is naturally upright from their side of the phone.
- `Settings.screenRatio` = `min(width, height) / 20` — used as the universal unit for all sizes.

### Physics

- `Force` = direction (`Point`, normalized) + `power` (scalar). Two forces per puck: `movement` (charge-release) and `launch` (collision). Combined each frame in `Puck.getNextDirection()`.
- Friction applied every frame via `Force.applyFriction(Settings.friction)`.
- Bounce: when puck would cross a wall, direction component is reflected. If inside a score zone and launched, the full arena boundaries apply (puck can enter goal zones mid-flight).

### Finger Selection System

Before each round, players choose a finger (Left Thumb, Right Thumb, Left Pointer, Right Pointer) from `HandSelection` circles. This sets `FingerState`, which affects how raw touch coordinates are `transform()`ed into puck-movement coordinates in `Logic.transform()`. Thumb fingers have more exaggerated mapping (scale factors 2x and 3.5x); pointer fingers use a quadratic mapping on x.

### Charge System

1. Hold finger down → `charge` increases from `chargeStart (10)` up to `sweetSpotMax (50)`.
2. Sweet spot: `sweetSpotMin (40)` to `sweetSpotMax (50)` — releasing here sets `shielded = true`.
3. Overcharge: `charge >= sweetSpotMax` → resets to half and locks (`chargePowerLocked = true`) until released.
4. Shielded puck: when it collides with an unshielded puck, the shielded puck wins — opponent launches at max + bonus, shielded puck barely moves.
5. Both shielded: both shields cancel, both launch at their respective stored powers.

### Ball Types System

12 `BallType` enum values: Classic, Neon, Ghost, Fire, Ice, Galaxy, Spiral, Metal, Pixel, Rainbow, Prism, Plasma.

- **Composition**: each puck has a `PuckSkin` (handles fill/stroke drawing) and a `TailRenderer` (handles trail). `BallStyleFactory` creates the correct skin+tail for a given `BallType`.
- **Color themes**: `ColorTheme` holds primary/secondary/accent colors per type. Warm theme = high player; cold theme = low player.
- **Unlock rule** in `BallStyleFactory.isUnlocked`: Classic always free; indices 1–9 unlock at `adsLeft ≤ 100 - ordinal*10`; Prism + Plasma both unlock at `adsLeft == 0`.
- **Persistence**: `Storage.saveHighBallType`/`saveLowBallType` in `ad` SharedPreferences. Loaded in `Logic.initializeSettings()` AND `Logic.resetGame()`.
- **In-game selection**: `BallSelectionCard` shown in each goal during FingerSelection. Tapping opens `BallSelectionPopup` — a horizontal drag-scroll strip across the goal width. High popup is positioned below `topGoalBottom`; low popup above `bottomGoalTop`. Touch routing via `Logic.interceptBallMenu`.
- **Main-menu unlock screen**: `BallUnlockActivity` + `BallUnlockView` — 2×6 vertically scrollable animated preview grid with lock overlays. "Watch Ad to Unlock" decrements `adsLeft` by 2 per reward.
- **Preview convention**: always call `previewPuck.setFill(theme.primary); previewPuck.setStroke(theme.secondary)` before `drawTo()`, or Classic renders with stale PaintBucket colors.
- **Mirror drag**: for the mirrored high-player popup, drag delta must be inverted: `logicalX = 2*cx - screenX`.

### Sound Design Convention

Sounds are spatialized using a 6-zone pitch grid (`rates` array in `Sounds`). Horizontal position → pitch rate from `getXRate()`, vertical position → rate from `getYRate()`. This gives positional audio flavor without spatial audio APIs.

---

## Conventions You Use

- **Global objects over passing context**: `Settings`, `Logic`, `Drawing`, `PaintBucket`, `Sounds`, `Tutorial`, `Effects` are all `object` singletons. Don't refactor these into DI or constructor-injected classes — the design is intentional.
- **Mirror everything**: The game renders content upside-down for the top player. All drawing that must be readable from both sides uses `canvas.scale(-1f, -1f, middleX, middleY)` then draws again. See `Drawing.mirrorText()` and `Drawing.drawScores()`.
- **`screenRatio` is the unit**: All sizes (ball radius, wall thickness, particle sizes, text, etc.) are multiples of `Settings.screenRatio`. Never hardcode pixel sizes.
- **`Ticker`**: The game's timing primitive. Ascending tickers count up to a max and expose `ratio` (0.0–1.0) and `finished`. Use ascending tickers for progress animations, descending for countdowns.
- **Touch routing**: Single-touch goes to whichever player's half the touch is in. Multi-touch: pointer 0 and pointer 1 are mapped to high/low based on `highTouchedFirst` (which player put their finger down first). This is set in `ACTION_DOWN` handling.
- **Player symmetry**: `highPlayer` and `lowPlayer` are always both initialized. Actions on one almost always have a parallel action on the other.

---

## Outstanding Issues / Needs Before Ship

### Critical (Play Store blockers)

1. **`applicationId "com.example.puck"`** — Must be changed to a real reverse-domain ID before Play Store submission. This changes the identity of the app permanently.
2. **All ad unit IDs are test IDs** — Replace before launch:
   - `MainActivity.kt` — rewarded ad test ID (TODO comment already there); verify live ID `ca-app-pub-1111532606958888/6682727846`
   - `GameActivity.kt` — interstitial test ID
   - `strings.xml` — `interstitial_ad_unit_id` is test ID
3. **AdMob Application ID** — `AndroidManifest.xml` has `ca-app-pub-1111532606958888~7923000787`; verify this is the real production app ID.
4. **`kotlin-android-extensions` is deprecated** — Currently kept to compile; must migrate to `viewBinding` before Kotlin 2.0 upgrade. Files using synthetics: `MainActivity.kt` (AdRatioText, rewardedAdButton), `Ads.kt` (next_level_button, level).
5. **Google Ads SDK 19.3.0 is very old** — `InterstitialAd` constructor-form API and `RewardedAd`/`RewardedAdCallback` were replaced in SDK 20.0. Migration is required; new API uses static `InterstitialAd.load()` with callbacks. See `Plans/14-update-admob-sdk.md`.

### Bugs

1. **`Player.puckFillColor` is stale after `setPuckColor()` is called** — `puckFillColor` is set once at construction and referenced in `drawTail()` for the non-launched, non-shielded tail color. `Logic.setPuckColor()` → `puck.setFill()` updates `puck.fillColor` but not `Player.puckFillColor`. During the brief Scored state, the tail will briefly show the wrong color.
2. **`alwaysBlackTextPaint` text size is hardcoded at `120f` pixels** — `PaintBucket.alwaysBlackTextPaint` and `textPaint` both use `textSize = 120f`, a raw pixel value. Should use a `screenRatio`-based size so score numbers scale properly on different screen densities.
3. **Score text position may clip for double-digit scores** — `Drawing.drawScore()` uses a `screenRatio / 2`-based x-offset, which is tight for two-digit score strings at large text sizes.
4. **`twoChargeCollisionId` and `teleportId` have no dedicated sound assets** — Placeholder sounds are used (`sheilded_collision` and `charge_activated`). Proper audio assets (`two_charge_collision.mp3`, `teleport.mp3`) should be added to `res/raw/`.
5. **`Sounds` cell dimensions not recalculated after `initializeGame()`** — `cellWidth` and `cellHeight` are computed at object-declaration time when `Settings` fields are all `0f`. Currently safe because sounds are not called during FingerSelection, but fragile.
6. **`checkScored()` assigns wrong colors on score** — Swaps puck colors during Scored state regardless of which player is actually high or low. `resetPlayerStates()` restores both. Appears intentional (brief flash) but verify no edge case leaves colors permanently swapped on GameOver.
7. **`finger` circle movement wastes calculations during FingerSelection** — `Logic.assignFingerLocation()` sets `fingerTargetLocation = player.puck`; circles are never visible here but movement calculations run every frame.
8. **`Sounds.initialize()` vs `Sounds.initializeGame()` are two separate init paths** — If `initializeGame()` is ever skipped, pitch spatialization silently uses wrong cell dimensions.

### Unfinished Features

1. **Teleport mechanic is disabled** — Fully implemented in `Player.kt` (`drawTeleport`, `prepareToTeleport`, `stopTeleportation`, teleport tickers) but trigger lines in `Player.shouldBounce()` are commented out. Uncomment the `preparingToTeleport = true` lines to re-enable.
2. **Standing-still charge bonus is disabled** — Fully implemented but commented out in `Player.drawTo()` (lines ~146–164). `bonusMovement` field in `Puck.kt` exists for this.
3. **Tutorial `ChargeBonusCanceledExplain` never triggers** — `TutorialView.playGame()` tracks `collisions` and shows `ChargeExplain` at 5 collisions, but `ChargeBonusCanceledExplain` is never triggered anywhere.
4. **`Ads.kt` is scaffolding** — Copy of AdMob sample template. Reachable from `MainActivity.goToAds()` but the button is disconnected. Either wire it up or remove it.
5. **`StingerTransition.kt`** — Screen transition animation (circle bounces up/down) that's never used. `transitionTo()` is empty.
6. **`Test.kt`** — Contains test/scratch code. Remove before ship.
7. **`PauseMenu` icon positions** — Settings, Reset, and Back icons in the pause menu (`PauseMenu.kt`) may have swapped positions relative to what `Logic.menuCallback()` expects for left/right/middle touch zones. Verify visual placement matches touch detection zones.
8. **`Settings.gameOver` reset** — After `GameOver` state resets via `victoryTicker`, verify no edge case where `Settings.gameOver` stays `true` across rounds through the `Logic.scored()` call chain.

### Polish / Pre-launch

1. **Custom app launcher icon** — Currently using the default Android Studio adaptive icon. A custom icon is needed before Play Store submission.
2. **Play Store listing assets** — Screenshots, feature graphic, description needed.
3. **Privacy policy** — Required for Play Store if collecting data (AdMob requires this).
4. **`versionCode`** — Starts at 1; increment on each Play Store upload. Managed in `app/build.gradle`.

---

## Build Stack (Current)

| Tool | Version |
|---|---|
| Gradle Wrapper | 8.9 |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 1.9.24 |
| compileSdk | 35 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 |
| Java | 17 |
| play-services-ads | 19.3.0 (old API — see blocker #5) |

## File Naming

- Activities: PascalCase (except `tutorial.kt` which is lowercase — inconsistency, do not normalize mid-project)
- Utility singletons: PascalCase object names in `utility/` package
- Enums: PascalCase enum class, PascalCase values (exception: `MenuSelection.none` is lowercase — intentional)
