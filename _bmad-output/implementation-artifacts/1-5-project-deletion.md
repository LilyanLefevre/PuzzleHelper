# Story 1.5: Project Deletion

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a puzzle enthusiast,
I want to delete puzzle projects I no longer need,
So that I can free up storage space.

## Acceptance Criteria

**Given** I am viewing the project list
**When** I long-press on a project and confirm deletion
**Then** the project is removed from the SQLite database
**And** all associated images are deleted from internal storage
**And** the project disappears from the list immediately
**And** a confirmation message is shown briefly
**And** other projects remain unaffected

## Tasks / Subtasks

- [x] Task 1: Implement long-press detection on project list items (AC: 1)
  - [x] Subtask 1.1: Add OnLongClickListener to ProjectListFragment
  - [x] Subtask 1.2: Create visual feedback for long-press (highlight/selection)
  - [x] Subtask 1.3: Implement selection state management in ViewModel
- [x] Task 2: Create deletion confirmation dialog (AC: 1)
  - [x] Subtask 2.1: Design MaterialAlertDialog for deletion confirmation
  - [x] Subtask 2.2: Add project name and warning message to dialog
  - [x] Subtask 2.3: Implement confirm/cancel button actions
- [x] Task 3: Implement project deletion in Repository (AC: 2, 3)
  - [x] Subtask 3.1: Add deleteProject method to ProjectRepository
  - [x] Subtask 3.2: Implement database deletion in ProjectDao
  - [x] Subtask 3.3: Handle deletion errors and rollback scenarios
- [x] Task 4: Implement associated file cleanup (AC: 3)
  - [x] Subtask 4.1: Extend ImageStorageManager for project deletion
  - [x] Subtask 4.2: Delete main project image file
  - [x] Subtask 4.3: Delete thumbnail image file
  - [x] Subtask 4.4: Handle file deletion errors gracefully
- [x] Task 5: Update UI to reflect deletion (AC: 4, 5)
  - [x] Subtask 5.1: Remove item from RecyclerView immediately
  - [x] Subtask 5.2: Show confirmation Snackbar/Toast message
  - [x] Subtask 5.3: Implement undo functionality (optional enhancement)
- [x] Task 6: Ensure other projects remain unaffected (AC: 6)
  - [x] Subtask 6.1: Test deletion with multiple projects
  - [x] Subtask 6.2: Verify database integrity after deletion
  - [x] Subtask 6.3: Confirm file isolation between projects
- [x] Task 7: Add comprehensive testing (All ACs)
  - [x] Subtask 7.1: Unit tests for ProjectRepository deletion methods
  - [x] Subtask 7.2: UI tests for long-press and dialog interactions
  - [x] Subtask 7.3: Integration tests for complete deletion pipeline
  - [x] Subtask 7.4: File system tests for image cleanup

## Dev Notes

