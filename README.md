# Basic Gallery

Basic Gallery is a single-module Android app that shows photos from device storage using Jetpack Compose.

## Features

- Requests runtime permission to access local images.
- Loads images from `MediaStore` and sorts them by capture date.
- Groups photos by day with localized section labels (for example, Today/Yesterday).
- Displays photos in a 3-column grid.
- Supports full-screen photo viewing.
- Supports multi-select and delete via `MediaStore.createTrashRequest` on Android 11+.

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

The app declares and requests image-read permissions based on Android version:

- Android 14+ (`API 34+`): `READ_MEDIA_IMAGES`, `READ_MEDIA_VISUAL_USER_SELECTED`
- Android 13 (`API 33`): `READ_MEDIA_IMAGES`
- Android 12 and lower: `READ_EXTERNAL_STORAGE`
