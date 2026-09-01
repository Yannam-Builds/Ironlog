package com.ironlog.app.data.photos

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.UUID

class LocalRestoreSnapshotStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val authority = "synthetic.fileprovider"

    @Test fun `checkpoint restores owned images after replacement and orphan cleanup remove originals`() {
        val files = temporary.newFolder("files")
        val recovery = File(files, "restore-recovery")
        val original = photo(files, "photo_123.jpg", "synthetic image bytes")
        val payload = payload("photo-A" to original.toURI().toString())
        val store = LocalRestoreSnapshotStore(files, recovery, authority)
        val checkpoint = store.save(payload)
        assertFalse(payload.has(LocalRestoreSnapshotStore.PHOTO_MANIFEST_KEY))
        original.setLastModified(1L)
        ProgressPhotoStorage(files, authority).reconcileOrphans(emptyList(), 2L)
        assertFalse(original.exists())

        val references = store.restorePhotoReferences(checkpoint, checkpoint.readText())
        val restored = File(URI(references.getValue("photo-A")))
        assertEquals("synthetic image bytes", restored.readText())
        assertNotEquals(original.absolutePath, restored.absolutePath)
        assertEquals(restored.canonicalFile, ProgressPhotoStorage(files, authority).ownedFile(references.getValue("photo-A")))
        assertTrue(checkpoint.isFile)
        assertTrue(File(recovery, checkpoint.nameWithoutExtension + "_photos").isDirectory)
    }

    @Test fun `saving checkpoint never copies unowned gallery or arbitrary file references`() {
        val files = temporary.newFolder("files")
        val outside = File(files, "private-backup.zip").apply { writeText("synthetic unrelated data") }
        val store = LocalRestoreSnapshotStore(files, File(files, "restore-recovery"), authority)
        val checkpoint = store.save(payload("outside" to outside.toURI().toString(), "gallery" to "content://gallery/123", "empty" to ""))
        assertEquals(0, JSONObject(checkpoint.readText()).getJSONObject(LocalRestoreSnapshotStore.PHOTO_MANIFEST_KEY).length())
        assertTrue(store.restorePhotoReferences(checkpoint, checkpoint.readText()).isEmpty())
        assertEquals("synthetic unrelated data", outside.readText())
    }

    @Test fun `missing owned image prevents publishing a misleading checkpoint`() {
        val files = temporary.newFolder("files")
        val missing = File(File(files, "progress_photos").apply { mkdirs() }, "photo_123.jpg")
        val recovery = File(files, "restore-recovery")
        val store = LocalRestoreSnapshotStore(files, recovery, authority)
        assertThrows(Exception::class.java) { store.save(payload("missing" to missing.toURI().toString())) }
        assertTrue(recovery.listFiles().orEmpty().none { it.extension == "json" })
        assertTrue(recovery.listFiles().orEmpty().isEmpty())
    }

    @Test fun `external checkpoint is rejected even when bytes and filename match`() {
        val files = temporary.newFolder("files")
        val recovery = File(files, "restore-recovery")
        val store = LocalRestoreSnapshotStore(files, recovery, authority)
        val checkpoint = store.save(payload())
        val external = File(temporary.newFolder("outside"), checkpoint.name).apply { writeText(checkpoint.readText()) }
        assertThrows(Exception::class.java) { store.restorePhotoReferences(external, external.readText()) }
        assertTrue(external.exists())
    }

    @Test fun `altered expected payload is rejected before creating restored images`() {
        val files = temporary.newFolder("files")
        val original = photo(files, "photo_123.jpg", "synthetic image")
        val store = LocalRestoreSnapshotStore(files, File(files, "restore-recovery"), authority)
        val checkpoint = store.save(payload("a" to original.toURI().toString()))
        val before = File(files, "progress_photos").listFiles()!!.map { it.name }.toSet()
        assertThrows(Exception::class.java) { store.restorePhotoReferences(checkpoint, checkpoint.readText() + " ") }
        assertEquals(before, File(files, "progress_photos").listFiles()!!.map { it.name }.toSet())
    }

    @Test fun `manifest traversal is rejected before touching an outside image`() {
        val files = temporary.newFolder("files")
        val original = photo(files, "photo_123.jpg", "synthetic image")
        val recovery = File(files, "restore-recovery")
        val store = LocalRestoreSnapshotStore(files, recovery, authority)
        val checkpoint = store.save(payload("a" to original.toURI().toString()))
        val modified = JSONObject(checkpoint.readText())
        modified.getJSONObject(LocalRestoreSnapshotStore.PHOTO_MANIFEST_KEY).put("a", "../outside.jpg")
        checkpoint.writeText(modified.toString())
        assertThrows(Exception::class.java) { store.restorePhotoReferences(checkpoint, checkpoint.readText()) }
        assertEquals(1, File(files, "progress_photos").listFiles()!!.size)
    }

    @Test fun `all manifest sources are validated before first recovery copy`() {
        val files = temporary.newFolder("files")
        val first = photo(files, "photo_123.jpg", "image A")
        val second = photo(files, "photo_456.jpg", "image B")
        val recovery = File(files, "restore-recovery")
        val store = LocalRestoreSnapshotStore(files, recovery, authority)
        val checkpoint = store.save(payload("a" to first.toURI().toString(), "b" to second.toURI().toString()))
        val manifest = JSONObject(checkpoint.readText()).getJSONObject(LocalRestoreSnapshotStore.PHOTO_MANIFEST_KEY)
        File(File(recovery, checkpoint.nameWithoutExtension + "_photos"), manifest.getString("b")).delete()
        assertThrows(Exception::class.java) { store.restorePhotoReferences(checkpoint, checkpoint.readText()) }
        assertEquals(setOf(first.name, second.name), File(files, "progress_photos").listFiles()!!.map { it.name }.toSet())
    }

    @Test fun `symlink checkpoint cannot enter trusted local recovery path`() {
        val files = temporary.newFolder("files")
        val recovery = File(files, "restore-recovery")
        val store = LocalRestoreSnapshotStore(files, recovery, authority)
        val checkpoint = store.save(payload())
        val link = File(recovery, "ironlog_backup_before_restore_${UUID.randomUUID()}.json")
        symlink(link, checkpoint)
        assertThrows(Exception::class.java) { store.restorePhotoReferences(link, checkpoint.readText()) }
    }

    @Test fun `symlink manifest image is rejected and its outside target survives`() {
        val files = temporary.newFolder("files")
        val original = photo(files, "photo_123.jpg", "image A")
        val recovery = File(files, "restore-recovery")
        val store = LocalRestoreSnapshotStore(files, recovery, authority)
        val checkpoint = store.save(payload("a" to original.toURI().toString()))
        val imageName = JSONObject(checkpoint.readText()).getJSONObject(LocalRestoreSnapshotStore.PHOTO_MANIFEST_KEY).getString("a")
        val savedImage = File(File(recovery, checkpoint.nameWithoutExtension + "_photos"), imageName)
        savedImage.delete()
        symlink(savedImage, original)
        assertThrows(Exception::class.java) { store.restorePhotoReferences(checkpoint, checkpoint.readText()) }
        assertEquals("image A", original.readText())
    }

    @Test fun `symlink snapshot directory is rejected without restoring images`() {
        val files = temporary.newFolder("files")
        val original = photo(files, "photo_123.jpg", "image A")
        val recovery = File(files, "restore-recovery")
        val store = LocalRestoreSnapshotStore(files, recovery, authority)
        val checkpoint = store.save(payload("a" to original.toURI().toString()))
        val snapshotDir = File(recovery, checkpoint.nameWithoutExtension + "_photos")
        val external = File(temporary.newFolder("external"), snapshotDir.name)
        Files.move(snapshotDir.toPath(), external.toPath())
        symlink(snapshotDir, external)
        assertThrows(Exception::class.java) { store.restorePhotoReferences(checkpoint, checkpoint.readText()) }
        assertEquals(1, File(files, "progress_photos").listFiles()!!.size)
    }

    @Test fun `failed checkpoint copy removes partial images and never publishes JSON`() {
        val files = temporary.newFolder("files")
        val first = photo(files, "photo_123.jpg", "image A")
        val second = photo(files, "photo_456.jpg", "image B")
        val recovery = File(files, "restore-recovery")
        var copies = 0
        val store = LocalRestoreSnapshotStore(files, recovery, authority, copyImage = { source, destination ->
            source.copyTo(destination)
            if (++copies == 2) error("Injected failure after partial second image")
        })
        assertThrows(Exception::class.java) { store.save(payload("a" to first.toURI().toString(), "b" to second.toURI().toString())) }
        assertTrue(recovery.listFiles().orEmpty().isEmpty())
        assertEquals("image A", first.readText())
        assertEquals("image B", second.readText())
    }

    @Test fun `failed recovery copy removes every new image and keeps existing originals`() {
        val files = temporary.newFolder("files")
        val first = photo(files, "photo_123.jpg", "image A")
        val second = photo(files, "photo_456.jpg", "image B")
        val recovery = File(files, "restore-recovery")
        val checkpoint = LocalRestoreSnapshotStore(files, recovery, authority).save(payload("a" to first.toURI().toString(), "b" to second.toURI().toString()))
        var copies = 0
        val store = LocalRestoreSnapshotStore(files, recovery, authority, copyImage = { source, destination ->
            source.copyTo(destination)
            if (++copies == 2) error("Injected failure after partial second image")
        })
        assertThrows(Exception::class.java) { store.restorePhotoReferences(checkpoint, checkpoint.readText()) }
        assertEquals(setOf(first.name, second.name), File(files, "progress_photos").listFiles()!!.map { it.name }.toSet())
        assertTrue(checkpoint.exists())
    }

    @Test fun `discard after transaction failure removes only copies issued by this store`() {
        val files = temporary.newFolder("files")
        val original = photo(files, "photo_123.jpg", "image A")
        val store = LocalRestoreSnapshotStore(files, File(files, "restore-recovery"), authority)
        val checkpoint = store.save(payload("a" to original.toURI().toString()))
        val restored = store.restorePhotoReferences(checkpoint, checkpoint.readText())
        store.discardRestoredPhotos(restored)
        assertFalse(File(URI(restored.getValue("a"))).exists())
        assertTrue(original.exists())
        assertTrue(checkpoint.exists())
    }

    @Test fun `discard rejects arbitrary owned original before deleting anything`() {
        val files = temporary.newFolder("files")
        val original = photo(files, "photo_123.jpg", "image A")
        val store = LocalRestoreSnapshotStore(files, File(files, "restore-recovery"), authority)
        val checkpoint = store.save(payload("a" to original.toURI().toString()))
        val restored = store.restorePhotoReferences(checkpoint, checkpoint.readText())
        assertThrows(Exception::class.java) { store.discardRestoredPhotos(restored + ("original" to original.toURI().toString())) }
        assertTrue(original.exists())
        assertTrue(File(URI(restored.getValue("a"))).exists())
    }

    @Test fun `trusted checkpoint predicate accepts only private direct checkpoints`() {
        val files = temporary.newFolder("files")
        val store = LocalRestoreSnapshotStore(files, File(files, "restore-recovery"), authority)
        val checkpoint = store.save(payload())
        assertTrue(store.isTrustedCheckpoint(checkpoint))
        val external = File(temporary.newFolder("external"), checkpoint.name).apply { writeText(checkpoint.readText()) }
        assertFalse(store.isTrustedCheckpoint(external))
        assertFalse(store.isTrustedCheckpoint(File(checkpoint.parentFile, "arbitrary.json").apply { writeText("{}") }))
    }

    private fun payload(vararg photos: Pair<String, String>) = JSONObject().put("data", JSONObject().put("progress_photos", JSONArray().apply {
        photos.forEach { (uid, uri) -> put(JSONObject().put("id", uid).put("file_uri", uri)) }
    }))

    private fun photo(files: File, name: String, bytes: String) =
        File(File(files, "progress_photos").apply { mkdirs() }, name).apply { writeText(bytes) }

    private fun symlink(link: File, target: File) {
        try { Files.createSymbolicLink(link.toPath(), target.toPath()) }
        catch (exception: Exception) { assumeNoException("Host must support symlinks", exception) }
    }
}
