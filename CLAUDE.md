# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

Run from the repository root:

```bash
./gradlew assembleDebug                    # Build debug APK
./gradlew :app:installDebug               # Install on connected device/emulator
./gradlew testDebugUnitTest               # Run JVM unit tests (app/src/test/)
./gradlew connectedDebugAndroidTest       # Run instrumented tests (requires device/emulator)
./gradlew lintDebug                       # Run Android lint
```

To run a single unit test class:
```bash
./gradlew testDebugUnitTest --tests "com.example.basicgallery.GalleryViewModelTest"
```

## Architecture

Single-module app (`app/`). Key layers:

**Data layer** (`data/`):
- `GalleryRepository` interface — abstraction for media operations
- `MediaStoreGalleryRepository` — queries Android MediaStore for photos/videos/trash
- `PhotoEditingProcessor` — applies adjustments (exposure, brightness, contrast, sharpness) and crop, saves edited photo to MediaStore preserving original capture date
- `EditedPhotoNameCodec` — encodes/decodes edited photo metadata in filenames

**UI layer** (`ui/`):
- Single activity (`MainActivity`) with Compose, edge-to-edge enabled
- `GalleryViewModel` manages all state via `StateFlow`; uses factory pattern for instantiation
- `GalleryScreen` — 3-column grid, section tabs (Photos/Trash), interactive timeline scrollbar, day-grouped items, multi-select
- `FullscreenMediaScreen` — left/right swipe between items, tap to toggle bars, vertical swipe-to-dismiss, zoom
- `PhotoEditorScreen` — sliders for exposure/brightness/contrast/sharpness, draggable crop handles

**State**: `GalleryUiState` holds photo list, trash list, selection state, section, and loading/error states.

## Product Behavior

- Sections: `Фото/Photos` and `Корзина/Trash` (bilingual UI — English and Russian)
- Photos section: multi-select → move to trash; per-day `Выбрать всё/Select all`
- Trash section: fixed bottom `Delete` bar; selection mode adds `Restore` and `Delete` in top bar Actions menu
- Full-screen delete in Photos → moves to trash; in Trash → permanent delete
- Editing saves a new photo (original untouched), preserving capture date
- Trash/restore/permanent-delete use MediaStore APIs requiring Android 11+ (minSdk is 29)
- Video cells show first-frame preview via Coil and duration badge (`MM:SS` / `HH:MM:SS`)

## Coding Style

- Kotlin, 4-space indentation, no tabs
- Classes/composables: `PascalCase`; functions/variables: `camelCase`; resources: `lowercase_snake_case`
- Test naming: `action_expectedResult` (e.g., `loadGallery_showsItems`)
- JUnit4 + `createAndroidComposeRule` for Compose UI tests; Mockito for mocking; `MainDispatcherRule` for coroutine test dispatchers

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- AndroidX ViewModel + StateFlow
- Coil 2.7.0 (image/video loading)
- compileSdk 36, minSdk 29, JDK 11
- Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`)

## Permissions

Declared in manifest and requested at runtime based on Android version:
- API 34+: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`
- API 33: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`
- API ≤ 32: `READ_EXTERNAL_STORAGE`
