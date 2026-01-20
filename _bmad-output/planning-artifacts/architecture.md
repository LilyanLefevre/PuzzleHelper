---
stepsCompleted: ['step-01-init', 'step-02-context', 'step-03-starter', 'step-04-decisions', 'step-05-patterns', 'step-06-structure', 'step-07-validation']
inputDocuments: ['/Users/lilyan/Documents/Projets/PuzzleHelper/_bmad-output/planning-artifacts/prd.md', '/Users/lilyan/Documents/Projets/PuzzleHelper/_bmad-output/planning-artifacts/ux-design-specification.md']
workflowType: 'architecture'
project_name: 'PuzzleHelper'
user_name: 'Lilyan'
date: '2026-01-19T22:39:00.000Z'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**
34 exigences fonctionnelles organisées en : capture photo, reconnaissance CV, suggestions adaptatives, mode créateur, et interface utilisateur. L'architecture doit supporter le traitement d'images local avec algorithmes de vision par ordinateur pour reconnaissance fiable de pièces de puzzle similaires.

**Non-Functional Requirements:**
Performance <3s pour reconnaissance et suggestion, fiabilité >70% de suggestions correctes, contraintes strictes de <50MB app size et 100% offline, Android natif. Ces NFRs critiques vont façonner les décisions technologiques et l'architecture système.

**Scale & Complexity:**
- Primary domain: Mobile natif avec traitement d'images local
- Complexity level: Moyenne
- Estimated architectural components: 5-7 composants principaux (CV engine, interface utilisateur, gestion mémoire, stockage local, algorithmes de suggestion)

### Technical Constraints & Dependencies

**Contraintes Plateforme:**
- Android natif obligatoire (pas de cross-platform)
- 100% offline requis (pas de dépendances réseau)
- <50MB app size (optimisation mémoire et stockage critique)
- Performance <3s (traitement CV en temps réel)

**Dépendances Techniques:**
- Algorithmes de vision par ordinateur (OpenCV ou similaire)
- Material Design 3 components
- Stockage local pour images et modèles
- Traitement d'images optimisé pour mobile

### Cross-Cutting Concerns Identified

**Performance CV:** Optimisation des algorithmes de reconnaissance pour traitement <3s sur device mobile avec mémoire limitée.

**Gestion Mémoire:** Contrôle strict de l'utilisation mémoire pour respecter <50MB app size et éviter OOM sur devices bas de gamme.

**Interface Responsive:** Material Design 3 avec flow centré minimaliste nécessitant des composants optimisés pour performance.

**Fiabilité Algorithmes:** Architecture modulaire pour permettre évolution des algorithmes CV sans refactoring complet.

- **Mode Créateur:** Support pour diagnostics avancés et outils de test pendant développement.

## Starter Template Evaluation

### Primary Technology Domain

**Mobile App Android Natif** - Avec traitement d'images local et algorithmes CV, basé sur les préférences utilisateur pour Kotlin natif et XML.

### Starter Options Considered

**Option 1 : Android Studio Empty Activity (Recommandé)** - Setup natif pur avec contrôle total sur l'architecture, parfait pour expertise OpenCV existante.

**Option 2 : Now In Android Template** - Architecture moderne MVVM mais plus complexe que nécessaire pour les besoins du projet.

**Option 3 : Custom Setup** - Contrôle total avec configuration manuelle, mais plus de travail initial requis.

### Selected Starter: Android Studio Empty Activity + OpenCV

**Rationale for Selection:**
- **Timeline 2 semaines** - Setup rapide sans complexité inutile
- **Expertise OpenCV** - Intégration immédiate possible avec bibliothèques natives
- **Contrôle total** - Architecture optimisée pour <50MB et performance <3s
- **XML Layout** - Parfait pour interface centrée minimaliste (Direction 1)
- **Kotlin Natif** - Performance optimale et compatibilité avec expertise existante

**Initialization Command:**

```bash
# Dans Android Studio
New Project → Empty Activity → Kotlin → Minimum SDK 24
```

**Architectural Decisions Provided by Starter:**

**Language & Runtime:**
- Kotlin natif avec configuration par défaut
- Runtime Android SDK optimisé pour performance locale
- Support pour traitement d'images natif

**Styling Solution:**
- XML layouts avec Material Design 3 components
- Theme system configuré pour adaptation couleurs puzzle
- Support pour interface responsive et accessible

