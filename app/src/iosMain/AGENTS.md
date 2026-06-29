# AGENTS.md — iosMain (iOS `actual` implementations)

Scope: iOS-specific `actual` impls of the commonMain `expect` declarations, the
iOS entry point, audio engine, and ad bridge. **Android is the source of truth**
for behavior; iOS parity gaps are tracked at the bottom.

## Files & what they implement

| File | Role |
|---|---|
| `com/runoutzone/pockpock/MainViewController.kt` | iOS entry → `ComposeUIViewController { AppRoot() }`; Swift `ContentView` wraps it as `UIViewControllerRepresentable` |
| `com/runoutzone/pockpock/PlatformExtras.ios.kt` | iOS platform extras |
| `utility/DrawingBridge.kt` | `actual fun DrawScope.drawGameFrame()` → `Drawing.drawFrame()` |
| `utility/TouchBridge.kt` | `actual` pointer handlers → `Logic.onPointer*()` |
| `utility/GameLoop.kt` | `actual class GameLoop` — coroutine loop on `Dispatchers.Main` |
| `utility/PlatformStorage.kt` | `actual object PlatformStorage` — `NSUserDefaults` (keys namespaced `store_key`) |
| `utility/Sounds.kt` | `actual object Sounds` — `AVAudioEngine` + 10-channel SFX pool with `AVAudioUnitTimePitch` |
| `utility/PlatformAd.kt` | Delegates to `IosAdProvider` → `iosApp/iosApp/AdMobBridge.swift` |
| `utility/ShareHelper.kt`, `Clock.kt`, `ProfilePlatform.kt`, `EdgeBackGesture.kt` | misc `actual`s |

The Swift side lives in `iosApp/iosApp/` (`ContentView.swift`, `iOSApp.swift`, `AdMobBridge.swift`, `Info.plist`).

## Audio

iOS `Sounds` uses a 10-node `AVAudioEngine` pool with `AVAudioUnitTimePitch` for pitch shifting, calling the shared `SoundSpatializer.getXRate()`/`getYRate()` grid (same as Android).

## Ads & Billing

- Rewarded ads work via `IosAdProvider` → `AdMobBridge.swift`. The rewarded unit IDs in `Info.plist` are still Google **test** IDs — replace before launch.
- **No billing yet** on iOS.

## iOS parity gaps (Android is source of truth)

The iOS game loop, rendering, and touch input are fully wired. Remaining gaps are
UI parity issues vs the Android screens:

1. **`MainMenuScreen` missing single-player mode** — Android has a "Play Solo" button + difficulty dialog (`Easy`/`Medium`/`Hard`) setting `Settings.botConfig` + `Settings.isSinglePlayer`. `AppRoot.kt` only has two-player `onPlayTapped`. Add a second button + dialog.
2. **`MainMenuScreen` missing ambient sound** — Android `MainActivity` calls `Sounds.playMenuAmbiance()` on resume and `Sounds.playGameAmbiance()` before launching. `AppRoot`/`IosGameHost` make no sound calls during menu navigation.
3. **`SettingsScreen` missing dark-mode toggle** — Android has a dark-mode preference that recreates the theme. commonMain `SettingsScreen` has no toggle (though `Storage.darkMode` exists).
4. **`PauseMenu` icon positions unverified** — Settings/Reset/Back icon positions may not match `Logic.menuCallback()`'s touch-zone mapping. Needs a manual test.

(`BallUnlockScreen` is deprecated and unreachable — see root `AGENTS.md`.)

When adding a new `expect`, the iOS `actual` may start as a clearly-marked stub.
