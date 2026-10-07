# PuzzleIt

[![CI](https://github.com/LilyanLefevre/PuzzleHelper/actions/workflows/ci.yml/badge.svg)](https://github.com/LilyanLefevre/PuzzleHelper/actions/workflows/ci.yml)

Stuck on a 1000-piece sky? Photograph a loose piece and PuzzleIt shows **where it goes on the box image**,
how to turn it, and how sure it is. Android, Kotlin, fully offline.

| Puzzle table | Result | Second lead | Blurry photo |
|---|---|---|---|
| ![](docs/screenshots/1_table.png) | ![](docs/screenshots/3_result.png) | ![](docs/screenshots/4_second_lead.png) | ![](docs/screenshots/5_blurry.png) |

*(captures from the instrumented tests, on synthetic box art)*

## How it works

```mermaid
flowchart LR
  A[Box photo] --> B[Rectified image + grid]
  P[Piece photo] --> C[Centre 70 %] --> D[Segment piece] --> E[17-zone Lab descriptor]
  B --> F[Candidates every half cell]
  E --> G{8 rotation shifts}
  F --> G --> H[Best + 3 leads] --> I[Confidence]
  I --> J[Cell / zone / rough area]
```

Colour is compared in 17 zones (centre + 2 rings x 8 sectors) of a disc scaled to the piece size, so scale and
lighting matter little; rotating the piece is a cyclic shift of the sectors. Code: `feature/recognition/PieceMatcher.kt`.

## Build and test

```bash
./gradlew assembleDebug testDebugUnitTest      # JDK 21
./gradlew connectedDebugAndroidTest            # phone or emulator
```

## Architecture
Single activity, MVVM, Hilt, Room, CameraX, OpenCV (box processing). Details and conventions: [`.claude/CLAUDE.md`](.claude/CLAUDE.md).
