# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

**Pock** is a 2-player local-multiplayer mobile game targeting both Android and iOS. Two players hold the phone together and each uses one finger to control a circular "puck." The goal is to knock the opponent's puck into one of the score zones (top or bottom edge of screen). First to N points wins.

The project is a **Kotlin Multiplatform (KMP)** app using Compose Multiplatform for rendering. All game drawing is done frame-by-frame in a Compose `Canvas` via `DrawScope`; there are no XML layouts for the game screen.

## Plans & Workflow

Plans are stored in `Plans/<project_name>`. When a plan is completed, **prepend `✅` to the filename and move it to `Plans/<project_name>/Finished/`** immediately — do this automatically without waiting to be asked. Example: `ios-refactor/07-move-ads-remaining-label.md` → `Plans/ios-refactor/ssFinished/✅07-move-ads-remaining-label.md`.

---

## Build Commands

```bash
# Android debug build
./gradlew :app:assembleDebug

# Android release build
./gradlew :app:assembleRelease

# Compile Kotlin (check for errors without full build)
./gradlew :app:compileDebugKotlin

# iOS — generate the KMP framework Xcode needs
./gradlew :app:linkDebugFrameworkIosSimulatorArm64

# iOS — build via Gradle wrapper (calls xcodebuild internally)
./gradlew :iosApp:buildDebug

# iOS — build directly with xcodebuild (from iosApp/ dir)
xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination "platform=iOS Simulator,name=iPhone 15" build

# Clean
./gradlew clean
```

There are no unit tests beyond the boilerplate `ExampleUnitTest`. Run instrumented tests with `./gradlew :app:connectedAndroidTest`.

---

## Architecture

### KMP Source Set Layout

```
app/src/
  commonMain/kotlin/     ← shared game logic (runs on both platforms)
  androidMain/kotlin/    ← Android-specific expect/actual implementations
  iosMain/kotlin/        ← iOS-specific expect/actual implementations
  main/java/             ← legacy Android-only code (not yet migrated to androidMain)
  main/res/              ← Android resources (still in src/main/ during migration)
  androidMain/           ← AndroidManifest.xml lives here (KMP remapping)
iosApp/
  iosApp.xcodeproj/      ← Xcode project
  iosApp/                ← Swift entry point (ContentView.swift, iOSApp.swift)
```

The `afterEvaluate` block in `app/build.gradle` keeps `src/main/java`, `src/main/res`, and `src/main/assets` on the Android source path during migration. New platform-agnostic code goes in `commonMain`; new Android-specific code goes in `androidMain` (not `src/main/java`).

### expect/actual Bridges

Five `expect` declarations wire the KMP boundary. Every new platform capability needs this pattern.

| File (commonMain) | What it declares | Android actual | iOS actual |
|---|---|---|---|
| `utility/DrawingBridge.kt` | `DrawScope.drawGameFrame()` | delegates to `Drawing.drawFrame()` | delegates to `Drawing.drawFrame()` |
| `utility/TouchBridge.kt` | `onGamePointerDown/Move/Up()` | delegates to `Logic.onPointerDown/Move/Up()` | delegates to `Logic.onPointerDown/Move/Up()` |
| `utility/GameLoop.kt` | `class GameLoop(intervalMs, onTick)` | `Handler.postDelayed` loop | coroutine loop on `Dispatchers.Main` |
| `utility/PlatformStorage.kt` | `object PlatformStorage` | `SharedPreferences` (two stores: `adPreferences`, default) | `NSUserDefaults` (keys namespaced `store_key`) |
| `utility/Sounds.kt` | `object Sounds` (SFX + ambient music) | `SoundPool` + `MediaPlayer` | `AVAudioEngine` + 10-channel SFX pool with `AVAudioUnitTimePitch` |

All five bridges are fully implemented on both platforms. The iOS game renders, accepts input, and runs the game loop.

### Navigation (Compose Multiplatform)

`AppRoot.kt` (`commonMain`) owns navigation with a `NavHost`:
```
MainMenuScreen → GameScreen (via IosGameHost wrapper)
             → SettingsScreen
             → BallUnlockScreen
```

