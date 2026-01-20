# Story 1.1: Android Project Setup with OpenCV

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a puzzle application developer,
I want to set up of Android project with OpenCV integration,
So that I have the foundation for computer vision processing.

## Acceptance Criteria

**Given** a new Android Studio project with Empty Activity template
**When** I configure build.gradle and project structure
**Then** the project builds successfully with OpenCV 4.8.0 dependency
**And** basic MVVM architecture structure is created
**And** Material Design 3 theme is configured
**And** the application launches without crashes
**And** the app size is under 50MB

## Tasks / Subtasks

- [ ] Task 1: Create Android Studio project with Empty Activity template (AC: 1)
  - [ ] Subtask 1.1: Create new project with Empty Activity, Kotlin, Minimum SDK 24
  - [ ] Subtask 1.2: Configure project structure following architecture guidelines
- [ ] Task 2: Add OpenCV 4.8.0 dependency and configure build (AC: 1)
  - [ ] Subtask 2.1: Add OpenCV dependency to app/build.gradle
  - [ ] Subtask 2.2: Configure ProGuard/R8 for OpenCV optimization
  - [ ] Subtask 2.3: Verify build compiles successfully
- [ ] Task 3: Setup basic MVVM architecture structure (AC: 2)
  - [ ] Subtask 3.1: Create base package structure (ui, domain, data, shared)
  - [ ] Subtask 3.2: Create MainActivity with basic MVVM setup
  - [ ] Subtask 3.3: Create base ViewModel classes
- [ ] Task 4: Configure Material Design 3 theme (AC: 3)
  - [ ] Subtask 4.1: Setup Material Design 3 theme in themes.xml
  - [ ] Subtask 4.2: Configure colors.xml for puzzle-themed colors
  - [ ] Subtask 4.3: Create basic layout resources
- [ ] Task 5: Implement OpenCV initialization and test (AC: 4, 5)
  - [ ] Subtask 5.1: Add OpenCV initialization code in MainActivity
  - [ ] Subtask 5.2: Create basic OpenCV loading test
  - [ ] Subtask 5.3: Verify app launches without crashes and OpenCV loads successfully
- [ ] Task 6: Optimize app size and validate constraints (AC: 6)
  - [ ] Subtask 6.1: Configure build optimizations for <50MB target
  - [ ] Subtask 6.2: Verify final APK size meets requirements
  - [ ] Subtask 6.3: Create basic unit tests for project setup

## Dev Notes

### Architecture Requirements
- **Pattern**: MVVM with Single Activity architecture [Source: architecture.md#Core Architectural Decisions]
- **Structure**: Feature-based packages with shared components [Source: architecture.md#Project Structure & Boundaries]
- **Naming**: Follow Android conventions (camelCase for classes, snake_case for database) [Source: architecture.md#Naming Patterns]

### Technical Specifications
- **Language**: Kotlin native [Source: architecture.md#Starter Template Evaluation]
- **OpenCV Version**: 4.8.0 (specified in ACs, latest stable as of 2026)
- **Minimum SDK**: 24 (Android 7.0) for OpenCV 4.8.0 compatibility [Source: architecture.md#Starter Template Evaluation]
- **Build System**: Gradle with Groovy DSL [Source: architecture.md#Starter Template Evaluation]
- **UI Framework**: Material Design 3 with XML layouts [Source: architecture.md#Starter Template Evaluation]

### OpenCV Integration Details
- **Dependency**: `implementation("org.opencv:opencv-android:4.8.0")` [Source: architecture.md#Note]
- **Initialization**: Must call `OpenCVLoader.initLocal()` before using OpenCV functions [Source: OpenCV Documentation]
- **Loading Pattern**: Load OpenCV at app start with error handling and user feedback [Source: OpenCV Documentation]
- **Version Note**: OpenCV 4.9.0+ available via Maven Central, but we use 4.8.0 as specified in requirements [Source: OpenCV Documentation]

### Project Structure Requirements
```
com.puzzlehelper/
├── MainActivity.kt
├── feature/
│   ├── camera/
│   ├── recognition/
│   ├── suggestions/
│   └── storage/
├── shared/
│   ├── ui/
│   ├── domain/
│   └── utils/
└── test/
```
[Source: architecture.md#Complete Project Directory Structure]

### Material Design 3 Configuration
- **Theme**: Material Design 3 with puzzle-themed colors [Source: architecture.md#Starter Template Evaluation]
- **Components**: Use Material Design 3 components throughout [Source: architecture.md#Core Architectural Decisions]
- **Colors**: Configure puzzle-themed color scheme in colors.xml [Source: architecture.md#Starter Template Evaluation]

### Performance Constraints
- **App Size**: <50MB total [Source: PRD.md#Non-Functional Requirements]
- **Startup Time**: <3 seconds [Source: PRD.md#Non-Functional Requirements]
- **Memory Usage**: Optimize for mobile devices with limited memory [Source: architecture.md#Cross-Cutting Concerns]

### Testing Requirements
- **Framework**: JUnit 4/5 for unit tests [Source: architecture.md#Starter Template Evaluation]
- **UI Tests**: Espresso for UI testing [Source: architecture.md#Starter Template Evaluation]
- **Coverage**: Comprehensive test coverage for project setup [Source: architecture.md#Implementation Patterns & Consistency Rules]

### Project Structure Notes
- **Alignment**: Follow unified project structure exactly as documented [Source: architecture.md#Project Structure & Boundaries]
- **Feature Isolation**: Each feature (camera, recognition, suggestions, storage) in separate packages [Source: architecture.md#Architectural Boundaries]
- **Shared Components**: Common UI, domain models, and utilities in shared/ package [Source: architecture.md#Complete Project Directory Structure]

### References
- [Source: epics.md#Story 1.1] - Story requirements and acceptance criteria
- [Source: architecture.md#Core Architectural Decisions] - MVVM and Single Activity patterns
- [Source: architecture.md#Starter Template Evaluation] - Android Studio Empty Activity setup
- [Source: architecture.md#Implementation Patterns & Consistency Rules] - Naming and structure patterns
- [Source: PRD.md#Non-Functional Requirements] - Performance and size constraints
- [Source: OpenCV Documentation] - OpenCV initialization and integration patterns

## Dev Agent Record

### Agent Model Used

Claude Sonnet 3.5 - November 2024 version

### Debug Log References

### Completion Notes List

### File List

- app/build.gradle (OpenCV dependency and build configuration)
- app/src/main/java/com/puzzlehelper/MainActivity.kt (Main activity with OpenCV init)
- app/src/main/res/values/themes.xml (Material Design 3 theme)
- app/src/main/res/values/colors.xml (Puzzle-themed colors)
- app/src/main/java/com/puzzlehelper/feature/ (Package structure)
- app/src/main/java/com/puzzlehelper/shared/ (Shared components)
- app/src/test/ (Test structure)
- proguard-rules.pro (OpenCV optimization rules)

## Change Log

- 2026-01-20: Initial story creation with comprehensive OpenCV integration requirements
