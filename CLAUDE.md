# Pock — CLAUDE.md

## What This Is

**Pock** is a 2-player local-multiplayer mobile game for Android. Two players hold the phone together (one at each end, or flat on a table) and each uses one finger to control a circular "puck." The goal is to knock the opponent's puck into one of the score zones (top or bottom edge of screen). First player to 5 points wins.

The game is custom-rendered using Android `Canvas` — there are **no XML layouts for the game screen**, only for the main menu and settings. All game drawing is done frame-by-frame in `onDraw`.

## Architecture

### Package Structure

| Package | Purpose |
|---|---|
| `com.example.puck` | Activities + `GameView` base class |
| `enums` | All state enums (GameState, FingerState, TouchState, MotionStates, Direction, MenuSelection, TutorialState) |
| `gameobjects` | `Player`, `Puck`, `Settings` (global config singleton), `PauseMenu` |
| `physics` | `Force` (direction + power vector), `Point` (2D vector/position), `Ticker` (countdown/countup timer), `TutorialTicker` |
| `shapes` | `Shape` (base), `Circle`, `DrawablePoint`, `Explosion`, `ScoreExplosion`, `HandSelection`, `TutorialBox`, `Collision` |
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
2. **Target SDK too low** — Now fixed to 35, but check Play Store current minimum requirement when submitting. Google requires targetSdk ≥ 34 as of 2024.
3. **All ad unit IDs are test IDs** — Replace before launch:
   - `MainActivity.kt:80` — rewarded ad test ID (TODO comment already there)
   - `MainActivity.kt:81` — commented-out live ad ID; verify `ca-app-pub-1111532606958888/6682727846` is correct
   - `GameActivity.kt:153` — interstitial test ID
   - `strings.xml:31` — `interstitial_ad_unit_id` is test ID
4. **AdMob Application ID** — `AndroidManifest.xml:22` has `ca-app-pub-1111532606958888~7923000787`; verify this is the real production app ID.
5. **`kotlin-android-extensions` is deprecated** — Currently kept to compile; must migrate to `viewBinding` before Kotlin 2.0 upgrade. Files using synthetics: `MainActivity.kt` (AdRatioText, rewardedAdButton), `Ads.kt` (next_level_button, level).
6. **Google Ads SDK 19.3.0 is very old** — `InterstitialAd` constructor-form API and `RewardedAd`/`RewardedAdCallback` were replaced in SDK 20.0. Migration is required; new API uses static `InterstitialAd.load()` with callbacks.

### Bugs

7. **`bounce_bonus` preference key mismatch** — `root_preferences.xml` defines key `bounce_bonuse` (extra 'e'), but `Storage.kt` reads key `bounce_bonus`. The bounce bonus setting never actually takes effect.
8. **`twoChargeCollisionId` never loaded** — `Sounds.initialize()` never calls `soundPool.load()` for this ID, so `playDoubleChargeCollision()` silently plays nothing (ID=0).
9. **`teleportId` never loaded** — Same issue; both `playTeleportStart()` and `playTeleportFinish()` use an ID of 0.
10. **`getYRate()` can throw ArrayIndexOutOfBoundsException** — Bounds check is commented out. If `y` is outside the 6-cell range, it returns an index > 5 which crashes the rates array lookup.
11. **`MediaPlayer` leak in `Sounds`** — `playGameAmbiance()` and `playMenuAmbiance()` create new `MediaPlayer` instances without releasing the old one first. Each navigation between menu and game leaks a player.
12. **Score text hardcoded pixel positions** — `Drawing.drawScore()` uses hardcoded `30f` and `90f` pixel offsets. These should use `screenRatio`.

### Unfinished Features

13. **Teleport mechanic is disabled** — The teleport system (puck disappears and reappears at finger location when hitting a score-zone wall) is fully implemented in `Player.kt` (`drawTeleport`, `prepareToTeleport`, `stopTeleportation`, teleport tickers) but the trigger lines in `Player.shouldBounce()` are commented out. Uncomment the `preparingToTeleport = true` lines to re-enable.
14. **Standing-still charge bonus is disabled** — The sweet-spot "bonus movement" mechanic (holding still charges a bonus) is fully implemented but commented out in `Player.drawTo()` (lines ~146–164). `bonusMovement` field in `Puck.kt` exists for this.
15. **Tutorial `ChargeBonusCanceledExplain` never triggers** — `TutorialView.playGame()` tracks `collisions` and shows `ChargeExplain` at 5 collisions, but `ChargeBonusCanceledExplain` is never triggered anywhere.
16. **Social share buttons have no functionality** — `activity_main.xml` has Facebook, Twitter, TikTok buttons. Facebook's `onClick="loadAds"` just reloads ads. Twitter and TikTok have no click handler at all.
17. **`Ads.kt` is scaffolding** — This activity is a copy of the AdMob sample "interstitial ad" template. It's reachable from `MainActivity.goToAds()` but the button is disconnected. Either wire it up or remove it.
18. **`StingerTransition.kt`** — A screen transition animation class that bounces a circle up and down. It's never used anywhere in the app. Incomplete; `transitionTo()` is empty.
19. **`Test.kt`** — Contains test/scratch code. Should be removed before ship.
20. **Title text mismatch** — `activity_main.xml` title TextView shows "Pockey" but the app is called "Pock".
21. **`PauseMenu` icon positions** — Settings, Reset, and Back icons in the pause menu (`PauseMenu.kt`) have swapped positions relative to what `Logic.menuCallback()` expects for left/right/middle touch zones. The touch detection zones in `Logic.onTouchEvent()` map: left → settings, middle → reset, right → back — but visual icon placement should be verified.
22. **`Settings.gameOver` reset** — After `GameOver` state resets via `victoryTicker`, `Settings.gameOver` is reset inside `Logic.gameOver()` but the call chain through `Logic.scored()` also checks it. Verify no edge case where it stays `true` across rounds.

### Polish / Pre-launch

23. **App icon** — Currently using default Android Studio launcher icon.
24. **Play Store listing assets** — Screenshots, feature graphic, description needed.
25. **Privacy policy** — Required for Play Store if collecting data (AdMob requires this).
26. **`versionCode`** — Starts at 1; increment on each Play Store upload. Managed here in `app/build.gradle`.

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
| play-services-ads | 19.3.0 (old API — see issue #6) |

## File Naming

- Activities: PascalCase (except `tutorial.kt` which is lowercase — inconsistency, do not normalize mid-project)
- Utility singletons: PascalCase object names in `utility/` package
- Enums: PascalCase enum class, PascalCase values (exception: `MenuSelection.none` is lowercase — intentional)