iOS entry: `MainViewController.kt` (`iosMain`) → `ComposeUIViewController { AppRoot() }` → Swift `ContentView` wraps it as `UIViewControllerRepresentable`.

Android still uses legacy `MainActivity` (XML layout) + `GameActivity` (Compose `setContent`) for the game. The `AppRoot` nav graph is shared but Android routes game launch through `GameActivity`, not through `AppRoot`.

### Package Structure

| Package | Purpose |
|---|---|
| `com.runoutzone.pockpock` (commonMain) | `AppRoot`, screen composables |
| `com.runoutzone.pockpock` (main/java) | Activities (`MainActivity`, `GameActivity`, `BallUnlockActivity`, etc.) |
| `enums` | All state enums (GameState, TouchState, MotionStates, Direction, MenuSelection, TutorialState, BallType) |
| `gameobjects` | `Player`, `Puck`, `Settings`, `PauseMenu`, `BotBrain`, `BotConfig` |
| `gameobjects/puckstyle` | `PuckRenderer`, `PuckSkin`, `TailRenderer`, `BallStyleFactory`, `ColorTheme`, `Palette`, `ChargePhase`, `RandomRoll` |
| `gameobjects/puckstyle/skins` | One file per ball type skin |
| `gameobjects/puckstyle/tails` | One file per ball type tail |
| `gameobjects/puckstyle/paddles` | One file per ball type paddle (named `*Launch.kt`) |
| `physics` | `Force`, `Point`, `Ticker`, `TutorialTicker` |
| `shapes` | `Circle`, `DrawablePoint`, `Explosion`, `ScoreExplosion`, `Collision`, `BallSelectionPopup`, `TutorialBox` |
| `utility` | `Logic`, `Drawing`, `Effects`, `PaintBucket`, `Sounds`, `SoundSpatializer`, `Storage`, `PlatformStorage`, `GameLoop`, `GameEvents`, `Signal`, `PurchaseManager`, `ShareHelper` |

### Key Singletons

- **`Settings`** (`commonMain/gameobjects`) — All runtime configuration: screen dimensions, ball size, speed, score state, game phase. Initialized once via `Settings.initializeForScreen(width, height)` which also reads `Storage`.
- **`Logic`** (`commonMain/utility`) — All game logic: player init, collision detection, touch routing, game state transitions, pause menu. Fully shared. `Logic.isInitialized` guards against calls before setup. `Logic.composeReinitCallback` allows `IosGameHost` to trigger a full re-init on reinstatement.
- **`Drawing`** (`commonMain/utility`) — All `DrawScope` canvas rendering. `Drawing.drawFrame()` is the entry point called from `DrawingBridge` on both platforms.
- **`PaintBucket`** (`commonMain/utility`) — All colors (Compose `Color`) and stroke descriptors. Android additionally exposes `android.graphics.Paint` objects via extension properties in `PaintBucketAndroid.kt` (`androidMain`). Call `PaintBucket.initialize(screenRatio)` after screen dims are known; Android also calls `initializeColors(resources)` then `buildPaints(resources)`.
- **`Sounds`** (`expect object`) — SFX + ambient music. Android: `SoundPool` + `MediaPlayer`. iOS: `AVAudioEngine` + `AVAudioPlayer` (10-channel SFX pool with pitch nodes).
- **`SoundSpatializer`** (`commonMain/utility`) — Shared pitch-rate grid for positional audio. Both platform `Sounds` implementations call `SoundSpatializer.getXRate()` / `getYRate()`.
- **`Storage`** (`commonMain/utility`) — Thin facade over `PlatformStorage`. All persistence goes through here.
- **`GameEvents`** (`commonMain/utility`) — Simple signal bus: `canScore`, `cantScore`, `gameOver`, `gameReset` signals. Use `Signal<T>.connect/disconnect/emit`.

### Game Loop

Android: `PlayView.startPlayers()` starts a `GameLoop` (Handler-based). Each tick runs the state machine:
```
BallSelection → Play → Scored → Play → ... → GameOver → BallSelection
```
(`CountDown` is a dead/unused state in the current code.)

