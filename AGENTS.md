# Repository Guidelines

## Project Structure & Module Organization
This repository is a single-module Android app.

- App module: `app/`
- Kotlin source: `app/src/main/java/com/example/basicgallery/`
- UI theme files: `app/src/main/java/com/example/basicgallery/ui/theme/`
- Android resources: `app/src/main/res/`
- Unit tests (JVM): `app/src/test/`
- Instrumented tests (device/emulator): `app/src/androidTest/`
- Gradle version catalog: `gradle/libs.versions.toml`

Keep feature code in `app/src/main/java/...` and mirror package structure in test directories.

## Product Behavior Snapshot
Current main-screen behavior (keep docs and code aligned when changing it):

- Section switcher is in the top app bar title area as tabs.
- Available sections: `Фото/Photos` and `Корзина/Trash`.
- Photos are grouped by date with localized labels (Today/Yesterday and local date format).
- Pull-down reveal shows section media counters (`Фото: X • Видео: Y` / `Photos: X • Videos: Y`).
- Media is shown in a 3-column grid (photos and videos).
- Grid has an interactive timeline scrollbar on the right:
  - User can drag/tap the scrollbar thumb to scrub through the timeline quickly.
  - While dragging, year markers (dot + year) are shown.
  - While dragging, a floating month/year label is shown near the thumb.
- Video cells use the first frame as preview and show duration badge at bottom-right:
  - `MM:SS` for videos shorter than 1 hour.
  - `HH:MM:SS` for videos 1 hour or longer.
- Full-screen photo viewer:
  - Top bar shows photo creation date/time.
  - In `Фото/Photos`, bottom actions: `Edit` and `Delete`.
  - In `Корзина/Trash`, only bottom action `Delete` is shown.
  - `Edit` opens an in-app photo editor with controls for `Exposure/Выдержка`, `Brightness/Яркость`, `Contrast/Контраст`, and `Sharpness/Резкость`.
  - Editor supports an interactive crop frame with draggable corner handles.
  - Saving creates a new photo without deleting the original.
  - The saved edited photo keeps the original photo capture date/time.
- Full-screen video viewer:
  - Top bar shows video creation date/time.
  - Uses a simple inline player.
  - Bottom action: `Delete`.
- Full-screen media viewer supports left/right swiping across all items in the current section (photos + videos).
- Full-screen `Delete` behavior:
  - In `Фото/Photos`, `Delete` moves media to trash.
  - In `Корзина/Trash`, `Delete` permanently deletes media.
- In `Фото/Photos`, multi-select moves chosen photos to trash.
- In `Фото/Photos`, each day header includes a right-aligned `Выбрать всё/Select all` action that selects all photos for that date.
- Trash supports:
  - `Delete all` action in top-right corner for all items in trash.
  - In selection mode, top app bar `Actions` menu with `Restore` and `Delete`.
  - Restore via MediaStore untrash API and permanent delete via MediaStore delete API (Android 11+).

## Build, Test, and Development Commands
Run commands from the repository root:

- `./gradlew assembleDebug` - builds a debug APK.
- `./gradlew :app:installDebug` - installs debug build on a connected device/emulator.
- `./gradlew testDebugUnitTest` - runs local unit tests in `app/src/test`.
- `./gradlew connectedDebugAndroidTest` - runs instrumented tests in `app/src/androidTest` (requires emulator/device).
- `./gradlew lintDebug` - runs Android lint checks for the debug variant.

## Coding Style & Naming Conventions
Use Kotlin conventions with 4-space indentation and no tabs.

- Classes/objects/composables: `PascalCase` (`MainActivity`, `GreetingPreview`).
- Functions/variables: `camelCase` (`addition_isCorrect` in tests can use underscores for readability).
- Packages: lowercase (`com.example.basicgallery`).
- Resource names: lowercase snake_case (`ic_launcher_foreground`, `backup_rules`).

Use Android Studio’s Kotlin formatter before committing (`Code > Reformat Code`).

## Testing Guidelines
Use JUnit4 for unit tests and AndroidX test runner for instrumented tests.

- Place pure logic tests under `app/src/test/...`.
- Place Android framework/UI behavior tests under `app/src/androidTest/...`.
- Name tests as `action_expectedResult` (example: `loadGallery_showsItems`).
- For bug fixes, add at least one regression test when feasible.

## Commit & Pull Request Guidelines
Current history is minimal (`Initial commit`), so no strict convention is established yet. Use short, imperative commit subjects, e.g., `Add gallery item model`.

For pull requests:

- Describe what changed and why.
- Link related issue(s).
- List verification steps and executed commands.
- Include screenshots for UI/Compose changes.
- Keep PRs focused; avoid mixing refactors with feature work.

## Configuration Tips
`local.properties` is machine-specific (SDK path) and should not contain secrets. Keep API keys or sensitive values out of the repository.
