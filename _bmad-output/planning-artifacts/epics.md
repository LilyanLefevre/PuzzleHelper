---
stepsCompleted: ['step-01-validate-prerequisites', 'step-02-design-epics', 'step-03-create-stories']
inputDocuments: ['/Users/lilyan/Documents/Projets/PuzzleHelper/_bmad-output/planning-artifacts/prd.md', '/Users/lilyan/Documents/Projets/PuzzleHelper/_bmad-output/planning-artifacts/architecture.md', '/Users/lilyan/Documents/Projets/PuzzleHelper/_bmad-output/planning-artifacts/ux-design-specification.md']
---

# PuzzleHelper - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for PuzzleHelper, decomposing the requirements from the PRD, UX Design if it exists, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: User can scan puzzle box image to create new puzzle project
FR2: User can view list of created puzzle projects
FR3: User can select existing puzzle project to work on
FR4: User can delete puzzle project from local storage
FR5: System can automatically pre-cut scanned box image into virtual puzzle pieces
FR6: User can capture photo of individual puzzle piece using device camera
FR7: System can analyze captured piece image using computer vision algorithms
FR8: System can compare piece image against virtual puzzle piece database
FR9: System can generate position suggestion with confidence score
FR10: System can provide precise position suggestion when confidence is high
FR11: System can provide general area suggestion when confidence is low
FR12: System can test piece image in multiple rotations for best match
FR13: User can access camera interface for piece capture
FR14: User can view puzzle box reference image while working
FR15: User can see position suggestion overlay on reference image
FR16: User can dismiss suggestion and continue manual search
FR17: User can view confidence score for each suggestion
FR18: User can navigate between different puzzle projects
FR19: User can access simple settings menu
FR20: System can store puzzle projects locally on device
FR21: System can save piece analysis results for future reference
FR22: System can maintain puzzle completion progress
FR23: User can export puzzle data for backup purposes
FR24: System can store application logs for debugging
FR25: System can request camera permission from user
FR26: System can access device storage for local data persistence
FR27: System can operate without internet connection
FR28: System can comply with Android storage and privacy requirements
FR29: System can detect blurry or low-quality piece photos
FR30: System can request new photo when image quality is insufficient
FR31: System can handle cases where piece cannot be matched
FR32: System can provide fallback suggestions when primary algorithms fail
FR33: System can display clear error messages for recognition failures
FR34: System can log errors for debugging and improvement

### NonFunctional Requirements

NFR1: Position calculation time: <5 seconds for analysis and position suggestion
NFR2: Photo capture time: <2 seconds for capture and processing
NFR3: Overall analysis time: <10 seconds total (photo + analysis + suggestion)
NFR4: App startup time: <3 seconds for application launch
NFR5: Recognition accuracy: >70% correct suggestions
NFR6: Error recovery success: >90% users can continue after error
NFR7: System stability: <5% crashes during normal usage
NFR8: Android native application (no cross-platform)
NFR9: 100% offline operation (no network dependencies)
NFR10: Application size <50MB
NFR11: Material Design 3 UI components
NFR12: Responsive design for different Android screen sizes

### Additional Requirements

- Starter Template: Android Studio Empty Activity with Kotlin + OpenCV integration
- Infrastructure: Android native development with local storage
- Integration: OpenCV Android library for computer vision processing
- Data migration: Local SQLite database + internal storage for images
- Monitoring: Application logging for debugging and algorithm improvement
- Security: Android storage and privacy compliance
- Performance optimization: Memory management for <50MB app size constraint
- UI Framework: Material Design 3 with XML layouts
- Architecture pattern: MVVM with singleton pattern for CV Engine
- Build system: Gradle with ProGuard/R8 optimization

### FR Coverage Map

