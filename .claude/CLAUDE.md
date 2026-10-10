# PuzzleHelper (PuzzleIt)

Android app (Kotlin, XML + Material 3, Hilt, Room, CameraX, OpenCV for box framing). Recognition is 100 % on the phone; sign-in to the owner's own server (PocketBase) is required and keeps the puzzles in sync (see Known limits).
Photograph a loose puzzle piece -> the app shows where it goes on the box image and how to turn it.
The owner speaks French: talk to them in French; code, comments and commit messages in English.

## Product
- Scan box -> rectified image + grid (rows x cols typed or computed) -> piece photo -> up to 4 leads, each a
  piece-sized square on the map, with rotation and confidence. The sheet shows the piece (turned) next to the box at
  the selected lead so the user can check. A hint, not a solver.
- Targets: > 70 % correct first lead, < 3 s per scan on the phone, no network. App size: > 50 MB is fine (owner, 2026-10-08),
  so an on-device model (ONNX Runtime) is allowed.
- Edit / delete a puzzle: round buttons in the puzzle table's top bar (covered by e2e tests - keep them reachable). The box photo can be retaken at creation and in edit.
- Sign-in is mandatory (no guest mode): the app starts on `LoginFragment` when nobody is signed in. Scanning itself stays 100 % on the phone. No "sync now" button, ever (owner).
- Screens that draw their own header with a back button (list, table, scanner, account, history, progress, login) are listed in `MainActivity.ownHeader`: add a new one there, or the toolbar's arrow doubles theirs.

## Code map (`app/src/main/java/com/lilyan_lefevre/puzzleit`) - package by feature, MVVM
- `core/database` (Room, migrations in one file, Hilt module), `core/image` (shared image helpers), `core/ui` (reusable
  views). No other cross-feature package: no `util`, `utils`, `shared`, `constants`.
- `feature/project/` `data/` (Project, ProjectDao, ProjectRepository, ImageStorageManager), `list/` (fragment, adapter,
  ProjectListViewModel), `create/` (ProjectCreation + PuzzleBounds fragments and ViewModels, BoxImageProcessor, GridProcessor).
- `feature/puzzle/` the table: PuzzleWorkingFragment + ViewModel (`ScanState`, follows the project live, edit/delete),
  PuzzleMapView (leads = piece-sized squares, tappable, clipped to the visible map); `capture/` PieceCaptureFragment + ScanFrameView.
- `feature/recognition/` `PieceReranker.kt` + `OnnxEmbedder.kt` (assets/reranker.onnx: embeddings of the piece and of the box squares), `PieceSegmenter.kt` + `OnnxSegmenter.kt`
  (assets/segmenter.onnx = U2-Net-small cuts the piece out; table-colour segmentation is the fallback), `PieceMatcher.kt` - the algorithm, pure Kotlin (JVM-testable): grid pre-cut,
  segmentation, **whole-box network search** (`buildIndex` = embeddings of every half-cell square, built in the background by `PieceRecognizer` and cached next to the reference
  image; `fuse` mixes similarity and colour over ALL candidates, `refine` moves the best leads to sub-cell places; scans use the colour matcher alone until the index is ready; the confidence of a lead
  is its share in a softmax of the fused scores, so it follows the order of the leads; the rows x columns typed at creation are NOT used, the piece size comes from the piece count), outline reading
  (tilt + flat/tab/blank sides -> corner/edge constraints, 4 rotations; with the network search the border constraint is a cost x1.4, a wall otherwise: the reading is often wrong on real pieces),
  5x5 Lab grid descriptor (mean, lightness ramp and contrast removed), confidence. `PieceRecognizer` = Bitmap glue,
  crops the square under the viewfinder, archives the last 200 captures + verdicts in `files/captures`.
