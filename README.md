# Basic Gallery

Basic Gallery is a single-module Android app that shows device photos and supports a full trash flow using Jetpack Compose.

## Features

- Requests runtime permissions for local media access.
- Loads photos, videos, and trashed media from `MediaStore`, sorted by capture date.
- Top app bar section switcher with dropdown (`Photos` / `Trash`) and arrow indicator.
- Groups photos by day with localized section labels (for example, Today/Yesterday).
- Pull-down reveal shows section media counters (`Photos: X • Videos: Y`).
- Day headers in Photos include a right-aligned `Select all` action for that date.
- Displays media in a 3-column grid.
- Video thumbnails use the first video frame and show duration at bottom-right (`MM:SS`, or `HH:MM:SS` for videos 1 hour+).
- Full-screen media viewer supports left/right swiping across all items in the current section (photos + videos).
- Full-screen photo view shows capture date/time and bottom actions (`Edit`, `Delete`).
- Built-in photo editor supports `Exposure`, `Brightness`, `Contrast`, and `Sharpness`.
- Built-in photo editor supports an interactive crop frame with draggable corner handles.
- Saving an edited photo creates a new file and keeps the original photo unchanged.
- Edited copy keeps the original photo capture date.
- Full-screen video view shows capture date/time, has a simple player, and bottom `Delete` action.
- Photos section: multi-select and move to trash.
- Trash section:
  - Title shows total size of photos in trash (`Trash (228 MB)` style).
  - Top-right `Delete all` action for all trash photos.
  - In selection mode, top app bar `Actions` menu provides `Restore` and `Delete`.
- Uses `MediaStore.createTrashRequest(..., false)` for restore and `MediaStore.createDeleteRequest(...)` for permanent delete on Android 11+.

## Tech Stack

- Kotlin
- Jetpack Compose + Material 3
- AndroidX ViewModel + StateFlow
- Coil (image loading)
- Gradle Kotlin DSL

## Requirements

- Android Studio (recent stable version)
- JDK 11
- Android SDK with `compileSdk 36` (minor API level 1)
- Minimum device API: 29

## Project Structure

- App module: `app/`
- Kotlin source: `app/src/main/java/com/example/basicgallery/`
- UI theme files: `app/src/main/java/com/example/basicgallery/ui/theme/`
- Resources: `app/src/main/res/`
- Unit tests: `app/src/test/`
- Instrumented tests: `app/src/androidTest/`

## Build and Run

Run from the repository root:

```bash
./gradlew assembleDebug
./gradlew :app:installDebug
```

## Test and Lint

```bash
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
./gradlew lintDebug
```

## Permissions

The app declares and requests media-read permissions based on Android version:

- Android 14+ (`API 34+`): `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`
- Android 13 (`API 33`): `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`
- Android 12 and lower: `READ_EXTERNAL_STORAGE`

Trash, restore, and permanent-delete request APIs require Android 11+.
