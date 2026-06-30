# AGENTS.md — Screens, Navigation & UI (com.runoutzone.pockpock, commonMain)

Scope: the Compose UI layer — `AppRoot`, every screen composable, the in-game
host, and the ad-hub creator screens. Game-logic/rendering internals are in
`utility` (`Logic`/`Drawing`); see commonMain & root `AGENTS.md`.

## Navigation

`AppRoot.kt` owns a `NavHost` and **both platforms run the whole app — including
the game — through it**:
```
MainMenuScreen → GameScreen (wrapped by IosGameHost)
             → SettingsScreen
             → BallDesignerScreen → BallDesignerColorScreen
```
`IosGameHost` (in `AppRoot.kt`) creates the single shared `GameLoop` and holds
the per-tick `onTick` body that drives the state machine (see root `AGENTS.md`
→ Game Loop). A game-loop/touch-routing change is wired here, once, for both
platforms.

## Screen inventory

| File | Status |
|---|---|
| `AppRoot.kt`, `MainMenuScreen.kt`, `GameScreen.kt`, `SettingsScreen.kt`, `SplashScreen.kt` | Current |
| `BallDesignerScreen.kt` (**CBC** = "Custom Ball Creator" / "Ball Designer") | Current — primary ad hub |
| `BallDesignerColorScreen.kt` (**CCP** = "Custom Color Picker") | Current — color ad hub |
| `MenuDemoCanvas.kt`, `TipOverlay.kt`, `WrongSideWarning.kt`, `ProfilerHud.kt`, `LocaleController.kt`, `UnlockProgressBar.kt` | Current support UI |
| `BallUnlockScreen.kt`, `CustomBallCreatorScreen.kt`, `CustomColorPickerScreen.kt` | **DEPRECATED** — kept compiling, not reachable from the menu. "CBC"/"CCP"/"Ball Designer" refer to the `BallDesigner*` files, NOT these. |
| `components/` | `StyleOptionButton`, `AdLimitPopup`, `MeterLockedPopup`, `HorizontalOptionCarousel`, `VerticalOptionCarousel` |
| `menu/` | `MenuComponents`, `PoppinsFont` |

## Custom Balls & Colors — the ad hubs

These two screens are the primary place players watch rewarded ads to unlock
components. (Unlock model + `Storage` keys: see `gameobjects/puckstyle/AGENTS.md`.)

- **`BallDesignerScreen` (CBC)**: a `UnlockProgressBar` thermometer sits at the top (also on the main menu). **10 custom slots** (`Storage.SLOT_COUNT`) shown 2×5. Slots 0–1 are free and prepopulated with composed Classic and PokPok balls (`Storage.ensureDefaultSlots()`, seeded once from `Storage.initialize`); slots 2–9 unlock at `unlockProgress >= 30 + (i-2)*10` (`Storage.isSlotUnlocked` / `slotRequiredPercent`), shown as "Reach X%". The skin/tail/paddle carousels are **composable** (`StyleOptionButton` rows drawing each part in a clipped `Canvas`); **tapping** a locked option runs the ad flow (or `AdLimitPopup` when over the hourly cap) — scrolling never triggers anything. A per-carousel "▲ front" control sets draw order (z-rank).
- **`BallDesignerColorScreen` (CCP)** + `ColorCarousel`: preset palette (Red/Blue/Purple free; the other 6 individually ad-unlockable via `Storage.isColorUnlocked`/`unlockColor`); the custom "any color" slider (index 9) is gated behind 100%. A locked color is only acted on by an explicit **tap** (handled in the screen's gesture code) → ad flow / `AdLimitPopup`, or `MeterLockedPopup` for the custom slider; scrolling onto a locked color just browses it.

**Unlocking flow**: `AdUnlock.watchAdToUnlock(grant) { onResult }` (commonMain) shows a rewarded ad on both platforms, then runs the per-component/per-color `Storage.unlock*` grant. UI uses `components/StyleOptionButton` (square raised/pressed buttons with locked/ad/premium/selected states), `components/MeterLockedPopup` (shown only when the custom color is tapped below 100%), and `components/AdLimitPopup` (shown when over the hourly ad cap). Ads are limited to **3 per rolling hour** (`Storage.canWatchAdNow` / `minutesUntilNextAd`); there is **no** main-menu watch-ad button.

## In-game ball selection (`BallSelection` state)

`BallSelectionPopup` (in `shapes`) shows **only unlocked + configured custom ball slots** (no full type list, no locks, no ad access). Horizontal drag-scroll strip in each goal; touch routed via `Logic.interceptBallMenu`; high popup mirrors drag delta (`logicalX = 2*cx - screenX`).

## UI conventions (enforced here)

- **Popups/dialogs must theme for dark & light mode** — read `LocalDarkMode.current`, set `containerColor` + content colors, and explicit `color` on `Text`/labels. `bg = if (isDark) PaintBucket.menuBackgroundDark else PaintBucket.menuBackgroundLight`, `fg = if (isDark) PaintBucket.white else Color(0xFF222222)`. Never hardcode a single `containerColor`. (Full rule in root `AGENTS.md` → Conventions.)
- **User-facing text says "Top"/"Bottom"**, never "High"/"Low"/"P1"/"P2". (high = Top, low = Bottom.)
- **Preview convention**: before drawing a preview puck, call `previewPuck.setFill(theme.primary); previewPuck.setStroke(theme.secondary)` then `drawTo()`.
