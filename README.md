# Instant3D Slicer

An offline-first Android material estimator for 3D printing. The first milestone establishes the Kotlin, Jetpack Compose, Material 3 and Room project foundation, with dark Home and Projects screens and bottom navigation.

## Development without Android Studio

The project uses the Gradle Wrapper and builds on GitHub-hosted Ubuntu runners. Android Studio is not required. Local Android SDK setup is optional; the GitHub Actions workflow installs the SDK it needs.

## Build

On a machine with JDK 17 and Android SDK Platform 35 configured:

```powershell
./gradlew.bat testDebugUnitTest assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions APK

Push to `main` or open a pull request to run the Android build workflow. Open the successful workflow run in GitHub Actions and download the `instant3d-slicer-debug-apk` artifact. Extract the APK and open it on an Android phone to install; Android may ask you to allow installs from that source.

## Roadmap

1. Project foundation and navigation (current).
2. STL import and mesh validation.
3. 3D model viewer.
4. Dimensions and volume.
5. Printer and filament profiles.
6. Modular material estimation.
7. Improved wall, infill and shell estimates.
8. Compare estimates with Bambu Studio and OrcaSlicer.

Estimates will be labeled approximate until validated against a slicing engine. This project does not connect to printers.
