package com.lilyan_lefevre.puzzleit.shared.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Unit tests for database operations
 * These tests verify Room database setup and Project entity operations
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var database: AppDatabase
    private lateinit var projectDao: ProjectDao

    @Before
    fun createDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        projectDao = database.projectDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun `should insert and retrieve project`() = runBlocking {
        // Given
        val project = Project(
            id = "test-project-1",
            name = "Test Puzzle",
            creationDate = System.currentTimeMillis(),
            imagePath = "/path/to/image.jpg",
            thumbnailPath = "/path/to/thumb.jpg",
            status = "active",
            puzzleSize = 1000,
            difficulty = "hard"
        )

        // When
        projectDao.insertProject(project)
        val retrievedProject = projectDao.getProjectById("test-project-1")

        // Then
        assertNotNull(retrievedProject)
        assertEquals("Test Puzzle", retrievedProject?.name)
        assertEquals("test-project-1", retrievedProject?.id)
    }

    @Test
    fun `should get all projects`() = runBlocking {
        // Given
        val project1 = Project(
            id = "test-project-1",
            name = "Test Puzzle 1",
            creationDate = System.currentTimeMillis(),
            imagePath = "/path/to/image1.jpg",
            thumbnailPath = "/path/to/thumb1.jpg",
            status = "active",
            puzzleSize = 1000,
            difficulty = "hard"
        )
        val project2 = Project(
            id = "test-project-2",
            name = "Test Puzzle 2",
            creationDate = System.currentTimeMillis(),
            imagePath = "/path/to/image2.jpg",
            thumbnailPath = "/path/to/thumb2.jpg",
            status = "active",
            puzzleSize = 1000,
            difficulty = "hard"
        )

        // When
        projectDao.insertProject(project1)
        projectDao.insertProject(project2)

        val projects = projectDao.getAllProjects().first()

        // Then
        assertEquals(2, projects.size)
        assertTrue(projects.any { it.name == "Test Puzzle 1" })
        assertTrue(projects.any { it.name == "Test Puzzle 2" })
    }

    @Test
    fun `should delete project`() = runBlocking {
        // Given
        val project = Project(
            id = "test-project-1",
            name = "Test Puzzle",
            creationDate = System.currentTimeMillis(),
            imagePath = "/path/to/image.jpg",
            thumbnailPath = "/path/to/thumb.jpg",
            status = "active",
            puzzleSize = 1000,
            difficulty = "hard"
        )
        projectDao.insertProject(project)

        // When
        projectDao.deleteProject(project)
        val retrievedProject = projectDao.getProjectById("test-project-1")

        // Then
        assertNull(retrievedProject)
    }
}
