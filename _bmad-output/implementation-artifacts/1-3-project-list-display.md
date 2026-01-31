---
story_id: 1-3
story_title: Project List Display
epic: 1
status: ready-for-dev
created: 2026-01-31
---

# Story 1.3: Project List Display

## User Story

As a puzzle enthusiast,
I want to view all my puzzle projects,
So that I can select which puzzle to work on.

## Acceptance Criteria

**Given** one or more puzzle projects exist in the database
**When** I open the application
**Then** I see a list of all my puzzle projects
**And** each project shows the box image thumbnail
**And** each project displays the creation date and name
**And** the list is scrollable if there are many projects
**And** tapping on a project navigates to project details

## Technical Requirements

### Components
- **ProjectListFragment**: Main fragment for displaying project list
- **ProjectAdapter**: RecyclerView adapter for project items
- **ProjectItemView**: Custom view for individual project display
- **ProjectViewModel**: ViewModel for managing project data

### Data Layer
- **ProjectRepository**: Repository for accessing project data
- **ProjectDao**: DAO for database operations
- **ProjectEntity**: Entity for database representation

### UI Requirements
- Material Design 3 Card layout for each project item
- Thumbnail image loading with Glide/Coil
- Lazy loading for large project lists
- Empty state handling when no projects exist
- Pull-to-refresh functionality

### Performance Requirements
- List should load within 2 seconds
- Images should be cached for smooth scrolling
- Memory usage optimized for large project collections

## Implementation Notes

### Dependencies
- RecyclerView for list display
- Glide/Coil for image loading
- Hilt for dependency injection
- Room for database operations
- Coroutines for async operations

### File Structure
```
app/src/main/java/com/lilyan_lefevre/puzzleit/
├── feature/project/
│   ├── ProjectListFragment.kt
│   ├── ProjectAdapter.kt
│   ├── ProjectItemView.kt
│   └── ProjectViewModel.kt
├── data/
│   ├── repository/
│   │   └── ProjectRepository.kt
│   ├── local/
│   │   ├── dao/
│   │   │   └── ProjectDao.kt
│   │   └── entity/
│   │       └── ProjectEntity.kt
└── di/
    └── ProjectModule.kt
```

### Layout Files
- `fragment_project_list.xml`: Main list layout
- `item_project.xml`: Individual project item layout
- `layout_empty_state.xml`: Empty state layout

## Test Requirements

### Unit Tests
- ProjectViewModel data loading tests
- ProjectAdapter item binding tests
- Repository data access tests

### UI Tests
- List display verification
- Item click interaction tests
- Scroll performance tests

### Integration Tests
- Database to UI data flow tests
- Image loading integration tests

## Definition of Done

- [ ] Project list displays all saved projects
- [ ] Each project shows thumbnail, name, and creation date
- [ ] List is scrollable with smooth performance
- [ ] Tapping project navigates to details (basic navigation)
- [ ] Empty state shows appropriate message
- [ ] Images load efficiently with caching
- [ ] Pull-to-refresh functionality works
- [ ] All acceptance criteria met
- [ ] Unit tests written and passing
- [ ] UI tests written and passing
- [ ] Code review completed
- [ ] Documentation updated
