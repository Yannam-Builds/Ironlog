package com.ironlog.app.data.photos

import com.ironlog.app.data.objectbox.ProgressPhotoEntity
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class ProgressPhotoStorageTest {
    @get:Rule val temporary = TemporaryFolder()
    private val authority = "com.ironlogpro.app.fileprovider"

    @Test fun `owned file and exact provider photo path resolve inside private directory`() {
        val files = temporary.newFolder("files")
        val photo = photoFile(files, "photo_123.jpg")
        val storage = ProgressPhotoStorage(files, authority)
        assertEquals(photo.canonicalFile, storage.ownedFile(photo.toURI().toString()))
        assertEquals(photo.canonicalFile, storage.ownedFile("content://$authority/progress_photos/${photo.name}"))
    }

    @Test fun `external file gallery authority wrong provider root and traversal never resolve as owned`() {
        val files = temporary.newFolder("files")
        val outside = File(files, "backup.zip").apply { writeText("disposable backup") }
        photoFile(files, "photo_123.jpg")
        val storage = ProgressPhotoStorage(files, authority)
        listOf(
            outside.toURI().toString(),
            "content://gallery/progress_photos/photo_123.jpg",
            "content://$authority/exports/photo_123.jpg",
            "content://$authority/progress_photos/../backup.zip",
            "content://$authority/progress_photos/%2e%2e%2fbackup.zip",
            "content://$authority/progress_photos/photo_123.jpg?other=1",
            "not a uri",
        ).forEach { assertNull(it, storage.ownedFile(it)) }
        assertTrue(outside.exists())
    }

    @Test fun `symlink file escaping photo directory never resolves or deletes target`() {
        val files = temporary.newFolder("files")
        val outside = File(files, "backup.zip").apply { writeText("disposable backup") }
        val dir = File(files, "progress_photos").apply { mkdirs() }
        val link = File(dir, "photo_123.jpg")
        try { Files.createSymbolicLink(link.toPath(), outside.toPath()) }
        catch (exception: Exception) { assumeNoException("Host must support symlinks", exception) }
        val storage = ProgressPhotoStorage(files, authority)
        assertNull(storage.ownedFile(link.toURI().toString()))
        storage.deletePhotos(listOf(row(1, link.toURI().toString()))) { true }
        assertTrue(outside.exists())
    }

    @Test fun `symlink photo directory cannot redefine the ownership boundary`() {
        val files = temporary.newFolder("files")
        val outside = temporary.newFolder("outside")
        val target = File(outside, "photo_123.jpg").apply { writeText("disposable image") }
        try { Files.createSymbolicLink(File(files, "progress_photos").toPath(), outside.toPath()) }
        catch (exception: Exception) { assumeNoException("Host must support symlinks", exception) }
        val storage = ProgressPhotoStorage(files, authority)
        assertNull(storage.ownedFile("content://$authority/progress_photos/photo_123.jpg"))
        storage.deletePhotos(listOf(row(1, target.toURI().toString()))) { true }
        assertTrue(target.exists())
    }

    @Test fun `single delete removes owned copy before removing row`() {
        val files = temporary.newFolder("files")
        val photo = photoFile(files, "photo_123.jpg")
        val removed = mutableListOf<Long>()
        val result = ProgressPhotoStorage(files, authority).deletePhotos(listOf(row(1, photo.toURI().toString()))) {
            assertFalse(photo.exists())
            removed.add(it)
        }
        assertEquals(listOf(1L), removed)
        assertEquals(1, result.deletedRows)
        assertTrue(result.failedPhotoIds.isEmpty())
    }

    @Test fun `bulk delete never touches gallery originals or arbitrary file references`() {
        val files = temporary.newFolder("files")
        val photo = photoFile(files, "photo_123.jpg")
        val outside = File(files, "backup.zip").apply { writeText("disposable backup") }
        val result = ProgressPhotoStorage(files, authority).deletePhotos(listOf(
            row(1, photo.toURI().toString()), row(2, outside.toURI().toString()),
            row(3, "content://gallery/progress_photos/photo_123.jpg"),
        )) { true }
        assertFalse(photo.exists())
        assertTrue(outside.exists())
        assertEquals(3, result.deletedRows)
        assertTrue(result.failedPhotoIds.isEmpty())
    }

    @Test fun `failed file cleanup keeps row available for retry and reports failure`() {
        val files = temporary.newFolder("files")
        val photo = photoFile(files, "photo_123.jpg")
        var removed = false
        val storage = ProgressPhotoStorage(files, authority, deleteFile = { false })
        val result = storage.deletePhotos(listOf(row(1, photo.toURI().toString()))) { removed = true; true }
        assertFalse(removed)
        assertTrue(photo.exists())
        assertEquals(listOf("photo-1"), result.failedPhotoIds)
        assertEquals(0, result.deletedRows)
    }

    @Test fun `row failure is reported and missing owned file remains retryable`() {
        val files = temporary.newFolder("files")
        val photo = photoFile(files, "photo_123.jpg")
        val storage = ProgressPhotoStorage(files, authority)
        val rows = listOf(row(1, photo.toURI().toString()))
        val first = storage.deletePhotos(rows) { throw IllegalStateException("test write failure") }
        assertEquals(listOf("photo-1"), first.failedPhotoIds)
        assertEquals(1, storage.deletePhotos(rows) { true }.deletedRows)
    }

    @Test fun `metadata import never grants ownership even for an apparent local URI`() {
        listOf("file:///data/user/0/app/files/progress_photos/photo_123.jpg",
            "content://$authority/progress_photos/photo_123.jpg", "content://gallery/123", "")
            .forEach { assertEquals("", importedProgressPhotoReference(it)) }
    }

    @Test fun `orphan reconciliation only removes old unreferenced generated direct child photos`() {
        val files = temporary.newFolder("files")
        val old = photoFile(files, "photo_123.jpg").apply { setLastModified(1L) }
        val used = photoFile(files, "photo_456.jpg").apply { setLastModified(1L) }
        val recent = photoFile(files, "progress_20260901_120000.jpg")
        val unrelated = photoFile(files, "unrelated.jpg").apply { setLastModified(1L) }
        val storage = ProgressPhotoStorage(files, authority)
        val failures = storage.reconcileOrphans(listOf(used.toURI().toString()), olderThanMillis = 2L)
        assertTrue(failures.isEmpty())
        assertFalse(old.exists())
        assertTrue(used.exists())
        assertTrue(recent.exists())
        assertTrue(unrelated.exists())
    }

    @Test fun `orphan reconciliation recognizes UUID restored copies but rejects lookalike names`() {
        val files = temporary.newFolder("files")
        val orphan = photoFile(files, "photo_12345678-1234-1234-1234-123456789abc.jpg").apply { setLastModified(1L) }
        val used = photoFile(files, "photo_87654321-1234-1234-1234-123456789abc.jpg").apply { setLastModified(1L) }
        val unknown = photoFile(files, "photo_not-a-uuid.jpg").apply { setLastModified(1L) }
        ProgressPhotoStorage(files, authority).reconcileOrphans(listOf(used.toURI().toString()), 2L)
        assertFalse(orphan.exists())
        assertTrue(used.exists())
        assertTrue(unknown.exists())
    }

    private fun photoFile(files: File, name: String): File =
        File(File(files, "progress_photos").apply { mkdirs() }, name).apply { writeText("disposable image") }

    private fun row(id: Long, uri: String) = ProgressPhotoEntity().apply {
        objectBoxId = id; uid = "photo-$id"; fileUri = uri
    }
}