- `feature/history/` (`data/`: `ScanRecord` = a scanned piece with its leads as fractions of the box + the person's verdict, DAO, `ScanHistoryRepository`; `HistoryFragment`) and
  `feature/progress/` (`data/`: `ProgressPhoto` + DAO + `ProgressRepository`; `ProgressFragment` takes the photo with the system camera). Both tables are deleted with their puzzle
  (FK cascade; the files live in `files/<projectId>/scans|progress`, removed with the puzzle's folder). DB version 7 (`ProjectMigration5` and `6`, covered by `ScanHistoryTest`; `updatedAt` = last edit of a puzzle, the sync keeps the newest).
  The result sheet asks "is that where the piece goes?" (`PuzzleWorkingViewModel.evaluate`): verdicts are the future real-world training / evaluation data.
- `feature/account/` (`data/`: `PocketBaseClient` (password + PocketBase's OAuth2 flow over `/api/realtime`), `AccountStore` on encrypted prefs, `AccountModule` (OkHttp + prefs), `SyncRepository`, `isAcceptableServer`;
  `LoginFragment` (email/password + one "Continue with ..." button per provider in the server's `auth-methods`), `AccountFragment` (who is signed in, sign out), one `AccountViewModel`). The server is `BuildConfig.PUZZLEIT_SERVER`
  (`-PpuzzleitServer=...` overrides it), not typed. `MainActivity` picks the start destination from `AccountStore` and navigates to/from login when the account appears/disappears (sign-out, 401).
  Repositories that delete things (`ProjectRepository`, `ScanHistoryRepository`, `ProgressRepository`) call `AccountStore.markDeleted` so the next sync tells the server (`deletions` collection, so the other phones delete too).
  Sync rules (`SyncRepository`): deletions replayed then applied, latest `updatedAt` wins for name/pieces/grid, newest `photoId` (date in the file name) wins for the box photo.
  **Principle (owner): every create / edit / delete triggers a request to the server.** The three repositories call `AccountStore.localChange()` after each write (`markDeleted` does too), and `SyncRepository.startAutoSync()`
  (started by `MainActivity`) syncs 2 s after the last change, on whatever screen. Any new write to a Room table must go through a repository and call it. Also synced when the list shows (30 s throttle) and on `MainActivity.onStop`
  (not enough alone: the system camera stops the activity before the photo exists). Covered by `SyncIntegrationTest` (photos taken one after the other reach the server by themselves).
  `Project.photoId` / `deleteBoxFiles()` live in `feature/project/data/BoxFiles.kt` (the box index is cached next to the warped image under its name and goes with it).
  Tests run signed in on throw-away prefs: `TestAccountModule` (androidTest and test source sets) replaces `AccountModule` through Hilt; `ProjectSetupTest` is a Hilt Robolectric test for that reason (no Android Keystore on the JVM).
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
- **Result sheet**: a real `BottomSheetBehavior` with four levels - peek (the leads with their %), half (piece next to the box), full ("why these leads" cards with the blinking `LeadComparisonView`), hidden (the map is free, a "Leads" pill brings it back). Its on-screen position is `getLocationOnScreen`, NOT `top` (the behaviour moves it without changing the layout `top`); set levels in an `OnPreDrawListener` once the new state is laid out. No rows x columns anywhere in the UI (unknown before the border is complete): the position is a zone + percentages.
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
  `main` only once built + tested; then check CI. Quote benchmark numbers in matcher commits.
- **Commits: no `Co-Authored-By` / `Claude-Session` trailers** (owner's explicit choice, history was rewritten without them), author `LilyanLefevre <lilyandu70.7@gmail.com>`
  (`git config user.name/email` are set to it in this clone). Never fake or backdate commit dates (refused once, the owner insisted: still no). Pushing rewritten history needs `--force-with-lease`.
- **CI polling**: `gh run watch` and long `sleep` get reaped when the PC is short of memory; poll with `until [ "$(gh run view <id> --json status --jq .status)" = completed ]; do sleep 30; done` in the background.
  The workflow cancels in-progress runs on a new push: do not push while waiting for a result you need.
- Repo files cannot be written from Bash/Python on this PC (`Bad file descriptor`): use the Edit/Write tools; Bash only for git/gradle/adb. Git Bash mangles `/sdcard` paths: `export MSYS_NO_PATHCONV=1`; binary files from the phone: `adb exec-out run-as ... cat`.
- The `humanizer` skill the owner's global CLAUDE.md asks for is not installed in this session: write texts plainly and say so.
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
  On emulators use the Gradle Managed Devices defined in `app/build.gradle.kts` (same devices on the CI matrix):
  `./gradlew pixel6api34DebugAndroidTest`, `./gradlew smallphoneapi34DebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.lilyan_lefevre.puzzleit.e2e`
  (only the journeys: its 192 MB Java heap OOMs the device tests; Nexus One profile, 320 dp wide: the half level
  of the result sheet cannot show everything there) or `allDevicesDebugAndroidTest`. They never touch a plugged phone; do not run
  `connectedDebugAndroidTest` while one is connected.
- 19 instrumented tests: `PieceRecognizerDeviceTest`, `e2e/ScanFlowTest` (list, table, scan, leads, blurry, empty,
  viewfinder, edit, delete, rate + history, progress screen) and `e2e/DemoTourTest` (records the README video into `/sdcard/demo.mp4` on the puzzle named by
  `-e demoPuzzle "La vague"` (never "the first one": the phone holds the owner's private puzzles such as "Famille", which must never be filmed, captured or published; the list is not filmed either),
  read only, real swipes, animations ON; skipped without that argument. Encode with ffmpeg `-vf fps=60` H.264,
  no ffmpeg installed: `pip install imageio-ffmpeg`. Never let the recording run into the home screen). Screenshots (PixelCopy) land in `/sdcard/Android/data/com.lilyan_lefevre.puzzleit/files/shots`.
  README images: `DemoTourTest#readmeShots` (table, result, why, map) and `#readmeBlurry`, same `-e demoPuzzle "La vague"`, files `readme_*.png`; scale to 405x868 (ffmpeg, lanczos) into `documentation/screenshots`.
  `list.png` is never taken on the phone (the list shows every puzzle, private ones included): `DemoTourTest#readmeList` runs on the emulator only (`Build.HARDWARE` ranchu/goldfish), creates three public-domain puzzles from
  `-e demoBoxDir <dir>` (wave.jpg, starry.jpg, lilies.jpg from Wikimedia Commons) and deletes them. Start the AVD `puzzle34` (`emulator -avd puzzle34 -no-window -gpu swiftshader_indirect`), `adb -s emulator-5554 shell wm size 1440x3088`
  and `wm density 560` (same proportions as the phone), `cmd locale set-app-locales com.lilyan_lefevre.puzzleit --locales fr-FR`, install with `adb -s emulator-5554 install` (never `installDebug`: it also hits the plugged phone).
- Phone tips: animations off, `adb shell cmd notification set_dnd on`, `adb shell svc power stayon true`; wireless adb
  drops often (`adb devices` before running). Never use `UiAutomation.takeScreenshot` in Espresso (steals focus).
- Tests touch the real app DB: they only insert/delete project id `e2e-scan-flow`. Demo puzzles `demo-*` on the phone
  are for README screenshots.
- Test fixtures must look like real photos (grain, slight blur): smooth images sit at `PieceMatcher.MIN_SHARPNESS`
  and flip between devices.
- CI (`.github/workflows/ci.yml`): build + unit tests, then the instrumented suite on two Gradle Managed Devices (a Pixel 6 and a small phone), one CI job each.

## Release
- Tag `v<versionName>` (`app/build.gradle.kts`) -> `.github/workflows/release.yml` builds the signed APK and creates the GitHub release.
  Signing comes from the env `RELEASE_KEYSTORE_FILE/_PASSWORD`, `RELEASE_KEY_ALIAS/_PASSWORD` (repo secrets `RELEASE_KEYSTORE_BASE64` etc.);
  without them `assembleRelease` gives an unsigned APK. The keystore and its passwords are in `C:\Users\Lilyan\.android-keys\` (outside the
  repo, never commit them; lose the key and no update can be installed over an existing install: tell the owner to back it up).
- `assembleRelease` needs the network (lint-gradle is not in the offline cache). The APK is ~220 MB (ONNX Runtime + OpenCV for 4 ABIs).

## Benchmarks (opt-in JVM tests, not in CI; data rebuilt by scripts)
- `DatasetReplayTest` + `tools/dataset/prepare_puzzle_map.py <dir>` -> `PUZZLE_DATASET_DIR`: Puzzle-Map (CC-BY-4.0),
  7 puzzles, 643 real hand-held photos with row/col/quarter-turn/sides (the annotated sides are exact, unlike the app's reading). Current: 84 % exact cell (89 % with the border as a wall),
  99 % in the 4 leads, rotation 88 % (55 / 90 / 62 % colour matcher alone). `prepare_puzzle_map.py` needs Pillow (no more macOS `sips`).
- `ImageBankBenchmarkTest` + `tools/dataset/prepare_image_bank.py <dir>` -> `PUZZLE_IMAGES_DIR`: 24 real images as
  500-piece puzzles, 576 pieces rendered by `PiecePhotos` (any angle, side light, white balance, exposure, table).
  Full pipeline. Current (re-ranker + whole-box search + U2-Net): 88 % exact (97 % textured, 90 % mixed, 73 % flat-heavy), outline 97 %, found 100 %.
  Set `PUZZLE_RERANKER=app/src/main/assets/reranker.onnx` and `PUZZLE_SEGMENTER=app/src/main/assets/segmenter.onnx` (absolute paths) to enable them in the benchmarks;
  `PUZZLE_GLOBAL=0` goes back to colour top-30 + re-rank, `PUZZLE_BORDER_PENALTY` and `PUZZLE_EMBED_COLOUR_W` tune the fusion, `PUZZLE_REFINE=0` turns the sub-cell refinement off, `PUZZLE_INTERIOR_COST` (border prior for pieces read as interior, 2.0) and `PUZZLE_RESID_WEIGHT` (pixel check of the best 8 leads, 0.7: real photos 35 -> 50 % right, hand-held Puzzle-Map 84 -> 81 %),
  `PUZZLE_CALIB=1` prints score + correctness of the best 8 leads of every scan (fit the softmax temperatures `CONF_TEMPERATURE` / `RERANK_TEMPERATURE` on it).
- `OwnPuzzlesTest` -> `PUZZLE_OWN_DIR` (folders `famillez`, `lavague`: box, grid, captures, `all/*.bmp`, `truth.txt`, `leads.json`, `cuts/`): the owner's REAL photos with exact positions labelled by
  the owner (`tools/dataset/label_pieces.html`: drag the cut-out piece onto the box; `make_label_cuts.py` builds the cut-outs), next to simulated pieces cut from the same boxes. Treat those labels
  as exact. Current: Famillez 30 % exact / 55 % in 4 leads (20 captures = 6 distinct pieces), La vague 1 piece of 3. Too small to tune on: do not over-fit it.
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
- Emulator: Android SDK in `%LOCALAPPDATA%\Android\Sdk`, AVD `puzzle34` (API 34 google_apis x86_64, WHPX). Start it
  with `ssh pc 'powershell -ExecutionPolicy Bypass -File C:\dev\PuzzleHelper\tools\pc\start_emulator.ps1'` (launched
  through WMI: OpenSSH kills its children on logout; session 0 has no GPU, hence swiftshader), then
  `ssh pc 'C:\dev\PuzzleHelper\tools\pc\instrumented.cmd'` (pulls `main`, runs `connectedDebugAndroidTest`).
  Error dialogs must stay hidden: a first-boot system ANR stole the window focus and failed 7 tests.

## Known limits / next steps
### State on 2026-10-11 (before v0.2.0)
- v0.1.0 released (signed APK on GitHub, release.yml; keystore in `C:\Users\Lilyan\.android-keys\`, to be backed up by the owner). `main` is green on CI (Pixel 6 + small phone managed devices). v0.2.0 is next:
  bump `versionName` + `versionCode` in `app/build.gradle.kts`, commit, tag `v0.2.0`. Done since v0.1.0: mandatory login (+ Google and other providers), production server built in, retake of the box photo
  (create and edit), double back arrows fixed, sync gaps closed (deletions, box photo, latest edit), new app icon (supplied PNGs, adaptive icon built with a 12 % inset; no monochrome/themed icon yet).
- Server **in production**: PocketBase on the owner's Raspberry Pi (host `limar`, `ssh raspi` works with `~/.ssh/id_ed25519_raspi`, user `lilyan`, 64-bit, Docker), behind a Cloudflare Tunnel at `https://puzzleit.lilyan.app`.
  A push touching `server/` rebuilds the image (GHCR, public package) and a GitHub self-hosted runner on the Pi redeploys it (`.github/workflows/server.yml`, clone in `~/PuzzleIt`; documented in `server/README.md`).
  The Pi's old services (gitea, mailhog, portainer, traefik, gitea-runner) were removed at the owner's request, their data folders are left in `/home/docker`; `code-server` and `whoami` containers are still there (the owner asked to remove them, the permission classifier refused: they run it themselves).
  The Google provider still has to be configured by the owner (Google Cloud OAuth client + PocketBase dashboard, steps in `server/README.md`); until then the login screen shows no provider button. Sign-up by password must be locked once their account exists.
- Verified against a real local PocketBase (download its release, check the checksum, `pocketbase serve --dir <tmp> --migrationsDir server/pb_migrations --http=127.0.0.1:8095`; port 8090 is taken on the owner's PC), then
  `POCKETBASE_URL=http://127.0.0.1:8095 ./gradlew testDebugUnitTest --tests "*SyncIntegrationTest*"` (3 scenarios) and, with a fake Google provider enabled by a temporary migration, `POCKETBASE_OAUTH_URL=... --tests "*OAuthIntegrationTest*"`.
  OAuth gotcha found that way: the `state` sent to the provider must be the realtime client id (that is how PocketBase finds the connection to push the code to).
- Repo files cannot be written from Bash (permission denied, also `rm`/`cp`): use Edit/Write, and PowerShell `Copy-Item`/`Remove-Item` for binaries (icons).
- **Security rule from the owner: reuse audited pieces, invent nothing** (also in memory). Auth, hashing, tokens, file tokens, access rules = PocketBase; token = Jetpack Security
  `EncryptedSharedPreferences` (`AccountModule`, excluded from backups); `http://` only for private/home addresses (`isAcceptableServer`, `ServerAddressTest`).

### Tasks now
1. **Release v0.2.0** (bump, tag, check the release workflow, tell the owner to back up the keystore). Before: the owner configures Google, locks the sign-up, and tries the login on their phone in 4G (never done with a real Google account).
2. Sync gaps left: no sync while the app is closed (WorkManager, a dependency to ask the owner about; the app syncs on show and when it goes to the background); a puzzle edited on two phones before they sync keeps only the later edit.
   The login screen reads the providers once when it opens: nothing is shown (and nothing said) if the server was unreachable then (a retry or a message is open).
3. Themed (monochrome) app icon: needs a transparent silhouette of the logo, the supplied zip has none. The Play Store icon (`playstore-icon.png`, 512 px) and the iOS sets of that zip are not in the repo.
4. Use the progress photos to help the localisation (the reason they exist), measure the box index build time on the phone for La vague (never done).
5. Owner's phone: wireless adb drops often; run tests there with `installDebug installDebugAndroidTest` + `am instrument` (animations off, restore them after), never `connectedDebugAndroidTest`.
- First-lead accuracy is below target on the owner's real photos (Famillez 30 %), especially on near-uniform pieces (white gutter between two photos, plain foam/sky of La vague). Tried and rejected
  (`documentation/07`, `08`): fine-tuning on Puzzle-Map real photos (hand-held; hurt the owner's table photos), naive log-colour matching, the full U2-Net. Pablo Moreira's DINOv2 piece classifier
  (363 MB, not embeddable) finds the white-gutter piece our model never finds: a patch-level cross-attention head is the research lead.
- The box index (up to ~3,900 embeddings for 1,000 pieces) is built in the background on the phone: its build time there is not measured yet.
- When a session runs on the owner's PC (not the Mac), the data is in `C:\dev\data` (`real`, `pm4` = Puzzle-Map, `bank`, `train-images`) and the venv in `C:\dev\venv-puzzle`.
- The project lives in iCloud Documents: iCloud creates `* 2.*` duplicates in `app/build` and Gradle fails in
  `parseDebugLocalResources`. Fix: `rm -rf app/build`. Moving the project out of iCloud would fix it for good.
- onnxruntime sessions run WITHOUT memory pattern and CPU arena (`OnnxEmbedder`, `OnnxSegmenter`): the batches have many different sizes and
  with them native memory grew 30 MB per scan (408 MB after the box index) until a 2 GB device killed the app. `PieceRecognizerDeviceTest` prints the memory.
- Leads 2-4 get a derived confidence (best confidence scaled by their own score).
