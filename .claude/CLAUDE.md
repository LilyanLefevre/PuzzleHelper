# PuzzleHelper (PuzzleIt)

Android app (Kotlin, XML + Material 3, Hilt, Room, CameraX). 100 % offline.
Photograph a loose puzzle piece -> the app tells where it goes on the box image.

## Product (from the former BMAD PRD, condensed)
- MVP: scan box -> virtual pre-cut grid -> piece photo (centre 70 %) -> position suggestion, 4+ rotations tested.
- Confidence-adaptive answer: exact cell / 3x3 zone / rough area. Autonomy first: it is a hint, not a solver.
- Targets: >70 % correct suggestions, < 3 s per recognition, app < 50 MB, no network.
- Out of scope for now: completed-zone detection, history/export, community features, AI models.

## Code map (`app/src/main/java/com/lilyan_lefevre/puzzleit`)
- `feature/recognition/PieceMatcher.kt` - the algorithm, pure Kotlin (JVM-testable). Grid pre-cut, piece segmentation,
  17-zone Lab descriptor (centre + 2 rings x 8 sectors), 8 rotation shifts, confidence. `PieceRecognizer` = Bitmap glue.
- `feature/recognition/PuzzleMapView.kt` - pan/zoom box image, spotlight, scan sweep. `PuzzleLoaderView` - loader.
- `feature/puzzle/` - working screen (`PuzzleWorkingFragment` + ViewModel state machine `ScanState`), `PieceCaptureFragment`.
- `feature/project`, `feature/storage`, `shared/` - projects, Room DB, image utils (pre-existing).
- A reference image = `Project.warpedPath` (rectified) else `imagePath`; grid = `gridRows/gridCols` else computed.

## Conventions
- Conventional commits (`feat:`, `fix:`, `test:`, `ci:`, `chore:`), one topic per commit, push when tested.
- Dark-only theme ("puzzle night"); colours in `values/colors.xml`, strings in `values` and `values-fr`.
- Keep matching logic out of Android classes so it stays unit-testable.

## Testing
- **Never run `./gradlew connectedDebugAndroidTest` on a personal phone**: Gradle uninstalls the app afterwards and
  wipes its data (projects, images). On a real phone use `installDebug installDebugAndroidTest` + `adb shell am instrument`.
  `connectedDebugAndroidTest` is for emulators / CI only.
- Unit (JVM, JDK 21 needed by Robolectric): `./gradlew testDebugUnitTest` (includes `PieceMatcherTest`, synthetic end-to-end).
- Device/emulator: `./gradlew connectedDebugAndroidTest` (`PieceRecognizerDeviceTest`, `e2e/ScanFlowTest`).
  Direct run, keeps the app and screenshots: `adb shell am instrument -w com.lilyan_lefevre.puzzleit.test/com.lilyan_lefevre.puzzleit.HiltTestRunner`
  then `adb pull /sdcard/Android/data/com.lilyan_lefevre.puzzleit/files/shots`.
- Phone tips: turn animations off, `adb shell cmd notification set_dnd on`, `svc power stayon true`; wireless adb drops often.
- Do not use `UiAutomation.takeScreenshot` in Espresso tests (steals window focus); use PixelCopy.
- Tests touch the real app DB: they only insert/delete project id `e2e-scan-flow`.

## Known limits / next steps
- Accuracy on real piece photos is not measured yet (only synthetic fixtures). Repetitive images (sky, water) are the weak spot.
- Leads 2-3 get a derived confidence (best confidence scaled by their own score), not an independent one.
- The camera/PieceBounds flow from `main` (device photo app + quad crop) is still in the nav graph, unused by the scan flow.
- Test fixtures must look like real photos (grain, real blur): smooth synthetic images sit at the sharpness threshold
  (`PieceMatcher.MIN_SHARPNESS`) and make tests flip between devices.
