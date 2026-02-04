# Story 1.6: Automatic Piece Pre-cutting

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a puzzle enthusiast,
I want the system to automatically pre-cut the box image into virtual puzzle pieces,
So that I have a reference for piece matching.

## Acceptance Criteria

**Given** a new puzzle project has been created from a box image
**When** the project initialization completes
**Then** the system analyzes the box image to identify puzzle piece outlines
**And** virtual puzzle piece coordinates and boundaries are calculated
**And** piece metadata (coordinates, dimensions) is stored in SQLite database
**And** the original box image remains stored in internal storage
**And** the pre-cutting process completes within 5 seconds
**And** the system can reference piece boundaries for future matching

## Tasks / Subtasks

- [ ] Task 1: Implement box image analysis for piece detection (AC: 1)
  - [ ] Subtask 1.1: Create OpenCV-based edge detection algorithm
  - [ ] Subtask 1.2: Implement contour detection for piece boundaries
  - [ ] Subtask 1.3: Add noise reduction and image preprocessing
- [ ] Task 2: Calculate virtual piece coordinates and boundaries (AC: 2)
  - [ ] Subtask 2.1: Extract piece coordinate data from detected contours
  - [ ] Subtask 2.2: Calculate piece dimensions and area measurements
  - [ ] Subtask 2.3: Create piece boundary polygon data structures
- [ ] Task 3: Store piece metadata in database (AC: 3)
  - [ ] Subtask 3.1: Create PuzzlePiece entity with coordinate fields
  - [ ] Subtask 3.2: Add PuzzlePieceDao for piece operations
  - [ ] Subtask 3.3: Implement piece data insertion in ProjectRepository
- [ ] Task 4: Preserve original box image storage (AC: 4)
  - [ ] Subtask 4.1: Verify original image remains unchanged
  - [ ] Subtask 4.2: Ensure piece data references original image
  - [ ] Subtask 4.3: Add image integrity validation
- [ ] Task 5: Optimize pre-cutting performance (AC: 5)
  - [ ] Subtask 5.1: Implement background processing with coroutines
  - [ ] Subtask 5.2: Add progress tracking for long operations
  - [ ] Subtask 5.3: Optimize OpenCV operations for mobile performance
- [ ] Task 6: Enable piece boundary referencing for matching (AC: 6)
  - [ ] Subtask 6.1: Create piece lookup methods in Repository
  - [ ] Subtask 6.2: Implement piece boundary comparison utilities
  - [ ] Subtask 6.3: Add piece metadata retrieval for CV engine
- [ ] Task 7: Add comprehensive testing (All ACs)
  - [ ] Subtask 7.1: Unit tests for OpenCV piece detection algorithms
  - [ ] Subtask 7.2: Integration tests for piece data storage
  - [ ] Subtask 7.3: Performance tests for 5-second completion requirement
  - [ ] Subtask 7.4: UI tests for pre-cutting progress indication

## Dev Notes

