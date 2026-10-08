# PuzzleHelper (PuzzleIt)

Android app (Kotlin, XML + Material 3, Hilt, Room, CameraX, OpenCV for box framing). 100 % offline.
Photograph a loose puzzle piece -> the app shows where it goes on the box image and how to turn it.
The owner speaks French: talk to them in French; code, comments and commit messages in English.

## Product
- Scan box -> rectified image + grid (rows x cols typed or computed) -> piece photo -> up to 4 leads, each a
  piece-sized square on the map, with rotation and confidence. The sheet shows the piece (turned) next to the box at
  the selected lead so the user can check. A hint, not a solver.
- Targets: > 70 % correct first lead, < 3 s per scan on the phone, app < 50 MB, no network.
- Edit / delete a puzzle: round buttons in the puzzle table's top bar (covered by e2e tests - keep them reachable).

## Code map (`app/src/main/java/com/lilyan_lefevre/puzzleit`) - package by feature, MVVM
- `core/database` (Room, migrations in one file, Hilt module), `core/image` (shared image helpers), `core/ui` (reusable
  views). No other cross-feature package: no `util`, `utils`, `shared`, `constants`.
- `feature/project/` `data/` (Project, ProjectDao, ProjectRepository, ImageStorageManager), `list/` (fragment, adapter,
  ProjectListViewModel), `create/` (ProjectCreation + PuzzleBounds fragments and ViewModels, BoxImageProcessor, GridProcessor).
- `feature/puzzle/` the table: PuzzleWorkingFragment + ViewModel (`ScanState`, follows the project live, edit/delete),
  PuzzleMapView (leads = piece-sized squares, tappable, clipped to the visible map); `capture/` PieceCaptureFragment + ScanFrameView.
- `feature/recognition/` `PieceMatcher.kt` - the algorithm, pure Kotlin (JVM-testable): grid pre-cut, segmentation
  (Mahalanobis to the table colour), outline reading (tilt + flat/tab/blank sides -> corner/edge constraints, 4 rotations),
  5x5 Lab grid descriptor (mean, lightness ramp and contrast removed), confidence. `PieceRecognizer` = Bitmap glue,
  crops the square under the viewfinder, archives the last 200 captures + verdicts in `files/captures`.
- Fragments only render state, handle system intents and navigate; IO, image work and validation live in ViewModels
  or injected classes.
- Docs: `documentation/` (French, numbered pages: the science 01-06, validation 07, AI experiment 08) and
  `documentation/screenshots` (README images). No other docs folder.

## Code conventions (follow the existing patterns)
- **Placement**: a new screen = `feature/<feature>/<screen>/` with its Fragment + `@HiltViewModel` ViewModel. Shared by
  several features -> `core/<topic>`. Pure algorithms stay free of Android types so they run on the JVM.
- **ViewModel state**: `private val _x = MutableStateFlow(...)` + `val x: StateFlow<...> = _x.asStateFlow()`. Screens with
  several states use a sealed interface (see `ScanState`). One-shot events are nullable StateFlows the fragment clears
  after handling (`errorShown()`, `savedHandled()`). Live data from Room: `repository.flow().stateIn(viewModelScope, ...)`.
- **Errors**: string resource ids (`@StringRes Int`), never hard-coded text; every string exists in `values` and
  `values-fr`.
- **Fragments**: ViewBinding with `_binding` nulled in `onDestroyView`; collect in
  `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { launch { ... } } }`; navigation with nav
  graph actions; transitions `MaterialSharedAxis` X between screens, Y for the camera.
- **Threading**: IO / image work in injected `@Singleton` classes with `withContext(Dispatchers.IO|Default)`; Bitmaps
  are decoded with a size cap (`inSampleSize`).
- **Custom views**: draw everything in `onDraw` (no child views), one infinite `ValueAnimator` clock started/stopped in
  attach/detach, colours from `R.color`.
- **Comments**: KDoc one-liner on each class saying what it is for; inline comments only for the non-obvious *why*.
  Imports sorted, no unused ones, no wildcard imports of project packages.
- **UI design**: dark "puzzle night" theme only. Use the tokens, not raw values: colours `puzzle_*` (`background`,
  `surface`, `surface_high`, `outline`, `primary` = citron accent, `secondary` = violet, `warning`, `error`),
  font `@font/space_grotesk_family`, text styles `Text.Display` (titles) / `Text.Eyebrow` (small caps labels), widgets
  `Widget.Puzzle.Button`, `Widget.Puzzle.IconButton` (48dp round buttons on the map), `Widget.Puzzle.Chip`,
  shapes `Shape.Puzzle.Small/Medium/Large` (12/20/28dp). Bottom sheet = `bg_sheet`, rounded 32dp top corners.
  Animate state changes (`TransitionManager`, `animate()`), keep motion short (300-900 ms).
- **Tests**: JVM tests for algorithm logic (one runnable check per non-trivial piece of logic), Espresso e2e tests for
  user journeys with `waitFor { ... }` polling instead of sleeps for async states; e2e tests seed their own data.

## Working with the owner
- Answer in French, concisely; say plainly what failed or was not verified, and own mistakes.
- Show UI changes with screenshots taken on the phone (PixelCopy shots from the e2e tests) sent with SendUserFile.
- Ask before decisions that are theirs (merging over their work, deleting their data, adding heavy dependencies or
  AI models to the app); otherwise act and report.