FR1: Epic 1 - Project creation from box scan
FR2: Epic 1 - Project listing and management
FR3: Epic 1 - Project selection and access
FR4: Epic 1 - Project deletion and cleanup
FR5: Epic 1 - Automatic puzzle piece pre-cutting
FR6: Epic 2 - Camera capture for puzzle pieces
FR7: Epic 2 - Computer vision analysis algorithms
FR8: Epic 2 - Piece comparison against virtual database
FR9: Epic 2 - Position suggestion generation
FR10: Epic 2 - High confidence precise suggestions
FR11: Epic 2 - Low confidence general area suggestions
FR12: Epic 2 - Multi-rotation piece testing
FR13: Epic 3 - Camera interface access
FR14: Epic 3 - Reference image display
FR15: Epic 3 - Position suggestion overlay
FR16: Epic 3 - Suggestion dismissal functionality
FR17: Epic 3 - Confidence score display
FR18: Epic 3 - Project navigation
FR19: Epic 3 - Settings menu access
FR20: Epic 4 - Local project storage
FR21: Epic 4 - Piece analysis result caching
FR22: Epic 4 - Progress tracking persistence
FR23: Epic 4 - Data export functionality
FR24: Epic 4 - Application logging storage
FR25: Epic 5 - Camera permission handling
FR26: Epic 5 - Device storage access
FR27: Epic 5 - Offline operation capability
FR28: Epic 5 - Android compliance requirements
FR29: Epic 6 - Image quality detection
FR30: Epic 6 - Photo retake requests
FR31: Epic 6 - Piece match failure handling
FR32: Epic 6 - Fallback suggestion mechanisms
FR33: Epic 6 - Error message display
FR34: Epic 6 - Error logging for debugging

## Epic List

### Epic 1: Project Management Foundation
Permettre aux utilisateurs de créer et gérer leurs projets de puzzles
**FRs couvertes:** FR1, FR2, FR3, FR4, FR5

### Epic 2: Computer Vision Recognition Engine  
Fournir des suggestions intelligentes de position de pièces
**FRs couvertes:** FR6, FR7, FR8, FR9, FR10, FR11, FR12

### Epic 3: User Interface & Experience
Offrir une interface intuitive pour l'interaction avec l'application
**FRs couvertes:** FR13, FR14, FR15, FR16, FR17, FR18, FR19

### Epic 4: Data Persistence & Management
Assurer la sauvegarde et la gestion fiable des données utilisateur
**FRs couvertes:** FR20, FR21, FR22, FR23, FR24

### Epic 5: System Integration & Compliance
Garantir la compatibilité et la conformité avec Android
**FRs couvertes:** FR25, FR26, FR27, FR28

### Epic 6: Error Handling & Recovery
Fournir une gestion robuste des erreurs et une récupération gracieuse
**FRs couvertes:** FR29, FR30, FR31, FR32, FR33, FR34

## Epic 1: Project Management Foundation

Permettre aux utilisateurs de créer et gérer leurs projets de puzzles

### Story 1.1: Android Project Setup with OpenCV

As a puzzle application developer,
I want to set up of Android project with OpenCV integration,
So that I have the foundation for computer vision processing.

**Acceptance Criteria:**

**Given** a new Android Studio project with Empty Activity template
**When** I configure build.gradle and project structure
**Then** the project builds successfully with OpenCV 4.8.0 dependency
**And** basic MVVM architecture structure is created
**And** Material Design 3 theme is configured
**And** the application launches without crashes
**And** the app size is under 50MB

### Story 1.2: Puzzle Project Creation

As a puzzle enthusiast,
I want to scan a puzzle box image to create a new project,
So that I can start working on solving the puzzle.

**Acceptance Criteria:**

**Given** the application is launched and camera permissions are granted
**When** I capture an image of a puzzle box
**Then** a new puzzle project is created with the box image as reference
**And** the project is saved to local SQLite database
**And** the project appears in the project list
**And** basic project metadata (creation date, name) is stored
**And** the image is stored in internal storage

### Story 1.3: Project List Display

As a puzzle enthusiast,
I want to view all my puzzle projects,
So that I can select which puzzle to work on.

**Acceptance Criteria:**

**Given** one or more puzzle projects exist in the database
**When** I open the application
**Then** I see a list of all my puzzle projects
**And** each project shows the box image thumbnail
**And** each project displays the creation date and name
**And** the list is scrollable if there are many projects
**And** tapping on a project navigates to project details

### Story 1.4: Project Selection & Navigation

As a puzzle enthusiast,
I want to select an existing puzzle project to work on,
So that I can continue solving my puzzle.

