# Mystecetus Narrative Generator

Native Android app for creating standardized visual-detection narratives from Mystecetus screen photographs or manual entry.

## Included

- Select multiple screenshots or take a photograph with the phone camera.
- Bundled on-device ML Kit OCR; narrative generation does not require a server.
- Conservative extraction: ambiguous values remain blank instead of being guessed.
- Reviewable manual form covering initial detection, counts, behavior, CPA, final detection, mitigation, and piling status.
- Required-field validation before generation.
- Editable result with copy, share, and local save controls.
- Detection history keyed by V-number.
- Persistent reminder during processing and on the result screen: **Make an effort line for the start and end of the detection.**

## Build

1. Open this folder in Android Studio Ladybug or newer.
2. Allow Gradle to sync the project.
3. Select **Build > Build APK(s)**.
4. The debug APK will be written to `app/build/outputs/apk/debug/app-debug.apk`.

Minimum Android version: Android 8.0 (API 26).

## Download on Android

Open the repository's **Releases** page and select **Latest Android Build**, then download `app-debug.apk`. Android may ask permission to install apps from the browser or GitHub app used for the download.

Every push to `main` also runs the **Build Android APK** workflow. Its APK is available from the workflow run as `Mystecetus-Narrative-Generator-APK` for 90 days.

## Important workflow

OCR results are never treated as final. Mystecetus photographs can contain glare, truncated cells, and several neighboring V-number rows. After photo processing, compare every populated field against the source images before generating the narrative.