## Working rules (learned the hard way)
- Conventional commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `ci:`, `chore:`), one topic per commit, push to
  `main` only once built + tested; then check CI with `gh run watch`. Quote benchmark numbers in matcher commits.
- Measure before and after every matcher change on BOTH benchmarks below; keep a change only if it holds on both.
  Document results (including rejected tries) in `documentation/07-validation.md`.
- Trace a real failure on real data before "fixing": pull the phone captures and replay them (`RealPhotoReplayTest`).
- When merging someone else's work, check no feature became unreachable (it happened with edit/delete).
- Dark-only theme ("puzzle night"); colours in `values/colors.xml`, strings in both `values` and `values-fr`.
- Delete dead code instead of parking it (`.bak` files are not allowed); history keeps it.

## Testing
- Unit (JVM, JDK 21 needed by Robolectric): `./gradlew testDebugUnitTest` (includes `PieceMatcherTest`).
- **Never run `./gradlew connectedDebugAndroidTest` on the owner's phone**: Gradle uninstalls the app afterwards and
  wipes its data (it already deleted their puzzles once). On a real phone: `./gradlew installDebug installDebugAndroidTest`
  then `adb shell am instrument -w com.lilyan_lefevre.puzzleit.test/com.lilyan_lefevre.puzzleit.HiltTestRunner`.
  `connectedDebugAndroidTest` is for emulators / CI only.
- 13 instrumented tests: `PieceRecognizerDeviceTest`, `e2e/ScanFlowTest` (list, table, scan, leads, blurry, empty,
  viewfinder, edit, delete). Screenshots (PixelCopy) land in `/sdcard/Android/data/com.lilyan_lefevre.puzzleit/files/shots`.
- Phone tips: animations off, `adb shell cmd notification set_dnd on`, `adb shell svc power stayon true`; wireless adb
  drops often (`adb devices` before running). Never use `UiAutomation.takeScreenshot` in Espresso (steals focus).
- Tests touch the real app DB: they only insert/delete project id `e2e-scan-flow`. Demo puzzles `demo-*` on the phone
  are for README screenshots.
- Test fixtures must look like real photos (grain, slight blur): smooth images sit at `PieceMatcher.MIN_SHARPNESS`
  and flip between devices.
- CI (`.github/workflows/ci.yml`): build + unit tests, then the instrumented suite on an Android 14 emulator.

## Benchmarks (opt-in JVM tests, not in CI; data rebuilt by scripts, macOS `sips`)
- `DatasetReplayTest` + `tools/dataset/prepare_puzzle_map.py <dir>` -> `PUZZLE_DATASET_DIR`: Puzzle-Map (CC-BY-4.0),
  7 puzzles, 643 real hand-held photos with row/col/quarter-turn/sides. Current: 56 % exact cell, 90 % in the 4 leads,
  rotation 64 %.
- `ImageBankBenchmarkTest` + `tools/dataset/prepare_image_bank.py <dir>` -> `PUZZLE_IMAGES_DIR`: 24 real images as
  500-piece puzzles, 576 pieces rendered by `PiecePhotos` (any angle, side light, white balance, exposure, table).
  Full pipeline. Current: 64 % exact (75 % textured, 63 % mixed, 51 % sky/sea-heavy), outline 96 %, found 99 %.
- `RealPhotoReplayTest` -> `PUZZLE_REAL_DIR`: phone captures (`adb pull .../files/captures`) + box
  (`adb exec-out run-as com.lilyan_lefevre.puzzleit cat files/<project>/puzzle/extraites/*_warped.jpg`), BMP via sips.
- `PUZZLE_DUMP_DIR` makes the first two export each piece + the top-30 leads (`LeadDump`) for off-device experiments.

## Remote PC (heavy work: AI, long benchmarks)
- `ssh pc` (key auth, alias in `~/.ssh/config`): Windows 11, Ryzen 5 7600X, 32 GB, **AMD Radeon RX 6800** -> PyTorch via
  **DirectML** (`torch-directml`), not CUDA/ROCm. Default remote shell is `cmd.exe`.
- `C:\dev\PuzzleHelper` (git clone of the public repo), `C:\dev\venv-puzzle` (Python 3.12: torch-directml, torchvision,
  pillow, numpy), `C:\dev\data` (benchmark data + dumps, sent with `tar -cf - ... | ssh pc "tar -xf - -C C:/dev/data"`).
- Run: `ssh pc 'cd /d C:\dev && venv-puzzle\Scripts\python <script> ...'`. Python on the Mac is a python.org build
  without certificates: download with `curl`, not `urllib`.
- Not set up yet: Android SDK / emulator on the PC (the owner wants instrumented tests to run there).

## Known limits / next steps
- First-lead accuracy below target on sky/sea-heavy images and on real hand-held photos; the right cell is in the
  top-30 leads 97 % of the time, so a better re-ranker has room. Pretrained networks did not help enough
  (`documentation/08-ia.md`); next: train a small contrastive model on synthetic pairs from `PiecePhotos`.
- Rotation is the weak point on Puzzle-Map (64 %).
- Segmentation needs contrast between piece and table.
- The project lives in iCloud Documents: iCloud creates `* 2.*` duplicates in `app/build` and Gradle fails in
  `parseDebugLocalResources`. Fix: `rm -rf app/build`. Moving the project out of iCloud would fix it for good.
- Leads 2-4 get a derived confidence (best confidence scaled by their own score).