**Acceptance Criteria:**

**Given** I am viewing the project list
**When** I tap on a specific project
**Then** I navigate to the main puzzle working screen
**And** the puzzle box reference image is displayed
**And** the project's current progress is shown if available
**And** I can access the camera for piece capture
**And** navigation back returns to the project list

### Story 1.5: Project Deletion

As a puzzle enthusiast,
I want to delete puzzle projects I no longer need,
So that I can free up storage space.

**Acceptance Criteria:**

**Given** I am viewing the project list
**When** I long-press on a project and confirm deletion
**Then** the project is removed from the SQLite database
**And** all associated images are deleted from internal storage
**And** the project disappears from the list immediately
**And** a confirmation message is shown briefly
**And** other projects remain unaffected

### Story 1.6: Automatic Piece Pre-cutting

As a puzzle enthusiast,
I want the system to automatically pre-cut the box image into virtual puzzle pieces,
So that I have a reference for piece matching.

**Acceptance Criteria:**

**Given** a new puzzle project has been created from a box image
**When** the project initialization completes
**Then** the system analyzes the box image to identify puzzle piece outlines
**And** virtual puzzle piece coordinates and boundaries are calculated
**And** piece metadata (coordinates, dimensions) is stored in SQLite database
**And** the original box image remains stored in internal storage
**And** the pre-cutting process completes within 5 seconds
**And** the system can reference piece boundaries for future matching

## Epic 2: Computer Vision Recognition Engine

Fournir des suggestions intelligentes de position de pièces

### Story 2.1: Camera Interface for Piece Capture

As a puzzle enthusiast,
I want to access a dedicated camera interface for capturing puzzle pieces,
So that I can analyze pieces for position suggestions.

**Acceptance Criteria:**

**Given** I am working on a puzzle project
**When** I tap the capture button
**Then** the camera interface opens in full screen
**And** I can see a live camera preview
**And** I can capture an image by tapping the screen
**And** the captured image is saved temporarily for analysis
**And** I can retake the photo if needed
**And** the interface follows Material Design 3 guidelines

### Story 2.2: Image Quality Detection

As a puzzle enthusiast,
I want the system to detect if my piece photo is good quality,
So that I can get accurate recognition results.

**Acceptance Criteria:**

**Given** I have captured a puzzle piece image
**When** the system analyzes the image quality
**Then** the system checks for blur and lighting conditions
**And** I receive feedback if the image quality is insufficient
**And** I am prompted to retake the photo if quality is poor
**And** the system accepts the image if quality meets the minimum threshold
**And** the quality check completes within 2 seconds

### Story 2.3: Computer Vision Analysis Engine

As a puzzle enthusiast,
I want the system to analyze captured piece images using computer vision,
So that it can identify piece characteristics for matching.

**Acceptance Criteria:**

**Given** a good quality piece image has been captured
**When** the CV analysis engine processes the image
**Then** the system extracts the puzzle piece from the background
**And** the system isolates the piece shape with clear boundaries
**And** the system extracts piece shape and edge features
**And** the system identifies piece color patterns and textures
**And** the extracted piece image is saved for debugging visualization
**And** I can view the extracted piece overlay on the original image
**And** analysis results are stored persistently for comparison
**And** analysis completes within 3 seconds
**And** the system uses OpenCV algorithms for feature extraction and piece isolation

### Story 2.4: Piece Database Comparison

As a puzzle enthusiast,
I want the system to compare my captured piece against virtual puzzle pieces,
So that it can find potential matches in the puzzle.

**Acceptance Criteria:**

**Given** piece analysis results are available
**When** the system compares against the virtual piece database
**Then** the system retrieves matching virtual pieces from the project
**And** the system compares shape features between captured and virtual pieces
**And** the system calculates similarity scores for each potential match
**And** the system identifies the top 3 potential matches
**And** the comparison process completes within 2 seconds

### Story 2.5: Position Suggestion Generation

As a puzzle enthusiast,
I want the system to generate position suggestions for my puzzle piece,
So that I know where to place the piece in the puzzle.

**Acceptance Criteria:**

