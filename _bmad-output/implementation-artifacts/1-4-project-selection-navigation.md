---
story_id: 1-4
story_title: Project Selection & Navigation
epic: 1
status: ready-for-dev
created: 2026-01-31
---

# Story 1.4: Project Selection & Navigation

## User Story

As a puzzle enthusiast,
I want to select an existing puzzle project to work on,
So that I can continue solving my puzzle.

## Acceptance Criteria

**Given** I am viewing the project list
**When** I tap on a specific project
**Then** I navigate to the main puzzle working screen
**And** the puzzle box reference image is displayed
**And** the project's current progress is shown if available
**And** I can access the camera for piece capture
**And** navigation back returns to the project list

## Technical Requirements

### Components
- **PuzzleWorkingFragment**: Main puzzle working screen
- **PuzzleWorkingViewModel**: ViewModel for puzzle working state
- **PuzzleWorkingLayout**: Layout with reference image and controls
- **Navigation**: Proper navigation setup between fragments

### UI Requirements
- Large display area for puzzle box reference image
- Camera capture button (FAB or prominent button)
- Progress indicator (if progress tracking exists)
- Back navigation to project list
- Material Design 3 components

### Navigation Flow
1. ProjectListFragment → PuzzleWorkingFragment
2. PuzzleWorkingFragment → (camera capture flow)
3. PuzzleWorkingFragment → ProjectListFragment (back)

### Data Requirements
- Pass selected project ID to working screen
- Load project data (image, metadata, progress)
- Handle project state persistence

## Implementation Notes

### Dependencies
- Navigation Component for fragment navigation
- Glide/Coil for reference image loading
- Hilt for dependency injection
- Room for project data access
- CameraX for piece capture (reused from 1-2)

### File Structure
```
app/src/main/java/com/lilyan_lefevre/puzzleit/
├── feature/puzzle/
│   ├── PuzzleWorkingFragment.kt
│   ├── PuzzleWorkingViewModel.kt
│   └── PuzzleWorkingRepository.kt
├── feature/project/
│   └── ProjectListFragment.kt (update navigation)
└── navigation/
    └── PuzzleNavigation.kt
```

### Layout Files
- `fragment_puzzle_working.xml`: Main working screen layout
- Update `nav_graph.xml`: Add navigation paths

### Navigation Setup
```xml
<!-- Navigation graph updates -->
<fragment
    android:id="@+id/projectListFragment"
    android:name="com.lilyan_lefevre.puzzleit.feature.project.ProjectListFragment">
    <action
        android:id="@+id/action_projectListFragment_to_puzzleWorkingFragment"
        app:destination="@id/puzzleWorkingFragment" />
</fragment>

<fragment
    android:id="@+id/puzzleWorkingFragment"
    android:name="com.lilyan_lefevre.puzzleit.feature.puzzle.PuzzleWorkingFragment">
    <argument
        android:name="projectId"
        app:argType="string" />
</fragment>
```

## Test Requirements

### Unit Tests
- PuzzleWorkingViewModel data loading tests
- Navigation argument passing tests
- Repository project access tests

### UI Tests
- Navigation flow from list to working screen
- Reference image display verification
- Back navigation functionality

### Integration Tests
- End-to-end project selection flow
- Data persistence across navigation

## Definition of Done

- [ ] Tapping project navigates to working screen
- [ ] Reference image displays prominently
- [ ] Camera capture button available
- [ ] Back navigation returns to project list
- [ ] Project data loads correctly
- [ ] Navigation arguments passed properly
- [ ] All acceptance criteria met
- [ ] Unit tests written and passing
- [ ] UI tests written and passing
- [ ] Code review completed
- [ ] Documentation updated

## Dependencies from Previous Stories

- Reuses ProjectRepository from 1-2
- Reuses CameraManager from 1-2
- Reuses navigation setup from 1-3
- Builds on existing database schema