iOS: `IosGameHost` in `AppRoot.kt` creates a coroutine-based `GameLoop` that increments a `mutableIntStateOf` tick counter, which forces `GameScreen`'s `Canvas` to redraw. `Settings.initializeForScreen`, `PaintBucket.initialize`, and `Sounds.initializeGame` are called once on the first `onSizeChanged`.

### Coordinate System / Screen Layout

```
┌────────────────────┐  y=0
│   HIGH score zone  │  (topGoalBottom = screenRatio * scoreZoneHeight)
├────────────────────┤  y=topGoalBottom
│                    │
│   Play area        │
│                    │
├────────────────────┤  y=bottomGoalTop
│   LOW score zone   │
└────────────────────┘  y=screenHeight
```

- `screenRatio = min(screenWidth / 20, 54f)` — the universal size unit.
- The high player's view is mirrored 180°. All drawing for the high player uses `canvas.scale(-1f, -1f, middleX, middleY)` (Android) or equivalent transform. See `Drawing.mirrorText()`.

### Physics

- `Force` = direction (`Point`, normalized) + `power` (scalar). Each puck has `movement` and `launch` forces combined per frame in `Puck.getNextDirection()`.
- Friction: `Force.applyFriction(Settings.friction)` every frame.
- Bounce: crossing a wall reflects the relevant direction component. Pucks can enter score zones mid-flight.

### Charge System

1. Hold → `charge` increases from `chargeStart` to `sweetSpotMax (50)`.
2. Sweet spot: `sweetSpotMin (40)` to `sweetSpotMax (50)` — release here sets `shielded = true`.
3. Overcharge: `charge >= sweetSpotMax` → resets to half, locks until release.
4. Shielded vs unshielded collision: shielded wins; both shielded: both shields persist.

### Ball Types System

`BallType` enum values: Classic, Neon, Ghost, Fire, Ice, Galaxy, Spinner, Metal, Pixel, Rainbow, Prism, Plasma, PokPok, Dragon, Axolotl, Cat, Random. (`Chicken` is a deprecated skin — `PokPok` replaces it, composed as `PokPokSkin` + `ChickenTail` + `ChickenLaunch`.)

- **Composition**: `PuckRenderer` owns a `PuckSkin` (fill/stroke), `TailRenderer` (trail), and `PaddleLaunchEffect` (paddle + strike animation). `BallStyleFactory.buildRenderer()` creates the full renderer for a given type.
- **Per-component unlock model** (Monetization Overhaul): every skin, tail, and paddle is individually unlockable by watching a rewarded ad. `BallStyleFactory.tierOf(type)` returns `Free` (Classic, PokPok) or `Ad` (every other skin/tail/paddle, incl. Prism/Plasma/Random/Dragon/Axolotl/Cat). No skin/tail/paddle is gated behind 100% — every non-free component is individually ad-unlockable. (`PREMIUM_TYPES` is intentionally empty.) Per-component state lives in `Storage` (`unlocked_skins`/`tails`/`paddles` CSV sets) with `Storage.isSkinUnlocked` / `isTailUnlocked` / `isPaddleUnlocked`. **Blanket rule:** all helpers return true when `unlockProgress >= 100`. `Storage.unlockProgress` reads the real persisted value (0–100); the meter economy is `recordAdWatched` +2, share +10, `unlock_all` IAP→100. Each individual ad unlock also calls `recordAdWatched()`, so it bumps the meter. **New styles ship as `Ad`-unlockable** (the only 100%-completion gate left is the custom "any color" in the CCP). Ads are limited to **3 per rolling hour** (`Storage.canWatchAdNow` / `minutesUntilNextAd`); there is no main-menu watch-ad button — players unlock by tapping a locked option in the CBC/CCP, and `components/AdLimitPopup` shows the wait time when over the limit.
- **Unlocking flow**: `AdUnlock.watchAdToUnlock(grant) { onResult }` (commonMain) shows a rewarded ad on both platforms, then runs the per-component/per-color `Storage.unlock*` grant. UI uses `components/StyleOptionButton` (square raised/pressed buttons with locked/ad/premium/selected states), `components/MeterLockedPopup` (shown only when the custom color is tapped below 100%), and `components/AdLimitPopup` (shown when over the hourly ad cap).
- **Persistence**: `Storage.saveHighBallType` / `saveLowBallType`. Loaded in `Settings.initializeForScreen`. Note: `Spiral` is migrated to `Spinner` on read.
- **Color themes**: `ColorTheme.getTheme(isHigh)` — warm theme for high player, cold for low.
- **In-game selection** (`BallSelection` state): `BallSelectionPopup` shows **only unlocked + configured custom ball slots** (no full type list, no locks, no ad access). Horizontal drag-scroll strip in each goal; touch routed via `Logic.interceptBallMenu`; high popup mirrors drag delta (`logicalX = 2*cx - screenX`).
- **Preview convention**: always call `previewPuck.setFill(theme.primary); previewPuck.setStroke(theme.secondary)` before `drawTo()`.