**Given** potential piece matches have been identified
**When** the system generates position suggestions
**Then** the system calculates precise coordinates for high-confidence matches
**And** the system provides general area suggestions for low-confidence matches
**And** the system assigns a confidence score to each suggestion
**And** suggestions are displayed as an overlay on the reference image
**And** generation completes within 1 second

### Story 2.6: Confidence Scoring System

As a puzzle enthusiast,
I want to see confidence scores for position suggestions,
So that I can trust the accuracy of the suggestions.

**Acceptance Criteria:**

**Given** position suggestions have been generated
**When** the confidence scoring system evaluates results
**Then** each suggestion displays a percentage confidence score
**And** high confidence (>80%) is highlighted in green
**And** medium confidence (50-80%) is shown in yellow
**And** low confidence (<50%) is displayed in red
**And** I can see the reasoning behind the confidence score
**And** the scoring algorithm considers shape, color, and pattern matches

### Story 2.7: Multi-rotation Testing

As a puzzle enthusiast,
I want the system to test my piece in multiple rotations,
So that I can find the best possible match regardless of piece orientation.

**Acceptance Criteria:**

**Given** a piece has been analyzed but initial match confidence is low
**When** multi-rotation testing runs
**Then** the system rotates the piece image in 90-degree increments
**And** the system tests each rotation against the virtual database
**And** the system selects the rotation with the highest confidence score
**And** the final suggestion includes the optimal rotation angle
**And** multi-rotation testing completes within 3 seconds
**And** the system can handle up to 4 rotations (0°, 90°, 180°, 270°)

## Epic 3: User Interface & Experience

Offrir une interface intuitive pour l'interaction avec l'application

### Story 3.1: Reference Image Display

As a puzzle enthusiast,
I want to view the puzzle box reference image while working,
So that I can compare it with my actual puzzle pieces.

**Acceptance Criteria:**

**Given** I am working on a puzzle project
**When** I am in the main puzzle working screen
**Then** the puzzle box reference image is displayed prominently
**And** the image is zoomable with pinch gestures
**And** the image maintains aspect ratio and quality
**And** I can pan around the image if it's larger than the screen
**And** the image remains visible during piece capture
**And** the display follows Material Design 3 guidelines

### Story 3.2: Position Suggestion Overlay

As a puzzle enthusiast,
I want to see position suggestions overlaid on the reference image,
So that I know exactly where to place my puzzle piece.

**Acceptance Criteria:**

**Given** position suggestions have been generated for a piece
**When** the system displays suggestions
**Then** suggestion markers are overlaid on the reference image
**And** each marker shows the suggested piece location
**And** markers are color-coded by confidence level (green/yellow/red)
**And** I can tap a marker to see detailed information
**And** the overlay is semi-transparent so the reference image remains visible
**And** the overlay updates automatically when new suggestions are generated

### Story 3.3: Suggestion Dismissal

As a puzzle enthusiast,
I want to dismiss position suggestions and continue manually,
So that I can maintain control over my puzzle solving process.

**Acceptance Criteria:**

**Given** position suggestions are displayed on screen
**When** I tap the dismiss button or swipe away suggestions
**Then** all suggestion overlays are removed from the screen
**And** the reference image remains visible
**And** I can continue with manual piece placement
**And** the dismiss action is clearly indicated with visual feedback
**And** I can request new suggestions by capturing another piece
**And** the dismiss button follows Material Design 3 guidelines

### Story 3.4: Project Navigation

As a puzzle enthusiast,
I want to navigate between different puzzle projects,
So that I can work on multiple puzzles seamlessly.

**Acceptance Criteria:**

**Given** I have multiple puzzle projects
**When** I access the navigation menu
**Then** I can see a list of all my projects
**And** I can switch to any project with a single tap
**Then** the current project state is preserved when switching
**And** navigation is smooth with transitions
**And** I can return to the main project list from any screen
**And** navigation follows Material Design 3 bottom navigation pattern

### Story 3.5: Settings Menu

As a puzzle enthusiast,
I want to access a simple settings menu,
So that I can customize the application behavior.

**Acceptance Criteria:**