**Build Tooling:**
- Gradle avec configuration optimisée pour Android natif
- Build variants pour debug/release
- Support pour ProGuard/R8 pour optimisation taille

**Testing Framework:**
- JUnit 4/5 pour tests unitaires
- Espresso pour tests UI
- Support pour tests d'intégration OpenCV

**Code Organization:**
- Structure par défaut Android (src/main/java, src/main/res)
- Package organization pour CV engine, UI, models
- Support pour modules additionnels si nécessaire

**Development Experience:**
- Android Studio avec hot reload pour layouts XML
- Debugging natif pour code Kotlin et bibliothèques natives
- Profiling tools pour optimisation performance CV

**Note:** L'initialisation avec OpenCV sera la première story d'implémentation :
```kotlin
// build.gradle (app)
implementation("org.opencv:opencv-android:4.8.0")
```

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
- CV Engine Singleton - Optimisation mémoire et performance
- Single Activity Architecture - Flow centré minimaliste
- Internal Storage + SQLite - Stockage privé et métadonnées
- MVVM Architecture - Séparation logique et UI moderne

**Important Decisions (Shape Architecture):**
- Kotlin natif + XML layouts - Expertise existante
- OpenCV pour traitement CV - Performance et intégration
- Material Design 3 - Interface utilisateur
- Gradle build system - Écosystème Android natif

**Deferred Decisions (Post-MVP):**
- Tests d'intégration avancés
- Mode créateur étendu
- Features communautaires futures

### CV & Image Processing Architecture

**CV Engine Architecture:**
- **Pattern:** Singleton pour optimisation mémoire et performance <3s
- **Components:** ImageProcessor, PieceRecognizer, PositionCalculator
- **Optimization:** Instance unique, gestion mémoire stricte pour <50MB
- **Integration:** OpenCV Android natif avec traitement local

**Image Processing Flow:**
1. Camera capture → ImageProcessor
2. Preprocessing (resize, enhance) → PieceRecognizer  
3. Feature extraction → PositionCalculator
4. Confidence scoring → SuggestionService

### UI & Navigation Architecture

**Single Activity Architecture:**
- **Pattern:** Single Activity avec fragments pour flow Direction 1
- **Components:** MainActivity, CameraFragment, ResultFragment
- **Navigation:** Fragment transactions pour flow centré
- **Layout:** XML layouts avec Material Design 3

**Fragment Structure:**
- **CameraFragment:** Interface centrée minimaliste pour capture
- **ResultFragment:** Affichage suggestions et feedback
- **Navigation:** Smooth transitions entre fragments

### Storage & Persistence Architecture

**Storage Strategy:**
- **Images:** Internal Storage privé (pas de permissions requises)
- **Metadata:** SQLite database pour historique et préférences
- **Cache:** Memory cache pour images récentes
- **Size Management:** Nettoyage automatique pour respecter <50MB

**Database Schema:**
```sql
-- Puzzles table
CREATE TABLE puzzles (
    id INTEGER PRIMARY KEY,
    name TEXT,
    box_image_path TEXT,
    created_at TIMESTAMP,
    completed_at TIMESTAMP
);

-- Pieces table  
CREATE TABLE pieces (
    id INTEGER PRIMARY KEY,
    puzzle_id INTEGER,
    image_path TEXT,
    recognized_at TIMESTAMP
);
```

### Modal & Logic Architecture

**MVVM Architecture:**
- **ViewModels:** MainViewModel, CameraViewModel, ResultViewModel
- **Services:** CVService, SuggestionService, StorageService
- **Repositories:** PuzzleRepository, PieceRepository
- **Models:** Puzzle, Piece, Suggestion

**Service Layer:**
- **CVService:** Traitement d'images et reconnaissance
- **SuggestionService:** Logique de recommandation adaptative
- **StorageService:** Gestion des données persistantes
- **ConfidenceService:** Calcul et gestion de la confiance

### Decision Impact Analysis

**Implementation Sequence:**
1. Setup projet Android Studio + OpenCV
2. Architecture MVVM de base
3. CV Engine singleton
4. Single Activity avec fragments
5. Services et repositories
6. UI Material Design 3
7. Storage SQLite + Internal Storage