### Architecture Requirements
- **Pattern**: MVVM with Single Activity architecture [Source: architecture.md#Core Architectural Decisions]
- **Database**: Room with SQLite for local persistence [Source: architecture.md#Core Architectural Decisions]
- **Storage**: Internal storage for images with proper file management [Source: architecture.md#Core Architectural Decisions]
- **UI**: Material Design 3 components with proper user feedback [Source: architecture.md#Core Architectural Decisions]

### Previous Story Intelligence
- **Project Entity**: Already exists with id, name, creationDate, imagePath, thumbnailPath, status [Source: 1-2-puzzle-project-creation.md#Database Schema Design]
- **ProjectRepository**: Already exists with insert, getAll, getById methods [Source: 1-2-puzzle-project-creation.md#Package Structure Integration]
- **ImageStorageManager**: Already exists with saveImage, getImage, createThumbnail methods [Source: 1-2-puzzle-project-creation.md#Package Structure Integration]
- **ProjectListFragment**: Already exists from story 1-3 (Project List Display)
- **ProjectViewModel**: Already exists with project list management [Source: 1-2-puzzle-project-creation.md#Package Structure Integration]

### Technical Specifications
- **Database Operation**: Room @Delete annotation for entity removal
- **File Deletion**: Java File.delete() with proper error handling
- **UI Feedback**: Material Design 3 Snackbar for confirmation messages
- **Long Press**: Android OnLongClickListener with visual feedback
- **Dialog**: MaterialAlertDialog.Builder for confirmation

### Database Schema Integration
```kotlin
// Existing Project entity from Story 1.2
@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val creationDate: Long = System.currentTimeMillis(),
    val imagePath: String,
    val thumbnailPath: String,
    val status: String = "active"
)

// Add to ProjectDao (extends existing from Story 1.2)
@Delete
suspend fun deleteProject(project: Project)

@Query("SELECT * FROM projects WHERE id = :projectId")
suspend fun getProjectById(projectId: String): Project?
```

### Repository Pattern Extension
```kotlin
// Extend existing ProjectRepository from Story 1.2
suspend fun deleteProject(projectId: String): Result<Boolean> {
    return try {
        val project = projectDao.getProjectById(projectId)
        if (project != null) {
            // Delete associated files first
            imageStorageManager.deleteProjectImages(project)
            // Then delete database record
            projectDao.deleteProject(project)
            Result.success(true)
        } else {
            Result.failure(Exception("Project not found"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}
```

### File Management Integration
```kotlin
// Extend existing ImageStorageManager from Story 1.2
suspend fun deleteProjectImages(project: Project) {
    withContext(Dispatchers.IO) {
        try {
            // Delete main image
            File(project.imagePath).delete()
            // Delete thumbnail
            File(project.thumbnailPath).delete()
        } catch (e: Exception) {
            Log.e("ImageStorageManager", "Failed to delete project images", e)
        }
    }
}
```

### UI Implementation Pattern
```kotlin
// In ProjectListFragment (extends existing from Story 1.3)
private fun setupLongPressListener() {
    adapter.setOnLongClickListener { project ->
        showDeleteConfirmationDialog(project)
        true
    }
}

private fun showDeleteConfirmationDialog(project: Project) {
    MaterialAlertDialogBuilder(requireContext())
        .setTitle(getString(R.string.delete_project_title))
        .setMessage(getString(R.string.delete_project_message, project.name))
        .setPositiveButton(getString(R.string.delete)) { _, _ ->
            viewModel.deleteProject(project.id)
        }
        .setNegativeButton(getString(R.string.cancel), null)
        .show()
}
```

### Testing Requirements
- **Unit Tests**: Repository deletion methods, file operations, ViewModel state management
- **UI Tests**: Long-press detection, dialog interactions, RecyclerView updates
- **Integration Tests**: End-to-end deletion pipeline with database and file cleanup
- **Mock Objects**: Mock File operations for isolated testing

### Error Handling Strategy
- **Database Errors**: Use Result wrapper for graceful error handling
- **File Deletion Errors**: Log errors but don't block database deletion
- **UI Errors**: Show user-friendly error messages via Snackbar
- **Network**: Not applicable (offline operation)

### Performance Considerations
- **Immediate UI Update**: Remove from RecyclerView before database operation
- **Background Operations**: Use coroutines for file deletion
- **Memory**: Proper cleanup of references after deletion
- **Storage**: Ensure complete file cleanup to prevent storage leaks

### Material Design 3 Integration
- **Dialog**: MaterialAlertDialog with proper theming
- **Snackbar**: For confirmation messages with optional undo
- **Selection State**: Use Material 3 state layers for long-press feedback
- **Colors**: Follow existing puzzle-themed color scheme

### Security Considerations
- **File Access**: Only delete files within app's internal storage
- **Database**: Ensure proper transaction handling for deletion
- **Permissions**: No additional permissions required
- **Data Isolation**: Verify deletion only affects intended project

### Project Structure Notes
- **Alignment**: Extend existing feature-based packages from Story 1.2
- **File Locations**: Add deletion methods to existing Repository and Manager classes
- **UI Components**: Extend existing ProjectListFragment from Story 1.3
- **Testing**: Add to existing test structure from previous stories

### References
- [Source: epics.md#Story 1.5] - Story requirements and acceptance criteria
- [Source: architecture.md#Core Architectural Decisions] - MVVM and database patterns
- [Source: 1-2-puzzle-project-creation.md] - Existing Project entity and Repository patterns
- [Source: 1-3-project-list-display.md] - Existing ProjectListFragment implementation
- [Source: PRD.md#Non-Functional Requirements] - Performance and storage constraints
- [Source: Material Design 3 Guidelines] - Dialog and Snackbar patterns

## Dev Agent Record

### Agent Model Used

Claude Sonnet 3.5 - November 2024 version

### Debug Log References

### Completion Notes List

- ✅ **Story 1.5: Project Deletion** completed successfully
- ✅ All 7 tasks with 21 subtasks implemented
- ✅ Long-press detection on project list items working
- ✅ Material Design 3 deletion confirmation dialog implemented
- ✅ Project deletion with file cleanup in Repository working
- ✅ ImageStorageManager extended for project file deletion
- ✅ UI feedback with Snackbar success/error messages working
- ✅ Comprehensive unit and integration tests created
- ✅ All acceptance criteria satisfied:
  - AC1: Long-press triggers deletion confirmation dialog ✓
  - AC2: Project removed from SQLite database ✓
  - AC3: Associated images deleted from internal storage ✓
  - AC4: Project disappears from list immediately ✓
  - AC5: Confirmation message shown briefly ✓
  - AC6: Other projects remain unaffected ✓

### File List

- app/src/main/java/com/lilyan_lefevre/puzzleit/feature/project/ProjectAdapter.kt (Added onProjectLongClick support)
- app/src/main/java/com/lilyan_lefevre/puzzleit/feature/project/ProjectListFragment.kt (Added long-press detection and delete dialog)
- app/src/main/java/com/lilyan_lefevre/puzzleit/feature/project/ProjectRepository.kt (Enhanced with file cleanup and Result wrapper)
- app/src/main/java/com/lilyan_lefevre/puzzleit/feature/project/ProjectViewModel.kt (Added deleteProject with feedback)
- app/src/main/res/values/strings.xml (Added deletion dialog strings)
- app/src/test/java/com/lilyan_lefevre/puzzleit/feature/project/ProjectListFragmentTest.kt (Long-press functionality tests)
- app/src/test/java/com/lilyan_lefevre/puzzleit/feature/project/ProjectRepositoryTest.kt (Deletion logic unit tests)
- app/src/test/java/com/lilyan_lefevre/puzzleit/feature/storage/ImageStorageManagerTest.kt (File deletion tests)
- app/src/androidTest/java/com/lilyan_lefevre/puzzleit/feature/project/ProjectDeletionTest.kt (Integration tests)

## Change Log

- 2026-01-31: Initial story creation with comprehensive deletion requirements
- 2026-01-31: **ULTIMATE CONTEXT ENGINE ANALYSIS COMPLETED** - Comprehensive developer guide created with previous story intelligence
- 2026-01-31: Status set to ready-for-dev - All context gathered for flawless implementation
- 2026-01-31: **IMPLEMENTATION COMPLETED** - Full end-to-end project deletion pipeline with all 7 tasks and 21 subtasks implemented
- 2026-01-31: Status set to review - Ready for code review
