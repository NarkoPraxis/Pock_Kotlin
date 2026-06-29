# AGENTS.md — main/res (Android resources during migration)

Scope: Android resources still living under `src/main/res` during the KMP
migration. Kept on the Android source path by the `afterEvaluate` block in
`app/build.gradle` (alongside `src/main/java` and `src/main/assets`).

## Layout

```
res/
  drawable/, drawable-v24/   ← icons & vector assets
  layout/                    ← legacy XML (the game uses Compose Canvas, not XML — minimal use)
  menu/, xml/                ← Android menu/config XML
  mipmap-*/                  ← launcher icons (still the default adaptive icon — needs a custom one before ship)
  raw/                       ← raw audio/assets
  values/, values-night/     ← colors/themes/styles; values-night = dark mode
  values-de/-es/-fr/-ja/-zh/ ← localized strings
  values-w820dp/             ← tablet/large-width overrides
```

## Notes

- **Strings have largely moved** to `commonMain/composeResources/values/strings.xml` (+ locale variants `values-de/-es/-fr/-ja/-zh`). Prefer composeResources for any string used by shared UI; `main/res` strings are for legacy Android-only paths. Keep both in sync where they overlap.
- `values-night/` backs Android's dark-mode theme recreate (driven by `Storage.darkMode`).
- **Launcher icon** is still the default Android Studio adaptive icon (`mipmap-*`) — replace before launch.
- User-facing strings refer to players as "Top"/"Bottom", never "High"/"Low"/"P1"/"P2".
- New Android resources can stay here during migration, but prefer `commonMain/composeResources` for anything shared UI consumes.