**Cross-Component Dependencies:**
- MVVM → Services → Repositories → Storage
- CV Engine → SuggestionService → UI
- Storage → Cache → Performance optimization

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

**Critical Conflict Points Identified:**
4 areas where AI agents could make different choices

### Naming Patterns

**Database Naming Conventions:**
- **Tables:** snake_case (puzzles, puzzle_pieces, puzzle_progress)
- **Columns:** snake_case (puzzle_id, piece_id, created_at)
- **Foreign Keys:** snake_case with _id suffix (puzzle_id, piece_id)
- **Indexes:** snake_case with _idx suffix (puzzles_name_idx)

**API Naming Conventions:**
- **Classes:** camelCase (PuzzleService, PuzzleViewModel, PuzzleRepository)
- **Files:** camelCase with extensions (PuzzleViewModel.kt, PuzzleRepository.kt)
- **Functions:** camelCase (getPuzzle, savePiece, calculatePosition)
- **Variables:** camelCase (puzzleId, pieceImage, confidenceScore)

**Code Naming Conventions:**
- **Packages:** lowercase (com.puzzlehelper.ui, com.puzzlehelper.domain)
- **Classes:** PascalCase (Puzzle, PuzzlePiece, Suggestion)
- **Interfaces:** PascalCase (PuzzleRepository, CVService)
- **Enums:** PascalCase (ConfidenceLevel, ProcessingState)

### Structure Patterns

**Project Organization:**
```
com.puzzlehelper/
├── ui/
│   ├── fragments/
│   └── viewmodels/
├── domain/
│   ├── models/
│   └── services/
├── data/
│   ├── repositories/
│   └── database/
└── utils/
```

**File Structure Patterns:**
- **Activities:** MainActivity.kt
- **Fragments:** CameraFragment.kt, ResultFragment.kt
- **ViewModels:** MainViewModel.kt, CameraViewModel.kt
- **Services:** PuzzleService.kt, CVService.kt
- **Repositories:** PuzzleRepository.kt, PieceRepository.kt

### Format Patterns

**API Response Formats:**
```kotlin
// Success response
data class PuzzleResponse(
    val success: Boolean,
    val data: Puzzle?,
    val error: String?
)

// Error response
data class ErrorResponse(
    val error: String,
    val code: Int,
    val details: String?
)
```

**Data Exchange Formats:**
- **JSON Fields:** camelCase (puzzleId, pieceImage, confidenceScore)
- **Booleans:** true/false
- **Nulls:** Explicit null handling with nullable types
- **Arrays:** Lists for collections

### Communication Patterns

**Event System Patterns:**
```kotlin
// Event naming
sealed class PuzzleEvent {
    data class PuzzleCreated(val puzzle: Puzzle) : PuzzleEvent()
    data class PieceRecognized(val piece: PuzzlePiece) : PuzzleEvent()
    data class SuggestionGenerated(val suggestion: Suggestion) : PuzzleEvent()
}
```

**State Management Patterns:**
- **ViewModel State:** MutableStateFlow for UI state
- **Action Naming:** camelCase (updatePuzzle, setLoading, setError)
- **State Organization:** Separate UI state from business state

### Process Patterns

**Error Handling Patterns:**
```kotlin
// Global error handling
sealed class PuzzleError : Exception() {
    data class CVProcessingError(override val message: String) : PuzzleError()
    data class StorageError(override val message: String) : PuzzleError()
    data class ValidationError(override val message: String) : PuzzleError()
}
```

**Loading State Patterns:**
```kotlin
// Loading state management
data class LoadingState(
    val isProcessing: Boolean = false,
    val message: String? = null,
    val progress: Float = 0f
)
```

### Enforcement Guidelines

**All AI Agents MUST:**
- Follow Android naming conventions (camelCase for classes, snake_case for database)
- Use MVVM pattern with proper separation of concerns
- Implement proper error handling with custom exceptions
- Use Material Design 3 components and themes
- Follow singleton pattern for CV Engine
- Use repository pattern for data access

**Pattern Enforcement:**
- Verify naming conventions during code review
- Use Android Lint rules to enforce patterns
- Document pattern violations in code comments
- Update patterns in this document when needed

### Pattern Examples

