# PuzzleIt

[![CI](https://github.com/LilyanLefevre/PuzzleHelper/actions/workflows/ci.yml/badge.svg)](https://github.com/LilyanLefevre/PuzzleHelper/actions/workflows/ci.yml)

Stuck on a 1000-piece sky? Photograph a loose piece and PuzzleIt shows **where it goes on the box image**,
how to turn it, and how sure it is. Android, Kotlin, fully offline.

| My puzzles | Puzzle table | Piece found |
|---|---|---|
| ![](docs/screenshots/list.png) | ![](docs/screenshots/table_real.png) | ![](docs/screenshots/result_real.png) |

A piece cut from the fjord photo, turned 90° and laid on a dark table: found at its exact cell, with the right
rotation and 88 % confidence.

<details>
<summary>More states (from the instrumented tests, synthetic box art)</summary>

| Synthetic table | Result | Second lead | Blurry photo |
|---|---|---|---|
| ![](docs/screenshots/1_table.png) | ![](docs/screenshots/3_result.png) | ![](docs/screenshots/4_second_lead.png) | ![](docs/screenshots/5_blurry.png) |

</details>

Demo box photos: Alexey Topolyanskiy, Andrew Ridley and Christian Joudrey on [Unsplash](https://unsplash.com) (Unsplash License).

## How it works

```mermaid
flowchart LR
  A[Box photo] --> B[Rectified image + grid]
  P[Piece photo] --> C[Square under the frame] --> D[Segment piece] --> O[Outline: tilt + flat sides] --> E[17-zone Lab descriptor]
  B --> F[Candidates every half cell]
  E --> G{4 rotations<br/>corner / edge constraints}
  F --> G --> H[Best + 3 leads] --> I[Confidence]
  I --> J[Cell / zone / rough area]
```

Colour is compared in 17 zones (centre + 2 rings x 8 sectors) of a disc scaled to the piece size, so scale and
lighting matter little. The piece's outline is read first: it is straightened from its straight edges, and its flat
sides send corner and edge pieces to the matching border cells with the only rotation that fits.

The science, step by step (in French): [`documentation/`](documentation/README.md).

## Build and test

```bash
./gradlew assembleDebug testDebugUnitTest      # JDK 21
./gradlew connectedDebugAndroidTest            # emulator only: uninstalls the app (wipes its data) afterwards
```

## Architecture

Single activity, MVVM, Hilt, Room, CameraX, OpenCV (box rectification). The matching algorithm is plain Kotlin,
so it runs and is tested on the JVM.

```mermaid
flowchart TB
  subgraph UI
    L[ProjectListFragment] --> W[PuzzleWorkingFragment]
    W --> C[PieceCaptureFragment]
    C -- photo path --> W
    W --- M[PuzzleMapView<br/>pan, zoom, spotlight]
  end
  W --> VM[PuzzleWorkingViewModel<br/>ScanState: Idle, Analyzing, Result, Blurry, NoPiece]
  VM --> R[PieceRecognizer<br/>Bitmap glue]
  R --> PM[PieceMatcher<br/>pure Kotlin]
  VM --> Repo[ProjectRepository] --> DB[(Room)]
```

| Path | Role |
|---|---|
| `feature/recognition` | `PieceMatcher` (algorithm), `PieceRecognizer`, `PuzzleMapView`, `PuzzleLoaderView` |
| `feature/puzzle` | working screen, scan state machine, piece capture |
| `feature/project` | project list, creation, box bounds |
| `shared` | Room database, image utils, shared views |

## Tests

| Suite | Where | What |
|---|---|---|
| `PieceMatcherTest` | JVM | synthetic box art, rotated pieces, accuracy, rotation, blur, empty table |
| `PieceRecognizerDeviceTest` | device / emulator | real JPEG decode, timing (< 3 s per scan), failure paths |
| `ScanFlowTest` | device / emulator | full journey: list, table, scan result, leads, blurry, no piece, viewfinder |

CI runs everything on each push to `main`, including an Android 14 emulator.

## Contributing
Conventional commits (`feat:`, `fix:`, `test:`, `docs:`, `ci:`, `chore:`), one topic per commit.
Project notes for AI assistants live in [`.claude/CLAUDE.md`](.claude/CLAUDE.md).