### Custom Balls & Colors (the ad hubs)

- **`CustomBallCreatorScreen`** (commonMain): the primary place players watch ads. A `UnlockProgressBar` thermometer sits at the top (also on the main menu). **10 custom slots** (`Storage.SLOT_COUNT`) shown 2×5. Slots 0–1 are free and prepopulated with composed Classic and PokPok balls (`Storage.ensureDefaultSlots()`, seeded once from `Storage.initialize`); slots 2–9 unlock at `unlockProgress >= 30 + (i-2)*10` (`Storage.isSlotUnlocked` / `slotRequiredPercent`), shown as "Reach X%". The skin/tail/paddle carousels are **composable** (`StyleOptionButton` rows drawing each part in a clipped `Canvas`); **tapping** a locked option runs the ad flow (or `AdLimitPopup` when over the hourly cap) — scrolling never triggers anything. A per-carousel "▲ front" control sets draw order (z-rank).
- **`CustomColorPickerScreen`** + `ColorCarousel`: preset palette (Red/Blue/Purple free; the other 6 individually ad-unlockable via `Storage.isColorUnlocked`/`unlockColor`); the custom "any color" slider (index 9) is gated behind 100%. A locked color is only acted on by an explicit **tap** (handled in the screen's gesture code) → ad flow / `AdLimitPopup`, or `MeterLockedPopup` for the custom slider; scrolling onto a locked color just browses it.

### Paddle System

Paddles live in `gameobjects/puckstyle/paddles/` (files named `*Launch.kt`). The directory `launcheffects/` referenced in old docs was renamed to `paddles/`.

- **`LaunchEffect` interface** + **`PaddleLaunchEffect` base class** define the paddle kinematics and strike animation. Subclasses override only visual drawing.
- **`ChargePhase`** (`Idle`, `Building`, `SweetSpot`, `Overcharged`) computed from `Settings` constants in `PaddleLaunchEffect.updateState()`. Never hardcode charge thresholds in subclasses.
- **`chargePaint` in `PuckRenderer`** is deprecated (replaced by paddle). Do not add logic to it.

### Single-Player Bot

`BotBrain` + `BotConfig` (`gameobjects`). Created in `Logic.initialize` when `Settings.isSinglePlayer` is true. Three presets: `BotConfig.Easy`, `Medium`, `Hard`. `BotBrain.tick()` is called each game loop tick before logic updates.

### Unlock / Monetization

**Model**: Per-component ad unlocking (see Ball Types System). Reaching 100% `unlockProgress` unlocks everything plus the only 100%-completion bonus (the custom "any color" in the CCP). The meter is filled by individual unlock ads (`recordAdWatched` +2 each, capped at 3 ads/rolling hour), the share bonus (+10), or the `unlock_all` IAP (→100). Ad-watching lives in the CBC/CCP (tap a locked option), not on the main menu.

**Rewarded-ad bridge**: `PlatformAd` (`commonMain` expect) is the cross-platform rewarded-ad API. Android's actual (`androidMain/utility/PlatformAd.kt`) loads/shows via the Google Mobile Ads SDK, presenting through `AdActivityProvider.activity` (set in `MainActivity.onResume`/cleared `onPause`). iOS's actual delegates to `IosAdProvider` → `iosApp/iosApp/AdMobBridge.swift`. commonMain screens call ads through `utility/AdUnlock`. (The legacy inline ad buttons in `PlatformExtras.android.kt` still exist for the deprecated screens.)

**Android billing**: `PurchaseManager` (`utility`) wraps Google Play Billing (`billing-ktx`). One in-app product (`PRODUCT_ID = "unlock_all"`) sets `Storage.unlockProgress = 100` on purchase.

**iOS**: ads work via `AdMobBridge.swift`; no billing yet.

### Sound Design

Sounds are spatialized via `SoundSpatializer` (shared): a 6-zone pitch grid (`rates` array). `getXRate(x)` / `getYRate(y)` map screen position to a pitch rate. Both Android and iOS `Sounds` implementations call these. The iOS implementation uses a 10-node `AVAudioEngine` pool with `AVAudioUnitTimePitch` for pitch shifting.

---

## Conventions You Use

- **Global objects over passing context**: `Settings`, `Logic`, `Drawing`, `PaintBucket`, `Sounds`, `Storage`, `Effects` are all object singletons. Don't refactor these into DI or constructor-injected classes — the design is intentional.
- **Mirror everything**: Content for the top player is drawn upside-down using `canvas.scale(-1f, -1f, middleX, middleY)`. All text and asymmetric shapes that must be readable from both ends are drawn twice. See `Drawing.mirrorText()`.
- **`screenRatio` is the unit**: All sizes (ball radius, wall thickness, text, etc.) are multiples of `Settings.screenRatio`. Never hardcode pixel sizes.
- **`Ticker`**: The game's timing primitive. Ascending tickers count up and expose `ratio` (0–1) and `finished`. Use ascending for progress animations, descending for countdowns.
- **Touch routing**: Single-touch goes to whichever player's half the touch is in. Multi-touch: pointer 0 and pointer 1 are mapped to high/low based on `highTouchedFirst`.
- **Player symmetry**: `highPlayer` and `lowPlayer` are always both initialized. Actions on one almost always have a parallel action on the other.
- **User-facing text says "Top"/"Bottom", code says high/low**: Internally everything keeps the `high`/`low` naming (the high player is mirrored at the top of the screen, the low player is at the bottom). But any **user-facing** string must refer to them as "Top player" / "Bottom player" (or just "Top" / "Bottom" for short) — **high = Top, low = Bottom**. Never surface "P1"/"P2" or "High"/"Low" in UI text or string resources.
- **Tail length is fixed**: Never vary tail length based on charge or shield state. Always use `baseCount * Settings.tailLengthMultiplier`.
- **Compose DrawScope transforms — use direct coordinates for rotation**: `withTransform { rotate(...) }` does not reliably rotate paths around the translated origin. For any `Path` that needs rotation, compute final screen coordinates using trig and draw in absolute screen space. Pattern:
  ```kotlin
  val rad = angleDegrees * (Math.PI.toFloat() / 180f)
  val cosA = cos(rad); val sinA = sin(rad)
  fun sx(lx: Float, ly: Float) = renderer.x + lx * cosA - ly * sinA
  fun sy(lx: Float, ly: Float) = renderer.y + lx * sinA + ly * cosA
  ```
- **KMP expect/actual**: When adding a new platform capability, declare `expect` in `commonMain`, `actual` in both `androidMain` and `iosMain`. iOS actuals may be stubs initially — mark them clearly.
- **`PaintBucket` Android extensions**: Android-specific `Paint` objects are extension properties defined in `PaintBucketAndroid.kt` (`androidMain`), not in the `PaintBucket` object itself. New Android paints go there; new cross-platform colors go in the `PaintBucket` object.
- **Popups/dialogs must theme for dark & light mode**: any `AlertDialog`/popup (e.g. `components/AdLimitPopup`, `components/MeterLockedPopup`) must read `LocalDarkMode.current` and set `containerColor` + `titleContentColor`/`textContentColor` (and explicit `color` on `Text`/button labels) so text stays readable in both modes. Pattern: `bg = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight`, `fg = if (isDark) PaintBucket.white else Color(0xFF222222)`. Never hardcode a single `containerColor` (e.g. `menuButtonDark`) — that's the bug that made popups dark-on-dark in light mode. New popups should follow this from the start.

---

## Outstanding Issues / Needs Before Ship

### iOS — Parity Gaps (Android is the source of truth)

The iOS game loop, rendering, and touch input are fully wired. The remaining gaps are UI parity issues compared to the Android screens:

1. **`MainMenuScreen` missing single-player mode** — Android's `MainActivity` has a "Play Solo" button that shows a difficulty picker dialog (`Easy`/`Medium`/`Hard`) and sets `Settings.botConfig` + `Settings.isSinglePlayer = true`. `AppRoot.kt` only has a two-player `onPlayTapped`. Add a second button and dialog.
2. **`MainMenuScreen` missing ambient sound** — `MainActivity` calls `Sounds.playMenuAmbiance()` on `onResume` and `Sounds.playGameAmbiance()` before launching the game. `AppRoot.kt` / `IosGameHost` makes no sound calls during menu navigation.
3. **`BallUnlockScreen` is DEPRECATED** — replaced by the per-component unlock model in the Custom Ball Creator / Color Picker. It is no longer reachable from the main menu (the `ball_types` button and `Screen.BallUnlock` route were removed) and is unmaintained. `BallUnlockScreen`/`BallUnlockActivity`/`BallUnlockView` are kept compiling but not deleted.
4. **`SettingsScreen` missing dark-mode toggle** — Android has a "dark mode" preference that triggers a full theme recreate. The `SettingsScreen` in commonMain has no dark mode toggle (though `Storage.darkMode` exists).
5. **`PauseMenu` icon positions unverified** — Settings, Reset, and Back icons may be drawn in positions that don't match `Logic.menuCallback()`'s left/right/middle touch zone mapping. Needs a manual test.

### Critical (Store blockers)

6. **`PurchaseManager.PRODUCT_ID`** — `"unlock_all"` is a placeholder. Replace with the real Google Play product ID before Android launch.
7. **AdMob IDs still test IDs** — the rewarded-ad unit IDs (`build.gradle` `ADMOB_REWARDED_UNIT_ID`, iOS `Info.plist`) are Google's test IDs. The per-component unlock flow (`AdUnlock` → `PlatformAd`) uses them; replace with live IDs before launch.
8. **AdMob Application ID** — `AndroidManifest.xml` has `ca-app-pub-1111532606958888~7923000787`; verify this is the real production app ID.

### Bugs

9. **`Player.puckFillColor` is stale after `setPuckColor()` is called** — `puckFillColor` is set at construction and used in tail drawing. `Logic.setPuckColor()` calls `puck.setFill()` but not `Player.puckFillColor`. Brief wrong-color tail during Scored state.
10. **`checkScored()` assigns wrong colors on score** — swaps puck colors during Scored regardless of which player scored. `resetPlayerStates()` restores both. Appears intentional (brief flash) but verify no edge case leaves colors permanently swapped on GameOver.

### Unfinished Features

11. **Teleport mechanic is disabled** — fully implemented in `Player.kt` but trigger lines in `Player.shouldBounce()` are commented out.
12. **Standing-still charge bonus is disabled** — implemented but commented out in `Player.drawTo()`. `bonusMovement` field in `Puck.kt` exists for this.
13. **`Test.kt`** — scratch code, remove before ship.
14. **`StingerTransition.kt`** — unused screen transition; `transitionTo()` is empty.
15. **`Ads.kt`** — AdMob scaffolding; `MainActivity.goToAds()` button is disconnected. Wire up or remove.
16. **Tutorial `ChargeBonusCanceledExplain`** — never triggered anywhere.

### Polish / Pre-launch

17. **Custom app launcher icon** — currently the default Android Studio adaptive icon.
18. **Play Store / App Store listing assets** — screenshots, feature graphic, description.
19. **Privacy policy** — required if collecting data (AdMob/billing require this).

---

## Build Stack

| Tool | Version |
|---|---|
| Gradle Wrapper | 8.13 |
| Android Gradle Plugin | 8.9.1 |
| Kotlin | 2.1.21 |
| Compose Multiplatform | 1.7.3 |
| compileSdk | 35 |
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 |
| Java | 17 |
| play-services-ads | 23.6.0 |
| billing-ktx | 7.1.1 |
