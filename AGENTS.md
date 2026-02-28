# AGENTS.md

## Project Overview

APKM Installer is an Android app that installs `.apkm` split-APK packages from APK Mirror.
It uses Kotlin, Jetpack Compose, Material 3, Hilt, and Clean Architecture (MVVM).

## Architecture

```
app/src/main/kotlin/com/apkm/installer/
├── data/                  # Data layer: ApkmParser (ZIP extraction), SplitApkInstaller (PackageInstaller API)
├── di/                    # Hilt dependency injection module (AppModule)
├── domain/
│   ├── model/             # Domain models: ApkmPackageInfo, InstallState
│   └── usecase/           # Use cases: ParseApkmUseCase, InstallPackageUseCase
├── presentation/
│   ├── detail/            # Package detail screen + ViewModel
│   ├── home/              # Home screen (file picker) + ViewModel
│   ├── install/           # Install progress screen + ViewModel
│   ├── navigation/        # Navigation graph (AppNavGraph)
│   └── theme/             # Material 3 theme, typography, color
├── ApkMInstallerApp.kt    # Hilt Application class
└── MainActivity.kt        # Single Activity entry point
```

## Build & Quality Commands

```bash
./gradlew assembleDebug                    # Build debug APK
./gradlew testDebugUnitTest                # Unit tests (JVM)
./gradlew detekt                           # Kotlin static analysis
./gradlew lintDebug                        # Android Lint
./gradlew spotlessCheck                    # Formatting check (ktlint)
./gradlew spotlessApply                    # Auto-fix formatting
./gradlew validateDebugScreenshotTest      # Compose preview screenshot tests
./gradlew updateDebugScreenshotTest        # Update screenshot reference images
./gradlew pixel6api34DebugAndroidTest      # Instrumentation tests on Gradle Managed Device
```

## Key Technical Decisions

- **AGP 9.0.0** — Kotlin support is built into AGP; no separate `kotlin-android` plugin needed.
- **PackageInstaller API** — Split APKs are installed via the Android `PackageInstaller` session API with `CompletableDeferred` for async callback bridging. Shell `pm install` commands are NOT used (requires system permissions).
- **Annotation targets** — Use `@param:ApplicationContext` (not bare `@ApplicationContext`) to avoid KT-73255 warnings with Kotlin 2.2+.
- **Compose functions** — PascalCase naming is allowed for `@Composable`, `@Preview`, and `@PreviewTest` functions (configured in `.editorconfig`).
- **Screenshot testing** — Uses the `com.android.compose.screenshot` plugin (0.0.1-alpha13). Previews need `@PreviewTest` annotation. Reference images live in `app/src/screenshotTestDebug/reference/`.
- **Hilt testing** — Instrumentation tests use `hilt-android-testing` with a custom `HiltTestRunner`.

## Test Structure

```
app/src/test/          # JVM unit tests (ApkmParserTest, ParseApkmUseCaseTest)
app/src/androidTest/   # Instrumentation tests (HomeScreenTest, PackageDetailScreenTest, InstallProgressScreenTest)
app/src/screenshotTest/ # Compose preview screenshot tests (ScreenshotPreviews.kt)
```

## CI

Seven parallel jobs run on every push/PR (`.github/workflows/ci.yml`):
Build, Lint, Detekt, Spotless, Unit tests, Instrumentation tests (GMD), Screenshot tests.

Releases are triggered by pushing a version tag (`v*`). See `.github/workflows/release.yml`.