**Given** I am in any part of the application
**When** I access the settings menu
**Then** I see essential settings options clearly organized
**And** I can adjust camera quality preferences
**And** I can set confidence level thresholds
**And** I can toggle suggestion auto-dismissal
**And** I can view application version and help information
**And** settings are saved persistently
**And** the interface follows Material Design 3 settings patterns

## Epic 4: Data Persistence & Management

Assurer la sauvegarde et la gestion fiable des données utilisateur

### Story 4.1: Local Project Storage

As a puzzle enthusiast,
I want to have my puzzle projects stored locally on my device,
So that I can access them without internet connection.

**Acceptance Criteria:**

**Given** I have created or modified puzzle projects
**When** application saves data
**Then** all project data is stored in SQLite database
**And** project images are stored in Android internal storage
**And** storage location follows Android best practices
**And** data persists across application restarts
**And** storage usage is optimized to stay under 50MB limit
**And** database schema supports project metadata and relationships

### Story 4.2: Piece Analysis Caching

As a puzzle enthusiast,
I want to have piece analysis results saved for future reference,
So that I don't need to re-analyze the same pieces repeatedly.

**Acceptance Criteria:**

**Given** a piece has been successfully analyzed
**When** system saves analysis results
**Then** piece features and metadata are cached in the database
**And** cached results are linked to the specific project
**And** I can retrieve cached results when working on the same piece
**And** cache is automatically cleaned up for deleted projects
**And** caching improves performance for repeated piece analysis
**And** cache size is monitored to prevent storage bloat

### Story 4.3: Progress Tracking

As a puzzle enthusiast,
I want to track my puzzle completion progress,
So that I can see how much of the puzzle I have completed.

**Acceptance Criteria:**

**Given** I am working on a puzzle project
**When** I place pieces or mark sections as complete
**Then** progress is automatically saved to the database
**And** I can view a visual progress indicator
**And** progress shows the percentage of pieces placed
**And** progress persists across application sessions
**And** I can manually adjust progress if needed
**And** progress data is backed up with project data

### Story 4.4: Data Export

As a puzzle enthusiast,
I want to export my puzzle data for backup purposes,
So that I don't lose my progress if something happens to my device.

**Acceptance Criteria:**

**Given** I have puzzle projects with progress
**When** I initiate data export
**Then** I can choose which projects to export
**And** system creates a backup file with all project data
**And** backup includes project metadata and progress
**And** backup file is saved to device storage
**And** I can share the backup file via Android share options
**And** exported data can be imported back into the application

### Story 4.5: Application Logging

As a puzzle enthusiast,
I want to application to log events for debugging,
So that issues can be identified and resolved quickly.

**Acceptance Criteria:**

**Given** the application is running
**When** significant events occur (errors, crashes, performance issues)
**Then** events are automatically logged with timestamps
**And** logs include error details and stack traces when applicable
**And** logs are stored in a dedicated log file
**And** log file size is automatically managed to prevent storage issues
**And** I can access logs for troubleshooting
**And** logging can be enabled/disabled in settings

## Epic 5: System Integration & Compliance

Garantir la compatibilité et la conformité avec Android

### Story 5.1: Camera Permission Handling

As a puzzle enthusiast,
I want to grant camera permissions to the application,
So that I can capture puzzle piece photos.

**Acceptance Criteria:**

**Given** application is launched for the first time
**When** application requires camera access
**Then** a clear permission request dialog is displayed
**And** dialog explains why camera permission is needed
**And** I can grant or deny the permission
**And** application handles both grant and denial gracefully
**And** I am prompted again if I deny and later try to use camera
**And** permission status is checked before camera operations

### Story 5.2: Device Storage Access

As a puzzle enthusiast,
I want to grant storage permissions to the application,
So that it can save my puzzle projects and images.

**Acceptance Criteria:**

**Given** application needs to save project data or images
**When** storage access is required
**Then** a clear storage permission request is displayed
**And** dialog explains what storage will be used for
**And** I can grant or deny the storage permission
**And** application handles both responses appropriately
**And** I can still use the application with limited functionality if denied
**And** permission is requested again when needed

### Story 5.3: Offline Operation

As a puzzle enthusiast,
I want to use the application without internet connection,
So that I can solve puzzles anywhere.

**Acceptance Criteria:**

