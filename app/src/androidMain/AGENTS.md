# AGENTS.md — androidMain (Android `actual` implementations)

Scope: Android-specific `actual` impls of the commonMain `expect` declarations,
plus Android-only drawing/ad/billing glue. New Android-specific code goes here
(not in `src/main/java`, which is legacy — see that dir's AGENTS.md). The
`AndroidManifest.xml` lives here (KMP remapping).

## Files & what they implement

| File | Role |
|---|---|
| `utility/DrawingBridge.kt` | `actual fun DrawScope.drawGameFrame()` → `Drawing.drawFrame()` |
| `utility/TouchBridge.kt` | `actual` pointer handlers → `Logic.onPointer*()` |
| `utility/GameLoop.kt` | `actual class GameLoop` — `Handler.postDelayed` loop |
| `utility/PlatformStorage.kt` | `actual object PlatformStorage` — `SharedPreferences` (two stores: `adPreferences`, default) |
| `utility/Sounds.kt` | `actual object Sounds` — `SoundPool` (SFX) + `MediaPlayer` (ambient music) |
| `utility/PaintBucketAndroid.kt` | Android `android.graphics.Paint` objects as extension properties on `PaintBucket` |
| `utility/PlatformAd.kt` | Rewarded ads via Google Mobile Ads SDK, shown through `AdActivityProvider.activity` |
| `utility/ShareHelper.kt`, `Clock.kt`, `ProfilePlatform.kt`, `EdgeBackGesture.kt` | misc `actual`s |
| `com/runoutzone/pockpock/PlatformExtras.android.kt` | Android platform extras (legacy inline ad buttons for deprecated screens still here) |

## Conventions

- **`PaintBucket` Android extensions**: Android-specific `Paint` objects are extension properties in `PaintBucketAndroid.kt`, **not** in the `PaintBucket` object. New Android paints go here; new cross-platform colors go in the `PaintBucket` object (commonMain). After screen dims are known, Android calls `PaintBucket.initializeColors(resources)` then `buildPaints(resources)` (in addition to the shared `initialize(screenRatio)`).
- **Sound spatialization**: `Sounds` calls `SoundSpatializer.getXRate()`/`getYRate()` (shared) for positional pitch.

## Ads (rewarded) & Billing

- **`PlatformAd` (Android actual)** loads/shows rewarded ads via the Google Mobile Ads SDK, presenting through `AdActivityProvider.activity` (set in `MainActivity.onResume`, cleared `onPause`). commonMain calls ads through `utility/AdUnlock`.
- **Billing** is `PurchaseManager` (currently in `src/main/java/utility/` — legacy) wrapping Google Play Billing (`billing-ktx`). One product `PRODUCT_ID = "unlock_all"` → `Storage.unlockProgress = 100`.

### Launch blockers (Android)
- `PurchaseManager.PRODUCT_ID = "unlock_all"` is a placeholder — replace with the real Play product ID.
- AdMob rewarded unit IDs (`build.gradle` `ADMOB_REWARDED_UNIT_ID`) are still Google test IDs.
- AdMob Application ID in `AndroidManifest.xml` (`ca-app-pub-1111532606958888~7923000787`) — verify it's the real production ID.
