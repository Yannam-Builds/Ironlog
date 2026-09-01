package com.ironlog.app.data.photos

import com.ironlog.app.data.objectbox.ProgressPhotoEntity
import java.io.File
import java.net.URI
import java.nio.file.Files

/** Backup photo rows contain metadata, not proof that a file on this device belongs to them. */
@Suppress("UNUSED_PARAMETER")
fun importedProgressPhotoReference(rawReference: String): String = ""

data class PhotoCleanupResult(val deletedRows: Int, val failedPhotoIds: List<String>)

/** Only direct children of filesDir/progress_photos are owned. Never delete a gallery source. */
class ProgressPhotoStorage(
    private val filesDir: File,
    private val providerAuthority: String,
    private val deleteFile: (File) -> Boolean = { it.delete() },
) {
    fun ownedFile(reference: String): File? = runCatching {
        val directory = ownedDirectory() ?: return@runCatching null
        val uri = URI(reference)
        if (uri.query != null || uri.fragment != null) return@runCatching null
        val candidate = when (uri.scheme) {
            "file" -> {
                if (!uri.authority.isNullOrEmpty()) return@runCatching null
                File(uri)
            }
            "content" -> {
                if (uri.authority != providerAuthority) return@runCatching null
                val path = uri.path ?: return@runCatching null
                val prefix = "/progress_photos/"
                if (!path.startsWith(prefix)) return@runCatching null
                val name = path.removePrefix(prefix)
                if (name.isBlank() || name == "." || name == ".." || '/' in name || '\\' in name) return@runCatching null
                File(directory, name)
            }
            else -> return@runCatching null
        }
        if (Files.isSymbolicLink(candidate.toPath())) return@runCatching null
        val canonical = candidate.canonicalFile
        if (canonical.parentFile != directory || canonical == directory) return@runCatching null
        // Reject aliases, including symlinks, instead of deleting their referenced image.
        if (candidate.absoluteFile.normalize() != canonical) return@runCatching null
        canonical
    }.getOrNull()

    fun deletePhotos(
        photos: List<ProgressPhotoEntity>,
        removeRow: (Long) -> Boolean,
    ): PhotoCleanupResult {
        var deleted = 0
        val failures = mutableListOf<String>()
        photos.forEach { photo ->
            val success = runCatching {
                check(deleteOwnedReference(photo.fileUri)) { "Photo cleanup failed" }
                check(removeRow(photo.objectBoxId)) { "Photo row removal failed" }
            }.isSuccess
            if (success) deleted++ else failures += photo.uid
        }
        return PhotoCleanupResult(deleted, failures)
    }

    /** Unowned/unavailable references only lose metadata; deletion is never delegated to a provider. */
    fun deleteOwnedReference(reference: String): Boolean {
        val file = ownedFile(reference) ?: return true
        return runCatching {
            if (!file.exists()) true
            else file.isFile && ownedFile(reference) == file && deleteFile(file)
        }.getOrDefault(false)
    }

    /** Recover old copies orphaned by older app versions, but leave unknown files and active captures alone. */
    fun reconcileOrphans(references: List<String>, olderThanMillis: Long): List<String> {
        val directory = ownedDirectory() ?: return emptyList()
        val referenced = references.mapNotNull(::ownedFile).toSet()
        val files = directory.listFiles() ?: return if (directory.exists()) listOf("photo directory") else emptyList()
        return files.filter { file ->
            file.name.matches(GENERATED_PHOTO_NAME) && file.isFile &&
                file.lastModified() < olderThanMillis && file !in referenced &&
                ownedFile(file.toURI().toString()) == file.canonicalFile
        }.filterNot { deleteOwnedReference(it.toURI().toString()) }.map { it.name }
    }

    private fun ownedDirectory(): File? = runCatching {
        val expected = File(filesDir.canonicalFile, "progress_photos")
        expected.takeIf { !Files.isSymbolicLink(it.toPath()) && it.canonicalFile == it.absoluteFile }
    }.getOrNull()

    private companion object {
        val GENERATED_PHOTO_NAME = Regex("(?:photo_(?:[0-9]+|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})|progress_[0-9]{8}_[0-9]{6})\\.jpg")
    }
}