**Given** device has no internet connection
**When** I use any application feature
**Then** all core functionality works without network access
**And** I can create, edit, and delete projects
**And** I can capture and analyze puzzle pieces
**And** I can receive position suggestions
**And** no network-related errors are displayed
**And** application clearly indicates offline status

### Story 5.4: Android Compliance

As a puzzle enthusiast,
I want to application to follow Android platform standards,
So that it works reliably across different Android versions.

**Acceptance Criteria:**

**Given** application is running on any Android device
**When** application accesses system resources
**Then** all storage follows Android scoped storage guidelines
**And** camera access follows Android camera API standards
**Then** application works on Android API level 24 and above
**And** all user interface follows Material Design 3 guidelines
**And** application passes Android compatibility checks
**And** privacy requirements are met according to Android policies

## Epic 6: Error Handling & Recovery

Fournir une gestion robuste des erreurs et une récupération gracieuse

### Story 6.1: Image Quality Detection

As a puzzle enthusiast,
I want to system to detect if my piece photo is blurry or low quality,
So that I can get accurate recognition results.

**Acceptance Criteria:**

**Given** I have captured a puzzle piece image
**When** system analyzes image quality
**Then** system checks for blur, focus, and lighting conditions
**And** I receive immediate feedback if quality is insufficient
**And** system provides specific guidance to improve photo quality
**And** quality assessment includes multiple factors (sharpness, contrast, glare)
**And** quality check completes within 2 seconds
**And** I can choose to retake photo or continue with current image

### Story 6.2: Photo Retake Requests

As a puzzle enthusiast,
I want to be prompted to retake photos when quality is poor,
So that I can ensure good recognition results.

**Acceptance Criteria:**

**Given** image quality has been assessed as insufficient
**When** system prompts for retake
**Then** a clear retake dialog is displayed with quality issues
**And** I can choose to retake photo immediately
**And** I can override suggestion and use current photo if desired
**And** previous photo is kept until new one is confirmed
**And** retake process returns me to camera interface
**And** system remembers my choice for future photos

### Story 6.3: Piece Match Failure Handling

As a puzzle enthusiast,
I want to system to handle cases where piece cannot be matched,
So that I can continue solving my puzzle.

**Acceptance Criteria:**

**Given** piece analysis finds no good matches in database
**When** match failure occurs
**Then** a clear "no match found" message is displayed
**And** I am offered options to continue manually or try again
**And** system saves the unmatched piece for future reference
**And** I can request manual position entry
**And** failure does not crash the application
**And** alternative suggestions are provided if available

### Story 6.4: Fallback Suggestion Mechanisms

As a puzzle enthusiast,
I want to have fallback suggestions when primary algorithms fail,
So that I can still get helpful guidance.

**Acceptance Criteria:**

**Given** primary recognition algorithm fails or has low confidence
**When** fallback mechanisms are activated
**Then** alternative matching algorithms are attempted
**And** system provides general area suggestions when precise matching fails
**And** I receive suggestions based on piece shape only if color matching fails
**And** fallback process is transparent to the user
**And** multiple fallback strategies are attempted in sequence
**And** I can choose which suggestion to use

### Story 6.5: Error Message Display

As a puzzle enthusiast,
I want to see clear error messages when recognition fails,
So that I understand what went wrong.

**Acceptance Criteria:**

**Given** an error occurs during piece recognition or processing
**When** error message is displayed
**Then** message is written in clear, user-friendly language
**And** message explains what went wrong in simple terms
**And** I receive suggestions on how to fix the issue
**And** error messages are consistent in tone and style
**And** I can dismiss error messages easily
**And** critical errors provide option to contact support

### Story 6.6: Error Logging for Debugging

As a puzzle enthusiast,
I want to have errors logged for debugging and improvement,
So that issues can be resolved in future versions.

**Acceptance Criteria:**

**Given** an error occurs during application operation
**When** error is detected by the system
**Then** error details are automatically logged with timestamp
**And** log includes error type, context, and device information
**And** failed piece images are saved with error logs for analysis
**And** logs are categorized by severity level
**And** error data can be exported for support requests
**And** logging does not impact application performance significantly
