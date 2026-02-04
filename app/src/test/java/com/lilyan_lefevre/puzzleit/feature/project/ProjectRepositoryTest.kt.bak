package com.lilyan_lefevre.puzzleit.feature.project

import com.lilyan_lefevre.puzzleit.feature.storage.ImageStorageManager
import com.lilyan_lefevre.puzzleit.shared.database.Project
import com.lilyan_lefevre.puzzleit.shared.database.ProjectDao
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProjectRepositoryTest {

    private lateinit var repository: ProjectRepository
    private val mockProjectDao: ProjectDao = mockk()
    private val mockImageStorageManager: ImageStorageManager = mockk()

    private val testProject = Project(
        id = "test-id",
        name = "Test Project",
        creationDate = System.currentTimeMillis(),
        imagePath = "/path/to/image.jpg",
        thumbnailPath = "/path/to/thumbnail.jpg",
        status = "active"
    )

    @Before
    fun setup() {
        repository = ProjectRepository(mockProjectDao, mockImageStorageManager)
    }

    @Test
    fun `deleteProject by id should delete files and database record successfully`() = runTest {
        // Given
        coEvery { mockProjectDao.getProjectById("test-id") } returns testProject
        coEvery { mockImageStorageManager.deleteProjectImages(any(), any()) } returns true
        coEvery { mockProjectDao.deleteProject(testProject) } just Runs

        // When
        val result = repository.deleteProject("test-id")

        // Then
        assert(result.isSuccess) { "Deletion should succeed" }
        assert(result.getOrNull() == true) { "Should return true on success" }
        
        coVerify { mockImageStorageManager.deleteProjectImages(testProject.imagePath, testProject.thumbnailPath) }
        coVerify { mockProjectDao.deleteProject(testProject) }
    }

    @Test
    fun `deleteProject by id should fail when project not found`() = runTest {
        // Given
        coEvery { mockProjectDao.getProjectById("nonexistent-id") } returns null

        // When
        val result = repository.deleteProject("nonexistent-id")

        // Then
        assert(result.isFailure) { "Deletion should fail when project not found" }
        assert(result.exceptionOrNull()?.message == "Project not found") { "Should return appropriate error" }
        
        coVerify(exactly = 0) { mockImageStorageManager.deleteProjectImages(any(), any()) }
        coVerify(exactly = 0) { mockProjectDao.deleteProject(any()) }
    }

    @Test
    fun `deleteProject by id should handle file deletion errors gracefully`() = runTest {
        // Given
        coEvery { mockProjectDao.getProjectById("test-id") } returns testProject
        coEvery { mockImageStorageManager.deleteProjectImages(any(), any()) } returns false
        coEvery { mockProjectDao.deleteProject(testProject) } just Runs

        // When
        val result = repository.deleteProject("test-id")

        // Then
        assert(result.isSuccess) { "Deletion should still succeed even if file deletion fails" }
        
        coVerify { mockImageStorageManager.deleteProjectImages(testProject.imagePath, testProject.thumbnailPath) }
        coVerify { mockProjectDao.deleteProject(testProject) }
    }

    @Test
    fun `deleteProject by id should handle database errors`() = runTest {
        // Given
        coEvery { mockProjectDao.getProjectById("test-id") } returns testProject
        coEvery { mockImageStorageManager.deleteProjectImages(any(), any()) } returns true
        coEvery { mockProjectDao.deleteProject(testProject) } throws RuntimeException("Database error")

        // When
        val result = repository.deleteProject("test-id")

        // Then
        assert(result.isFailure) { "Deletion should fail when database operation fails" }
        assert(result.exceptionOrNull()?.message?.contains("Database error") == true) { "Should propagate database error" }
        
        coVerify { mockImageStorageManager.deleteProjectImages(testProject.imagePath, testProject.thumbnailPath) }
        coVerify { mockProjectDao.deleteProject(testProject) }
    }

    @Test
    fun `legacy deleteProject should delete files and database record`() = runTest {
        // Given
        coEvery { mockImageStorageManager.deleteProjectImages(any(), any()) } returns true
        coEvery { mockProjectDao.deleteProject(testProject) } just Runs

        // When
        repository.deleteProject(testProject)

        // Then
        coVerify { mockImageStorageManager.deleteProjectImages(testProject.imagePath, testProject.thumbnailPath) }
        coVerify { mockProjectDao.deleteProject(testProject) }
    }
}