### Architecture Requirements
- **Pattern**: MVVM with Single Activity architecture [Source: architecture.md#Core Architectural Decisions]
- **Database**: Room with SQLite for local persistence [Source: architecture.md#Core Architectural Decisions]
- **Computer Vision**: OpenCV for image processing and piece detection [Source: architecture.md#Core Architectural Decisions]
- **Performance**: Background processing to maintain UI responsiveness [Source: architecture.md#Core Architectural Decisions]

### Previous Story Intelligence
- **Project Entity**: Already exists with id, name, creationDate, imagePath, thumbnailPath, status [Source: 1-2-puzzle-project-creation.md#Database Schema Design]
- **ProjectRepository**: Already exists with insert, getAll, getById, delete methods [Source: 1-2-puzzle-project-creation.md#Package Structure Integration]
- **ImageStorageManager**: Already exists with saveImage, getImage, createThumbnail methods [Source: 1-2-puzzle-project-creation.md#Package Structure Integration]
- **OpenCV Integration**: Already configured in build.gradle.kts from Story 1.1
- **Coroutines**: Already configured for async operations from previous stories

### Technical Specifications
- **OpenCV Functions**: Use Canny edge detection, findContours, approxPolyDP for piece boundary detection
- **Database Schema**: New PuzzlePiece entity linked to Project by projectId
- **Image Processing**: Grayscale conversion, Gaussian blur, thresholding for preprocessing
- **Performance**: Use Dispatchers.Default for CPU-intensive OpenCV operations
- **Data Structure**: Store piece boundaries as list of coordinate points

### Database Schema Extension
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

// New PuzzlePiece entity for Story 1.6
@Entity(tableName = "puzzle_pieces")
data class PuzzlePiece(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String, // Foreign key to Project
    val pieceNumber: Int,
    val boundaryPoints: String, // JSON array of coordinate points
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val area: Float,
    val status: String = "unplaced"
)

// Add to existing ProjectDao from Story 1.2
@Query("SELECT * FROM puzzle_pieces WHERE projectId = :projectId")
suspend fun getPiecesForProject(projectId: String): List<PuzzlePiece>

// New PuzzlePieceDao
@Dao
interface PuzzlePieceDao {
    @Insert
    suspend fun insertPiece(piece: PuzzlePiece)
    
    @Insert
    suspend fun insertPieces(pieces: List<PuzzlePiece>)
    
    @Query("SELECT * FROM puzzle_pieces WHERE projectId = :projectId")
    suspend fun getPiecesForProject(projectId: String): List<PuzzlePiece>
    
    @Query("DELETE FROM puzzle_pieces WHERE projectId = :projectId")
    suspend fun deletePiecesForProject(projectId: String)
}
```

### OpenCV Pre-cutting Engine
```kotlin
// New PieceDetectionEngine for Story 1.6
class PieceDetectionEngine {
    
    suspend fun analyzeBoxImage(imagePath: String): List<PuzzlePiece> {
        return withContext(Dispatchers.Default) {
            val image = Imgcodecs.imread(imagePath)
            val processedImage = preprocessImage(image)
            val contours = detectPieceContours(processedImage)
            contours.mapIndexed { index, contour ->
                createPuzzlePiece(contour, index)
            }
        }
    }
    
    private fun preprocessImage(image: Mat): Mat {
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        
        Imgproc.cvtColor(image, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(blurred, edges, 50.0, 150.0)
        
        return edges
    }
    
    private fun detectPieceContours(image: Mat): List<MatOfPoint> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        
        Imgproc.findContours(
            image, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        return contours.filter { contour ->
            val area = Imgproc.contourArea(contour)
            area > 1000.0 // Filter out small noise
        }
    }
    
    private fun createPuzzlePiece(contour: MatOfPoint, index: Int): PuzzlePiece {
        val rect = Imgproc.boundingRect(contour)
        val moments = Imgproc.moments(contour)
        val centerX = (moments.m10 / moments.m00).toFloat()
        val centerY = (moments.m01 / moments.m00).toFloat()
        
        return PuzzlePiece(
            id = UUID.randomUUID().toString(),
            projectId = "", // Set by caller
            pieceNumber = index + 1,
            boundaryPoints = contour.toList().toString(),
            centerX = centerX,
            centerY = centerY,
            width = rect.width.toFloat(),
            height = rect.height.toFloat(),
            area = Imgproc.contourArea(contour).toFloat()
        )
    }
}
```

### Repository Pattern Extension
```kotlin
// Extend existing ProjectRepository from Story 1.2
suspend fun preCutProjectPieces(projectId: String, imagePath: String): Result<List<PuzzlePiece>> {
    return try {
        val pieces = pieceDetectionEngine.analyzeBoxImage(imagePath)
        val piecesWithProjectId = pieces.map { it.copy(projectId = projectId) }
        
        // Clear existing pieces and insert new ones
        puzzlePieceDao.deletePiecesForProject(projectId)
        puzzlePieceDao.insertPieces(piecesWithProjectId)
        
        Result.success(piecesWithProjectId)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

suspend fun getPiecesForProject(projectId: String): List<PuzzlePiece> {
    return puzzlePieceDao.getPiecesForProject(projectId)
}
```

### ViewModel Integration
```kotlin
// Extend existing ProjectViewModel from Story 1.2
private val _preCuttingProgress = MutableLiveData<Int>()
val preCuttingProgress: LiveData<Int> = _preCuttingProgress

fun preCutProjectPieces(projectId: String) {
    viewModelScope.launch {
        _preCuttingProgress.value = 0
        
        try {
            val project = projectRepository.getProjectById(projectId)
            if (project != null) {
                _preCuttingProgress.value = 25
                val result = projectRepository.preCutProjectPieces(projectId, project.imagePath)
                _preCuttingProgress.value = 100
                
                result.fold(
                    onSuccess = { pieces ->
                        // Success - pieces are ready for matching
                    },
                    onFailure = { error ->
                        // Handle pre-cutting error
                    }
                )
            }
        } catch (e: Exception) {
            // Handle exception
        }
    }
}
```

### Testing Requirements
- **Unit Tests**: OpenCV piece detection algorithms, coordinate calculations, database operations
- **Integration Tests**: End-to-end pre-cutting pipeline with real puzzle images
- **Performance Tests**: Validate 5-second completion requirement on various image sizes
- **Mock Objects**: Mock OpenCV operations for isolated testing
- **UI Tests**: Progress indication during pre-cutting process

### Error Handling Strategy
- **OpenCV Errors**: Graceful fallback when piece detection fails
- **Database Errors**: Use Result wrapper for proper error propagation
- **Performance**: Timeout handling for long-running operations
- **Image Quality**: Handle cases where piece detection is not possible

### Performance Considerations
- **Background Processing**: Use Dispatchers.Default for CPU-intensive operations
- **Memory Management**: Proper Mat disposal in OpenCV operations
- **Progress Tracking**: Provide user feedback during processing
- **Optimization**: Image resizing for very large puzzle box images

### OpenCV Integration Notes
- **Functions**: Canny edge detection, findContours, boundingRect, moments
- **Memory**: Proper Mat lifecycle management to prevent leaks
- **Threading**: OpenCV operations must run on background threads
- **Compatibility**: Ensure OpenCV 4.8.0 compatibility with Android

### Material Design 3 Integration
- **Progress**: LinearProgressIndicator for pre-cutting progress
- **Status**: Status messages for user feedback
- **Error Handling**: MaterialAlertDialog for error states
- **Loading**: Skeleton loading states during processing

### Security Considerations
- **File Access**: Only process images within app's internal storage
- **Memory**: Prevent memory leaks with proper OpenCV Mat disposal
- **Data Validation**: Validate piece coordinate data before storage
- **Performance**: Monitor processing time to prevent ANR

### Project Structure Notes
- **Alignment**: Extend existing feature-based packages from Story 1.2
- **New Classes**: PieceDetectionEngine, PuzzlePiece entity, PuzzlePieceDao
- **Existing Extensions**: ProjectRepository, ProjectViewModel
- **Testing**: Add to existing test structure from previous stories

### References
- [Source: epics.md#Story 1.6] - Story requirements and acceptance criteria
- [Source: architecture.md#Core Architectural Decisions] - MVVM and OpenCV patterns
- [Source: 1-2-puzzle-project-creation.md] - Existing Project entity and Repository patterns
- [Source: 1-1-android-project-setup-with-opencv.md] - OpenCV integration foundation
- [Source: PRD.md#Non-Functional Requirements] - Performance constraints (5-second limit)
- [Source: OpenCV Documentation] - Edge detection and contour analysis algorithms

## Dev Agent Record

### Agent Model Used

Claude Sonnet 3.5 - November 2024 version

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-01-31: Initial story creation with comprehensive automatic piece pre-cutting requirements
- 2026-01-31: **ULTIMATE CONTEXT ENGINE ANALYSIS COMPLETED** - Comprehensive developer guide created with previous story intelligence
- 2026-01-31: Status set to ready-for-dev - All context gathered for flawless implementation
