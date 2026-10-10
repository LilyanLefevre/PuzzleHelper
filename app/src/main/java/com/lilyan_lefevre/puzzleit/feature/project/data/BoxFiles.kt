package com.lilyan_lefevre.puzzleit.feature.project.data

import java.io.File

/** The name the box photo files share, it carries the date the photo was taken: a retaken photo gets a new one. */
val Project.photoId: String
    get() = File(warpedPath.ifEmpty { imagePath }).nameWithoutExtension.removeSuffix("_warped").removeSuffix("_original")

/** When a [photoId] was taken ("" when its name has no date): of two photos of the same box, the newest wins in a sync. */
fun photoStamp(photoId: String): String = Regex("\\d{8}_\\d{6}$").find(photoId)?.value.orEmpty()

/** Deletes the box photo files and the box index cached next to the reference image under its name. */
fun Project.deleteBoxFiles() {
    listOf(imagePath, thumbnailPath).filter { it.isNotEmpty() }.forEach { File(it).delete() }
    File(warpedPath).takeIf { warpedPath.isNotEmpty() }?.let { w ->
        w.parentFile?.listFiles { f -> f.name.startsWith(w.nameWithoutExtension) }?.forEach { it.delete() }
    }
}
