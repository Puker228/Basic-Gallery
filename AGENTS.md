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
