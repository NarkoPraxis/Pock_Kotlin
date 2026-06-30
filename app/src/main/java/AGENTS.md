# AGENTS.md — main/java (legacy Android-only code)

Scope: Android-only code that has **not yet been migrated** to `androidMain`.
**Do not add new code here** — new Android-specific code goes in `androidMain`,
new shared code in `commonMain`. The `afterEvaluate` block in `app/build.gradle`
keeps `src/main/java`, `src/main/res`, and `src/main/assets` on the Android
source path during migration.

## Files

| File | Role |
|---|---|
| `com/runoutzone/pockpock/MainActivity.kt` | Thin `AppCompatActivity`. `onCreate` does Storage/Sounds/ads/billing init, applies the dark-mode theme, then `setContent { AppRoot() }`. **Not** an XML layout; there is **no** `GameActivity`/`PlayView`. Sets/clears `AdActivityProvider.activity` in `onResume`/`onPause`; calls `Sounds.playMenuAmbiance()` / `playGameAmbiance()`. |
| `utility/PurchaseManager.kt` | Google Play Billing (`billing-ktx`). `PRODUCT_ID = "unlock_all"` → sets `Storage.unlockProgress = 100`. **Placeholder ID — replace before launch.** |
| `utility/PaintBucket.kt` | Legacy PaintBucket (the live cross-platform one is `commonMain/utility/PaintBucket.kt`). |
| `shapes/TutorialBox.kt`, `com/runoutzone/pockpock/UnlockProgressBar.kt` | Legacy Android-only support classes. |

## Notes

- `MainActivity` is the Android entry point; it shares the exact `AppRoot → IosGameHost → GameScreen` path with iOS — no Android-only game screen.
- `Ads.kt` scaffolding / a disconnected `goToAds()` button is dead code to wire up or remove before ship.
- When you migrate a file out of here, move it to `androidMain` (or `commonMain` if it's platform-agnostic) and update the package/source-set accordingly.