**Good Examples:**
```kotlin
// Database table definition
@Entity(tableName = "puzzles")
data class Puzzle(
    @PrimaryKey val id: Long,
    val name: String,
    val boxImagePath: String,
    val createdAt: Long,
    val completedAt: Long?
)

// ViewModel with proper state management
class CameraViewModel : ViewModel() {
    private val _loadingState = MutableStateFlow(LoadingState())
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()
    
    fun capturePiece(image: Bitmap) {
        _loadingState.value = LoadingState(isProcessing = true, message = "Analyzing piece...")
        // Process image...
    }
}
```

**Anti-Patterns:**
```kotlin
// Don't do this - inconsistent naming
class puzzleService { }  // Should be PuzzleService
val puzzle_id = 1L     // Should be puzzleId
val isLoading = true    // Should be isProcessing

## Project Structure & Boundaries

### Complete Project Directory Structure

```
PuzzleHelper/
├── app/
│   ├── src/main/
│   │   ├── java/com/puzzlehelper/
│   │   │   ├── MainActivity.kt
│   │   │   ├── feature/
│   │   │   │   ├── camera/
│   │   │   │   │   ├── CameraFragment.kt
│   │   │   │   │   ├── CameraViewModel.kt
│   │   │   │   │   ├── CameraService.kt
│   │   │   │   │   └── test/
│   │   │   │   │       ├── CameraFragmentTest.kt
│   │   │   │   │       ├── CameraViewModelTest.kt
│   │   │   │   │       └── CameraFeatureTest.kt
│   │   │   │   ├── recognition/
│   │   │   │   │   ├── RecognitionFragment.kt
│   │   │   │   │   ├── RecognitionViewModel.kt
│   │   │   │   │   ├── CVService.kt
│   │   │   │   │   ├── PieceRecognizer.kt
│   │   │   │   │   └── test/
│   │   │   │   │       ├── RecognitionFragmentTest.kt
│   │   │   │   │       ├── CVServiceTest.kt
│   │   │   │   │       └── RecognitionFeatureTest.kt
│   │   │   │   ├── suggestions/
│   │   │   │   │   ├── SuggestionFragment.kt
│   │   │   │   │   ├── SuggestionViewModel.kt
│   │   │   │   │   ├── SuggestionService.kt
│   │   │   │   │   ├── PositionCalculator.kt
│   │   │   │   │   └── test/
│   │   │   │   │       ├── SuggestionFragmentTest.kt
│   │   │   │   │       ├── SuggestionServiceTest.kt
│   │   │   │   │       └── SuggestionFeatureTest.kt
│   │   │   │   └── storage/
│   │   │   │       ├── StorageRepository.kt
│   │   │   │       ├── StorageService.kt
│   │   │   │       └── test/
│   │   │   │           ├── StorageRepositoryTest.kt
│   │   │   │           └── StorageFeatureTest.kt
│   │   │   └── shared/
│   │   │       ├── ui/
│   │   │       │   ├── components/
│   │   │       │   └── themes/
│   │   │       ├── domain/
│   │   │       │   ├── models/
│   │   │       │   │   ├── Puzzle.kt
│   │   │       │   │   ├── PuzzlePiece.kt
│   │   │       │   │   └── Suggestion.kt
│   │   │       │   └── interfaces/
│   │   │       │       ├── PuzzleRepository.kt
│   │   │       │       └── CVService.kt
│   │   │       └── utils/
│   │   │           ├── ImageUtils.kt
│   │   │           ├── Constants.kt
│   │   │           └── Extensions.kt
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml
│   │   │   │   ├── fragment_camera.xml
│   │   │   │   └── fragment_result.xml
│   │   │   ├── values/
│   │   │   │   ├── colors.xml
│   │   │   │   ├── strings.xml
│   │   │   │   └── themes.xml
│   │   │   └── drawable/
│   │   │       ├── ic_camera.xml
│   │   │       └── ic_puzzle.xml
│   │   └── test/
│   │       └── integration/
│   │           ├── CameraToRecognitionTest.kt
│   │           └── RecognitionToSuggestionTest.kt
│   └── build.gradle
├── gradle/
├── libs/
└── docs/
    └── README.md
