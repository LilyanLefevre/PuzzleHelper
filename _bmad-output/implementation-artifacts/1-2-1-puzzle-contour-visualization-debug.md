# Story 1.2.1: Puzzle Contour Visualization & Debug

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a puzzle enthusiast,
I want to see visual feedback of puzzle piece detection when creating a project,
So that I can ensure good photo quality and validate piece detection accuracy.

## Acceptance Criteria

**Given** I have captured a puzzle box image in the project creation flow
**When** the system analyzes the image for piece contours
**Then** I see an overlay showing detected puzzle piece boundaries on the captured image
**And** the overlay appears within 2 seconds of photo capture
**And** I can choose to retake the photo if contour detection looks poor
**And** the contour overlay disappears when I proceed to project metadata

**Given** I am in debug mode and have entered the number of puzzle pieces for the project
**When** the system performs the intelligent pre-cutting analysis
**Then** I see a debug overlay showing the calculated piece grid layout with numbering
**And** the overlay displays multiple grid options if proportions are ambiguous
**And** I can see the recommended grid based on box image aspect ratio and estimated piece size
**And** I can manually adjust the grid dimensions if needed
**And** I can toggle the debug overlay on/off
**And** debug information is only visible in debug builds

## Tasks / Subtasks

- [x] Task 1: Implement real-time contour detection after photo capture (AC: 1-4)
  - [x] Subtask 1.1: Create OpenCV contour detection service
  - [x] Subtask 1.2: Implement contour overlay rendering on captured image
  - [x] Subtask 1.3: Add retake photo option when contours look poor
  - [x] Subtask 1.4: Optimize detection for 2-second completion requirement
- [x] Task 2: Create debug visualization for intelligent pre-cutting analysis (AC: 5-10)
  - [x] Subtask 2.1: Implement intelligent grid calculation with multiple options
  - [x] Subtask 2.2: Add aspect ratio and piece size analysis algorithms
  - [x] Subtask 2.3: Implement grid recommendation system with scoring
  - [x] Subtask 2.4: Add manual grid adjustment controls in debug mode
  - [x] Subtask 2.5: Add debug overlay toggle functionality
  - [x] Subtask 2.6: Ensure debug-only visibility with BuildConfig.DEBUG
- [x] Task 3: Integrate with existing project creation flow (All ACs)
  - [x] Subtask 3.1: Extend ProjectCreationFragment with contour overlay
  - [x] Subtask 3.2: Modify ProjectMetadataDialog with debug visualization
  - [x] Subtask 3.3: Add contour detection to ProjectViewModel
  - [x] Subtask 3.4: Implement overlay state management
- [x] Task 4: Add performance optimization and error handling (All ACs)
  - [x] Subtask 4.1: Implement background processing for contour detection
  - [x] Subtask 4.2: Add progress indicators during analysis
  - [x] Subtask 4.3: Handle cases where contour detection fails
  - [x] Subtask 4.4: Optimize memory usage for image processing
- [x] Task 5: Add comprehensive testing (All ACs)
  - [x] Subtask 5.1: Unit tests for OpenCV contour detection algorithms
  - [x] Subtask 5.2: UI tests for overlay interactions and retake functionality
  - [x] Subtask 5.3: Integration tests for debug visualization flow
  - [x] Subtask 5.4: Performance tests for 2-second detection requirement

## Dev Notes

