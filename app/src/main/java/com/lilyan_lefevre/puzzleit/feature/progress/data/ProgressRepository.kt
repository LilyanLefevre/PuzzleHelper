package com.lilyan_lefevre.puzzleit.feature.progress.data

import android.content.Context
import com.lilyan_lefevre.puzzleit.feature.account.data.AccountStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** The dated photos of a puzzle's progress. */
@Singleton
class ProgressRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ProgressPhotoDao,
    private val account: AccountStore,
) {
    fun photos(projectId: String): Flow<List<ProgressPhoto>> = dao.observe(projectId)

    /** Moves the camera's temporary file into the puzzle's folder (deleted with the puzzle) and records it. */
    suspend fun add(projectId: String, taken: File): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val target = File(File(context.filesDir, "$projectId/progress").apply { mkdirs() }, "$now.jpg")
        taken.copyTo(target, overwrite = true)
        taken.delete()
        dao.insert(ProgressPhoto(projectId = projectId, createdAt = now, path = target.absolutePath))
    }

    suspend fun delete(photo: ProgressPhoto) = withContext(Dispatchers.IO) {
        dao.delete(photo.id)
        File(photo.path).delete()
        account.markDeleted("photo:${photo.projectId}:${photo.createdAt}")
    }
}
