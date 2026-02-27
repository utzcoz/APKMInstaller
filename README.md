# APKM Installer

A modern, ads-free Android app for installing `.apkm` split-APK packages downloaded from [APK Mirror](https://www.apkmirror.com/).

## Features

- 📦 **Open & install `.apkm` files** — browse with the system file picker or open directly from your file manager
- 🔍 **Package preview** — shows app icon, name, package name, version, size, split count, and declared permissions before installing
- 📊 **Step-by-step progress** — animated Extract → Verify → Install progress with success/failure states
- 🎨 **Material You design** — dynamic color on Android 12+, beautiful static palette on older devices
- 🌗 **Light & dark themes**
- 🚫 **Zero ads, zero tracking**

## Tech Stack

| Area | Technology |
|------|------------|
| Language | Kotlin 2.2.21 |
| UI | Jetpack Compose + Material Design 3 |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt 2.57.2 |
| Navigation | Navigation Compose |
| Image loading | Coil 2.7.0 |
| Async | Coroutines + StateFlow |

## Requirements

- Android 8.0 (API 26) or higher
- "Install unknown apps" permission must be granted to this app in Android settings

## How .apkm files work

A `.apkm` file is a ZIP archive used by APK Mirror to distribute split-APK packages. It contains:
- `base.apk` — the main application APK
- `split_config.*.apk` — optional architecture/language/density split APKs
- `info.json` — optional APK Mirror metadata

The app extracts all APKs to a temporary cache directory and installs them together as a single split-APK session using the Android `PackageInstaller` API.

## Building

```bash
./gradlew assembleDebug
```

## Testing

### Unit tests
```bash
./gradlew testDebugUnitTest
```

### Instrumentation tests (Gradle Managed Device — pixel6api34 / aosp_atd / API 34)
```bash
./gradlew pixel6api34DebugAndroidTest
```
