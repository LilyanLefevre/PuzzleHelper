package com.lilyan_lefevre.puzzleit.feature.storage

import android.content.Context
import com.lilyan_lefevre.puzzleit.shared.utils.ImageUtils
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ImageStorageManagerTest {

    private lateinit var imageStorageManager: ImageStorageManager
    private val mockContext: Context = mockk()
    private val mockImageUtils: ImageUtils = mockk()

    @Before
    fun setup() {
        imageStorageManager = ImageStorageManager(mockContext, mockImageUtils)
    }

    @Test
    fun `deleteProjectImages should return true when both files are deleted successfully`() {
        // Given
        val imagePath = "/path/to/image.jpg"
        val thumbnailPath = "/path/to/thumbnail.jpg"
        every { mockImageUtils.deleteImageFile(mockContext, imagePath) } returns true
        every { mockImageUtils.deleteImageFile(mockContext, thumbnailPath) } returns true

        // When
        val result = imageStorageManager.deleteProjectImages(imagePath, thumbnailPath)

        // Then
        assert(result) { "Should return true when both files are deleted successfully" }
        verify { mockImageUtils.deleteImageFile(mockContext, imagePath) }
        verify { mockImageUtils.deleteImageFile(mockContext, thumbnailPath) }
    }

    @Test
    fun `deleteProjectImages should return false when one file deletion fails`() {
        // Given
        val imagePath = "/path/to/image.jpg"
        val thumbnailPath = "/path/to/thumbnail.jpg"
        every { mockImageUtils.deleteImageFile(mockContext, imagePath) } returns true
        every { mockImageUtils.deleteImageFile(mockContext, thumbnailPath) } returns false

        // When
        val result = imageStorageManager.deleteProjectImages(imagePath, thumbnailPath)

        // Then
        assert(!result) { "Should return false when one file deletion fails" }
        verify { mockImageUtils.deleteImageFile(mockContext, imagePath) }
        verify { mockImageUtils.deleteImageFile(mockContext, thumbnailPath) }
    }

    @Test
    fun `deleteProjectImages should return false when both files deletion fail`() {
        // Given
        val imagePath = "/path/to/image.jpg"
        val thumbnailPath = "/path/to/thumbnail.jpg"
        every { mockImageUtils.deleteImageFile(mockContext, imagePath) } returns false
        every { mockImageUtils.deleteImageFile(mockContext, thumbnailPath) } returns false

        // When
        val result = imageStorageManager.deleteProjectImages(imagePath, thumbnailPath)

        // Then
        assert(!result) { "Should return false when both files deletion fail" }
        verify { mockImageUtils.deleteImageFile(mockContext, imagePath) }
        verify { mockImageUtils.deleteImageFile(mockContext, thumbnailPath) }
    }

    @Test
    fun `getProjectImageSize should return sum of both file sizes`() {
        // Given
        val imagePath = "/path/to/image.jpg"
        val thumbnailPath = "/path/to/thumbnail.jpg"
        every { mockImageUtils.getFileSize(mockContext, imagePath) } returns 1024L
        every { mockImageUtils.getFileSize(mockContext, thumbnailPath) } returns 256L

        // When
        val result = imageStorageManager.getProjectImageSize(imagePath, thumbnailPath)

        // Then
        assert(result == 1280L) { "Should return sum of both file sizes (1024 + 256 = 1280)" }
        verify { mockImageUtils.getFileSize(mockContext, imagePath) }
        verify { mockImageUtils.getFileSize(mockContext, thumbnailPath) }
    }

    @Test
    fun `imagesExist should return true when both files exist`() {
        // Given
        val imagePath = "/path/to/image.jpg"
        val thumbnailPath = "/path/to/thumbnail.jpg"
        mockkStatic("java.io.File")
        val mockImageFile = mockk<java.io.File>()
        val mockThumbnailFile = mockk<java.io.File>()
        
        every { java.io.File(imagePath) } returns mockImageFile
        every { java.io.File(thumbnailPath) } returns mockThumbnailFile
        every { mockImageFile.exists() } returns true
        every { mockThumbnailFile.exists() } returns true

        // When
        val result = imageStorageManager.imagesExist(imagePath, thumbnailPath)

        // Then
        assert(result) { "Should return true when both files exist" }
        verify { mockImageFile.exists() }
        verify { mockThumbnailFile.exists() }
    }

    @Test
    fun `imagesExist should return false when one file does not exist`() {
        // Given
        val imagePath = "/path/to/image.jpg"
        val thumbnailPath = "/path/to/thumbnail.jpg"
        mockkStatic("java.io.File")
        val mockImageFile = mockk<java.io.File>()
        val mockThumbnailFile = mockk<java.io.File>()
        
        every { java.io.File(imagePath) } returns mockImageFile
        every { java.io.File(thumbnailPath) } returns mockThumbnailFile
        every { mockImageFile.exists() } returns true
        every { mockThumbnailFile.exists() } returns false

        // When
        val result = imageStorageManager.imagesExist(imagePath, thumbnailPath)

        // Then
        assert(!result) { "Should return false when one file does not exist" }
        verify { mockImageFile.exists() }
        verify { mockThumbnailFile.exists() }
    }
}