```

### Architectural Boundaries

**Feature Boundaries:**
- **Camera Feature:** Isolated with own UI, logic, and tests
- **Recognition Feature:** CV processing with dedicated service layer
- **Suggestion Feature:** Business logic separated from UI
- **Storage Feature:** Data access abstracted behind repository pattern

**Component Boundaries:**
- **UI → ViewModels:** Via LiveData/MutableStateFlow
- **ViewModels → Services:** Dependency injection
- **Services → Repositories:** Interface-based access
- **Repositories → Storage:** SQLite + Internal Storage

**Data Boundaries:**
- **Repositories → SQLite:** Room database with entities
- **Repositories → Internal Storage:** File-based image storage
- **Services → Cache:** Memory caching for performance optimization

### Requirements to Structure Mapping

**Feature/Epic Mapping:**
- **FR: Scan boîte puzzle** → feature/camera/
- **FR: Reconnaissance pièce** → feature/recognition/
- **FR: Suggestions position** → feature/suggestions/
- **FR: Mode créateur** → feature/suggestions/ (extended)
- **FR: Interface utilisateur** → feature/camera/ + shared/ui/

**Cross-Cutting Concerns:**
- **MVVM Architecture:** All features follow same pattern
- **TDD Approach:** Each feature has comprehensive test suite
- **Material Design 3:** shared/ui/themes/ and components/
- **OpenCV Integration:** feature/recognition/CVService.kt
- **Performance Optimization:** Shared utils and caching

### Integration Points

**Internal Communication:**
- **Feature Communication:** Via shared domain models and interfaces
- **Service Communication:** Dependency injection with interfaces
- **Data Flow:** UI → ViewModel → Service → Repository → Storage

**External Integrations:**
- **OpenCV Library:** feature/recognition/CVService.kt
- **Android Camera API:** feature/camera/CameraService.kt
- **Material Design 3:** shared/ui/themes/

**Data Flow:**
1. Camera capture → CameraService → CVService
2. Image processing → PieceRecognizer → PositionCalculator
3. Confidence scoring → SuggestionService → Repository
4. Storage → SQLite database + Internal Storage

### File Organization Patterns

**Configuration Files:**
- **app/build.gradle:** Dependencies and build configuration
- **gradle/wrapper:** Gradle wrapper configuration
- **docs/README.md:** Project documentation

**Source Organization:**
- **Feature-based:** All code organized by business feature
- **Shared components:** Common UI, domain models, utilities
- **Test co-location:** Tests within each feature package

**Test Organization:**
- **Unit tests:** Within each feature/test/ directory
- **Integration tests:** app/src/test/integration/
- **Feature tests:** Comprehensive test coverage per feature

**Asset Organization:**
- **Layouts:** app/src/main/res/layout/
- **Drawables:** app/src/main/res/drawable/
- **Themes:** app/src/main/res/values/themes.xml

### Development Workflow Integration

**Development Server Structure:**
- **Android Studio:** Native Android development environment
- **Gradle Build:** Automated build and dependency management
- **Feature Isolation:** Each feature can be developed independently

**Build Process Structure:**
- **Feature Compilation:** Gradle builds all features together
- **Test Execution:** JUnit runs feature tests automatically

**Key Strengths:**
- Stack technologique parfaitement aligné avec expertise utilisateur
- Architecture évolutive avec feature packages isolés
- TDD intégré pour qualité garantie du code
- Performance optimisée pour contraintes strictes (<3s, <50MB)
- Mode créateur avec diagnostics avancés pour différenciation
- Patterns d'implémentation complets et clairs pour agents IA

**Areas for Future Enhancement:**
- Mode créateur étendu avec entraînement local
- Features communautaires (partage de puzzles)
- Algorithmes CV avancés avec apprentissage
- Support multi-types de puzzles

### Implementation Handoff

**AI Agent Guidelines:**
- Follow all architectural decisions exactly as documented
- Use implementation patterns consistently across all components
- Respect project structure and feature boundaries
- Implement TDD approach with comprehensive test coverage
- Refer to this document for all architectural questions
- Support mode créateur avec diagnostics avancés

**First Implementation Priority:**
```bash
# Initialisation du projet
New Project → Empty Activity → Kotlin → Minimum SDK 24

# Première dépendance à ajouter
implementation("org.opencv:opencv-android:4.8.0")

# Architecture à implémenter en priorité
1. Setup MVVM de base
2. CV Engine singleton
3. Feature camera avec capture
4. Feature recognition avec OpenCV
5. Feature suggestions avec mode créateur
6. Tests TDD pour chaque feature