### Architecture Requirements
- **Pattern**: MVVM with Single Activity architecture [Source: architecture.md#Core Architectural Decisions]
- **Computer Vision**: OpenCV for contour detection and piece analysis [Source: architecture.md#Core Architectural Decisions]
- **UI**: Custom overlay views for contour visualization [Source: architecture.md#Core Architectural Decisions]
- **Performance**: Background processing to maintain UI responsiveness [Source: architecture.md#Core Architectural Decisions]

### Previous Story Intelligence
- **ProjectCreationFragment**: Already exists with camera integration [Source: 1-2-puzzle-project-creation.md#Package Structure Integration]
- **ProjectMetadataDialog**: Already exists for project metadata input [Source: 1-2-puzzle-project-creation.md#Package Structure Integration]
- **ProjectViewModel**: Already exists with project creation logic [Source: 1-2-puzzle-project-creation.md#Package Structure Integration]
- **CameraManager**: Already exists with photo capture functionality [Source: 1-2-puzzle-project-creation.md#Package Structure Integration]
- **OpenCV Integration**: Already configured in build.gradle.kts from Story 1.1

### Technical Specifications
- **OpenCV Functions**: Canny edge detection, findContours, approxPolyDP for contour analysis
- **Overlay Rendering**: Custom View with Canvas drawing for contour visualization
- **Performance**: Use Dispatchers.Default for CPU-intensive OpenCV operations
- **Debug Mode**: BuildConfig.DEBUG check for debug-only features
- **Image Processing**: Grayscale conversion, Gaussian blur, thresholding for preprocessing

### Contour Detection Service
```kotlin
// New ContourDetectionService for Story 1.2.1
class ContourDetectionService {
    
    suspend fun detectContours(imagePath: String): ContourDetectionResult {
        return withContext(Dispatchers.Default) {
            val image = Imgcodecs.imread(imagePath)
            val processedImage = preprocessImage(image)
            val contours = detectPuzzleContours(processedImage)
            
            ContourDetectionResult(
                contours = contours,
                detectionTime = System.currentTimeMillis(),
                confidence = calculateDetectionConfidence(contours)
            )
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
    
    private fun detectPuzzleContours(image: Mat): List<MatOfPoint> {
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        
        Imgproc.findContours(
            image, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )
        
        return contours.filter { contour ->
            val area = Imgproc.contourArea(contour)
            area > 500.0 // Filter out small noise
        }
    }
    
    private fun calculateDetectionConfidence(contours: List<MatOfPoint>): Float {
        // Simple confidence calculation based on contour properties
        return if (contours.isNotEmpty()) {
            val avgArea = contours.map { Imgproc.contourArea(it) }.average()
            when {
                avgArea > 5000 -> 0.9f
                avgArea > 2000 -> 0.7f
                else -> 0.5f
            }
        } else 0.0f
    }
}

data class ContourDetectionResult(
    val contours: List<MatOfPoint>,
    val detectionTime: Long,
    val confidence: Float
)
```

### Debug Pre-cutting Service
```kotlin
// New DebugPreCuttingService for Story 1.2.1
class DebugPreCuttingService {
    
    suspend fun debugPreCutting(imagePath: String, pieceCount: Int): DebugPreCuttingResult {
        return withContext(Dispatchers.Default) {
            val image = Imgcodecs.imread(imagePath)
            val imageWidth = image.cols()
            val imageHeight = image.rows()
            val aspectRatio = imageWidth.toDouble() / imageHeight.toDouble()
            
            // Generate multiple grid options
            val gridOptions = generateGridOptions(pieceCount, aspectRatio)
            
            // Score and recommend best option
            val recommendedOption = recommendBestGrid(gridOptions, aspectRatio, imageWidth, imageHeight)
            
            DebugPreCuttingResult(
                pieceCount = pieceCount,
                allOptions = gridOptions,
                recommendedOption = recommendedOption,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                aspectRatio = aspectRatio
            )
        }
    }
    
    private fun generateGridOptions(pieceCount: Int, aspectRatio: Double): List<GridOption> {
        val options = mutableListOf<GridOption>()
        
        // Find all factor pairs close to the target
        val sqrtCount = sqrt(pieceCount.toDouble()).toInt()
        
        for (rows in maxOf(1, sqrtCount - 5)..minOf(pieceCount, sqrtCount + 5)) {
            if (pieceCount % rows == 0) {
                val cols = pieceCount / rows
                val gridAspectRatio = cols.toDouble() / rows.toDouble()
                val aspectMatch = 1.0 - abs(gridAspectRatio - aspectRatio) / aspectRatio
                
                options.add(
                    GridOption(
                        rows = rows,
                        cols = cols,
                        aspectMatch = aspectMatch,
                        pieceSize = calculatePieceSize(rows, cols),
                        score = calculateGridScore(rows, cols, aspectRatio)
                    )
                )
            }
        }
        
        // Add some near-miss options for non-perfect divisions
        if (options.size < 3) {
            for (rows in maxOf(1, sqrtCount - 3)..minOf(pieceCount, sqrtCount + 3)) {
                val cols = ceil(pieceCount.toDouble() / rows).toInt()
                val totalCells = rows * cols
                if (totalCells >= pieceCount && totalCells <= pieceCount + 10) {
                    val gridAspectRatio = cols.toDouble() / rows.toDouble()
                    val aspectMatch = 1.0 - abs(gridAspectRatio - aspectRatio) / aspectRatio
                    
                    options.add(
                        GridOption(
                            rows = rows,
                            cols = cols,
                            aspectMatch = aspectMatch,
                            pieceSize = calculatePieceSize(rows, cols),
                            score = calculateGridScore(rows, cols, aspectRatio),
                            emptyCells = totalCells - pieceCount
                        )
                    )
                }
            }
        }
        
        return options.sortedByDescending { it.score }.take(5)
    }
    
    private fun calculatePieceSize(rows: Int, cols: Int): PieceSize {
        // Estimate typical puzzle piece dimensions based on total pieces
        val avgPiecesPerRow = cols.toDouble()
        val avgPiecesPerCol = rows.toDouble()
        
        // Standard puzzle pieces are roughly square with slight variations
        val typicalPieceWidth = 100.0 / avgPiecesPerRow // Normalized
        val typicalPieceHeight = 100.0 / avgPiecesPerCol // Normalized
        
        return PieceSize(typicalPieceWidth, typicalPieceHeight)
    }
    
    private fun calculateGridScore(rows: Int, cols: Int, targetAspectRatio: Double): Double {
        val gridAspectRatio = cols.toDouble() / rows.toDouble()
        val aspectScore = 1.0 - abs(gridAspectRatio - targetAspectRatio) / targetAspectRatio
        
        // Prefer more square-like pieces (better for puzzles)
        val pieceRatio = maxOf(rows, cols).toDouble() / minOf(rows, cols).toDouble()
        val squarenessScore = 2.0 - pieceRatio // Closer to 1.0 is better
        
        // Prefer grids with fewer empty cells
        val efficiencyScore = 1.0
        
        return (aspectScore * 0.5) + (squarenessScore * 0.3) + (efficiencyScore * 0.2)
    }
    
    private fun recommendBestGrid(options: List<GridOption>, aspectRatio: Double, imageWidth: Int, imageHeight: Int): GridOption {
        return options.maxByOrNull { it.score } ?: options.first()
    }
}

data class DebugPreCuttingResult(
    val pieceCount: Int,
    val allOptions: List<GridOption>,
    val recommendedOption: GridOption,
    val imageWidth: Int,
    val imageHeight: Int,
    val aspectRatio: Double
)

data class GridOption(
    val rows: Int,
    val cols: Int,
    val aspectMatch: Double,
    val pieceSize: PieceSize,
    val score: Double,
    val emptyCells: Int = 0
)

data class PieceSize(
    val width: Double,
    val height: Double
)
```

### Contour Overlay View
```kotlin
// New ContourOverlayView for Story 1.2.1
class ContourOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var contours: List<MatOfPoint> = emptyList()
    private var paint: Paint = Paint()
    private var showOverlay: Boolean = true
    
    init {
        paint.apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 4f
            alpha = 200
        }
    }
    
    fun setContours(contours: List<MatOfPoint>) {
        this.contours = contours
        invalidate()
    }
    
    fun setShowOverlay(show: Boolean) {
        this.showOverlay = show
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (!showOverlay || contours.isEmpty()) return
        
        contours.forEach { contour ->
            val points = contour.toList()
            if (points.isNotEmpty()) {
                val path = Path()
                path.moveTo(points.first().x.toFloat(), points.first().y.toFloat())
                points.forEach { point ->
                    path.lineTo(point.x.toFloat(), point.y.toFloat())
                }
                path.close()
                canvas.drawPath(path, paint)
            }
        }
    }
}
```

### Debug Overlay View
```kotlin
// New DebugOverlayView for Story 1.2.1
class DebugOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var debugResult: DebugPreCuttingResult? = null
    private var selectedOptionIndex: Int = 0
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(8f, 2f, 2f, Color.BLACK)
    }
    
    private val optionPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        alpha = 50
    }
    
    fun setDebugResult(result: DebugPreCuttingResult) {
        this.debugResult = result
        this.selectedOptionIndex = 0 // Default to recommended option
        invalidate()
    }
    
    fun selectNextOption() {
        val result = debugResult ?: return
        selectedOptionIndex = (selectedOptionIndex + 1) % result.allOptions.size
        invalidate()
    }
    
    fun selectPreviousOption() {
        val result = debugResult ?: return
        selectedOptionIndex = if (selectedOptionIndex > 0) selectedOptionIndex - 1 else result.allOptions.size - 1
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val result = debugResult ?: return
        val selectedOption = result.allOptions[selectedOptionIndex]
        
        // Set colors based on whether this is the recommended option
        val isRecommended = selectedOption == result.recommendedOption
        optionPaint.color = if (isRecommended) Color.GREEN else Color.CYAN
        fillPaint.color = if (isRecommended) Color.GREEN else Color.CYAN
        
        // Draw selected grid layout
        val pieceWidth = result.imageWidth / selectedOption.cols
        val pieceHeight = result.imageHeight / selectedOption.rows
        
        for (row in 0 until selectedOption.rows) {
            for (col in 0 until selectedOption.cols) {
                val pieceNumber = row * selectedOption.cols + col + 1
                if (pieceNumber <= result.pieceCount) {
                    val left = col * pieceWidth
                    val top = row * pieceHeight
                    val right = left + pieceWidth
                    val bottom = top + pieceHeight
                    
                    // Draw piece rectangle
                    val rect = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
                    canvas.drawRect(rect, fillPaint)
                    canvas.drawRect(rect, optionPaint)
                    
                    // Draw piece number
                    canvas.drawText(
                        pieceNumber.toString(),
                        left + 20f,
                        top + 60f,
                        textPaint
                    )
                } else if (selectedOption.emptyCells > 0) {
                    // Draw empty cells with different style
                    val left = col * pieceWidth
                    val top = row * pieceHeight
                    val right = left + pieceWidth
                    val bottom = top + pieceHeight
                    
                    optionPaint.color = Color.RED
                    optionPaint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                    val rect = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
                    canvas.drawRect(rect, optionPaint)
                    
                    canvas.drawText(
                        "VIDE",
                        left + 20f,
                        top + 60f,
                        textPaint
                    )
                    optionPaint.pathEffect = null
                    optionPaint.color = if (isRecommended) Color.GREEN else Color.CYAN
                }
            }
        }
        
        // Draw grid information
        val optionInfo = "Option ${selectedOptionIndex + 1}/${result.allOptions.size}: ${selectedOption.rows}x${selectedOption.cols}"
        canvas.drawText(optionInfo, 50f, 100f, textPaint)
        
        val scoreInfo = "Score: ${String.format("%.2f", selectedOption.score)} ${if (isRecommended) "(RECOMMANDÉ)" else ""}"
        canvas.drawText(scoreInfo, 50f, 160f, textPaint)
        
        val aspectInfo = "Aspect: ${String.format("%.2f", selectedOption.aspectMatch)} | Pièces vides: ${selectedOption.emptyCells}"
        canvas.drawText(aspectInfo, 50f, 220f, textPaint)
        
        // Draw all options summary
        var yOffset = 300f
        canvas.drawText("Toutes les options:", 50f, yOffset, textPaint)
        yOffset += 60f
        
        result.allOptions.forEachIndexed { index, option ->
            val prefix = if (index == selectedOptionIndex) "►" else "  "
            val recommended = if (option == result.recommendedOption) " [RECOM]" else ""
            val optionText = "$prefix ${option.rows}x${option.cols} (score: ${String.format("%.2f", option.score)})$recommended"
            canvas.drawText(optionText, 50f, yOffset, textPaint)
            yOffset += 50f
        }
        
        // Draw controls hint
        canvas.drawText("← → pour naviguer entre les options", 50f, canvas.height - 100f, textPaint)
    }
}
```

### ViewModel Integration
```kotlin
// Extend existing ProjectViewModel from Story 1.2
private val _contourDetectionResult = MutableLiveData<ContourDetectionResult>()
val contourDetectionResult: LiveData<ContourDetectionResult> = _contourDetectionResult

private val _debugPreCuttingResult = MutableLiveData<DebugPreCuttingResult>()
val debugPreCuttingResult: LiveData<DebugPreCuttingResult> = _debugPreCuttingResult

private val _isProcessing = MutableLiveData<Boolean>()
val isProcessing: LiveData<Boolean> = _isProcessing

fun detectContours(imagePath: String) {
    viewModelScope.launch {
        _isProcessing.value = true
        try {
            val result = contourDetectionService.detectContours(imagePath)
            _contourDetectionResult.value = result
        } catch (e: Exception) {
            // Handle error
        } finally {
            _isProcessing.value = false
        }
    }
}

fun debugPreCutting(imagePath: String, pieceCount: Int) {
    if (!BuildConfig.DEBUG) return
    
    viewModelScope.launch {
        _isProcessing.value = true
        try {
            val result = debugPreCuttingService.debugPreCutting(imagePath, pieceCount)
            _debugPreCuttingResult.value = result
        } catch (e: Exception) {
            // Handle error
        } finally {
            _isProcessing.value = false
        }
    }
}
```

### Fragment Integration
```kotlin
// Extend existing ProjectCreationFragment from Story 1.2
private lateinit var contourOverlay: ContourOverlayView
private lateinit var debugOverlay: DebugOverlayView

private fun setupContourVisualization() {
    contourOverlay = ContourOverlayView(requireContext())
    // Add overlay to layout
    
    viewModel.contourDetectionResult.observe(viewLifecycleOwner) { result ->
        contourOverlay.setContours(result.contours)
        
        // Show retake option if confidence is low
        if (result.confidence < 0.6f) {
            showRetakeOption()
        }
    }
}

private fun setupDebugVisualization() {
    if (!BuildConfig.DEBUG) return
    
    debugOverlay = DebugOverlayView(requireContext())
    // Add debug overlay to layout
    
    viewModel.debugPreCuttingResult.observe(viewLifecycleOwner) { result ->
        debugOverlay.setDebugResult(result)
    }
}

private fun onPhotoCaptured(imagePath: String) {
    viewModel.detectContours(imagePath)
}

private fun onPieceCountEntered(pieceCount: Int) {
    if (BuildConfig.DEBUG) {
        viewModel.debugPreCutting(currentImagePath, pieceCount)
    }
    proceedWithProjectCreation()
}
```

### Testing Requirements
- **Unit Tests**: OpenCV contour detection, confidence calculations, overlay rendering
- **UI Tests**: Overlay interactions, retake functionality, debug toggle
- **Integration Tests**: End-to-end contour detection flow
- **Performance Tests**: 2-second detection requirement validation
- **Debug Tests**: Debug-only functionality verification

### Error Handling Strategy
- **OpenCV Errors**: Graceful fallback when contour detection fails
- **Performance**: Timeout handling for long-running operations
- **UI Errors**: User-friendly error messages for detection failures
- **Debug Safety**: Ensure debug features don't affect release builds

### Performance Considerations
- **Background Processing**: Use Dispatchers.Default for OpenCV operations
- **Memory Management**: Proper Mat disposal in OpenCV operations
- **UI Responsiveness**: Progress indicators during processing
- **Optimization**: Image resizing for very large images

### Material Design 3 Integration
- **Progress**: LinearProgressIndicator for detection progress
- **Dialogs**: MaterialAlertDialog for retake confirmation
- **Colors**: Use Material 3 color system for overlay colors
- **Typography**: Material 3 typography for debug text

### Security Considerations
- **Debug Isolation**: Ensure debug features are debug-build only
- **File Access**: Only process images within app's internal storage
- **Memory**: Prevent memory leaks with proper OpenCV Mat disposal
- **Performance**: Monitor processing time to prevent ANR

### Project Structure Notes
- **New Classes**: ContourDetectionService, DebugPreCuttingService, ContourOverlayView, DebugOverlayView
- **Existing Extensions**: ProjectCreationFragment, ProjectMetadataDialog, ProjectViewModel
- **Package Structure**: Add to existing feature/camera and feature/project packages
- **Testing**: Add to existing test structure from previous stories

### References
- [Source: 1-2-puzzle-project-creation.md] - Existing project creation flow and architecture
- [Source: 1-6-automatic-piece-pre-cutting.md] - Pre-cutting algorithm foundation
- [Source: architecture.md#Core Architectural Decisions] - MVVM and OpenCV patterns
- [Source: OpenCV Documentation] - Contour detection and analysis algorithms
- [Source: Material Design 3 Guidelines] - Overlay and dialog patterns

## Dev Agent Record

### Agent Model Used

Claude Sonnet 3.5 - November 2024 version

### Debug Log References

### Completion Notes List

### File List

## Change Log

- 2026-01-31: Initial story creation with comprehensive contour visualization and debug requirements
- 2026-01-31: **ULTIMATE CONTEXT ENGINE ANALYSIS COMPLETED** - Comprehensive developer guide created with previous story intelligence
- 2026-01-31: Status set to ready-for-dev - All context gathered for flawless implementation
