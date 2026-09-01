package com.ironlog.app.data.photos

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/** Private, device-local recovery checkpoints. Ordinary imported JSON must never select this path. */
class LocalRestoreSnapshotStore(
    private val filesDir: File,
    private val recoveryDirectory: File,
    private val providerAuthority: String,
    private val copyImage: (File, File) -> Unit = ::copyImageDurably,
) {
    // Cleanup authority is issued only by a successful restore on this same store instance.
    private val issuedRestoredFiles = mutableMapOf<String, File>()

    fun isTrustedCheckpoint(file: File): Boolean = runCatching {
        check(CHECKPOINT_NAME.matches(file.name))
        checkedChild(file, checkedDirectory(recoveryDirectory, create = false), mustExist = true)
        true
    }.getOrDefault(false)

    /** Rolls back newly issued image copies after the caller's database transaction fails. */
    @Synchronized
    fun discardRestoredPhotos(references: Map<String, String>) {
        val storage = ProgressPhotoStorage(filesDir, providerAuthority)
        val targets = references.values.toSet().map { reference ->
            val issued = issuedRestoredFiles[reference] ?: error("This image was not created by this recovery operation.")
            check(storage.ownedFile(reference) == issued) { "Recovered image ownership changed; cleanup stopped." }
            reference
        }
        var cleanupFailure: IllegalStateException? = null
        targets.forEach { reference ->
            if (!storage.deleteOwnedReference(reference)) {
                val failure = IllegalStateException("A newly restored image could not be removed after recovery failed.")
                if (cleanupFailure == null) cleanupFailure = failure else cleanupFailure!!.addSuppressed(failure)
            }
        }
        cleanupFailure?.let { throw it }
    }

    /** Publishes JSON only after every owned image has a durable independent copy. */
    fun save(payload: JSONObject): File {
        val root = checkedDirectory(recoveryDirectory, create = true)
        check(!root.toPath().startsWith(File(filesDir.canonicalFile, "progress_photos").toPath())) {
            "Recovery checkpoints must be outside the progress-photo directory."
        }
        val snapshot = JSONObject(payload.toString())
        val storage = ProgressPhotoStorage(filesDir, providerAuthority)
        val sources = photoReferences(snapshot).mapNotNull { (uid, reference) ->
            val source = storage.ownedFile(reference) ?: return@mapNotNull null
            check(source.isFile && source.length() > 0L && Files.isReadable(source.toPath())) {
                "An owned progress image is missing or unreadable. Nothing was replaced."
            }
            uid to source
        }
        val stem = "ironlog_backup_before_restore_${UUID.randomUUID()}"
        val checkpoint = File(root, "$stem.json")
        val temporaryJson = File(root, "$stem.json.part")
        val images = File(root, "${stem}_photos")
        val created = mutableListOf<File>()
        var createdImages = false
        var published = false
        try {
            check(!checkpoint.exists() && !temporaryJson.exists()) { "Checkpoint name already exists." }
            Files.createDirectory(images.toPath())
            createdImages = true
            val imageDirectory = checkedDirectory(images, create = false)
            val manifest = JSONObject()
            sources.forEach { (uid, source) ->
                check(storage.ownedFile(source.toURI().toString()) == source) { "Photo ownership changed while creating a checkpoint." }
                val destination = File(imageDirectory, "photo_${UUID.randomUUID()}.jpg")
                checkedChild(destination, imageDirectory, mustExist = false)
                created += destination
                copyImage(source, destination)
                checkedChild(destination, imageDirectory, mustExist = true)
                manifest.put(uid, destination.name)
            }
            snapshot.put(PHOTO_MANIFEST_KEY, manifest)
            created += temporaryJson
            writeDurably(temporaryJson, snapshot.toString().toByteArray(Charsets.UTF_8))
            try {
                Files.move(temporaryJson.toPath(), checkpoint.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (_: AtomicMoveNotSupportedException) {
                // Same-directory rename still never exposes a half-written JSON file.
                Files.move(temporaryJson.toPath(), checkpoint.toPath())
            }
            published = true
            return checkedChild(checkpoint, root, mustExist = true)
        } catch (failure: Throwable) {
            cleanupCreated(created, root, images.takeIf { createdImages }, failure)
            // A final verification failure must not leave a published checkpoint behind.
            if (published && checkpoint.parentFile == root && !Files.isSymbolicLink(root.toPath())) {
                runCatching { Files.deleteIfExists(checkpoint.toPath()) }.exceptionOrNull()?.let(failure::addSuppressed)
            }
            throw failure
        }
    }

    /**
     * Caller must supply a trusted checkpoint selected from the app's private recovery directory.
     * The exact previewed bytes and every manifest source are checked before any image is restored.
     */
    @Synchronized
    fun restorePhotoReferences(checkpoint: File, expectedPayload: String): Map<String, String> {
        val root = checkedDirectory(recoveryDirectory, create = false)
        check(CHECKPOINT_NAME.matches(checkpoint.name)) { "Not a local recovery checkpoint." }
        val sourceCheckpoint = checkedChild(checkpoint, root, mustExist = true)
        val expectedBytes = expectedPayload.toByteArray(Charsets.UTF_8)
        check(sourceCheckpoint.length() == expectedBytes.size.toLong() && sourceCheckpoint.readBytes().contentEquals(expectedBytes)) {
            "Recovery checkpoint changed after preview. Please review it again."
        }
        val payload = JSONObject(expectedPayload)
        val references = photoReferences(payload)
        val manifest = payload.opt(PHOTO_MANIFEST_KEY)
        check(manifest is JSONObject) { "This checkpoint has no local image snapshot. Images cannot be fully recovered." }
        val imageDirectory = checkedDirectory(File(root, sourceCheckpoint.nameWithoutExtension + "_photos"), create = false)
        check(imageDirectory.parentFile == root) { "Invalid checkpoint image directory." }
        val storage = ProgressPhotoStorage(filesDir, providerAuthority)
        references.forEach { (uid, reference) ->
            check(storage.ownedFile(reference) == null || manifest.has(uid)) { "The checkpoint is missing an owned image entry." }
        }
        val sources = manifest.keys().asSequence().map { uid ->
            check(uid.isNotBlank() && references.containsKey(uid)) { "Unknown photo in checkpoint manifest." }
            val name = manifest.get(uid)
            check(name is String && IMAGE_NAME.matches(name)) { "Unsafe checkpoint image filename." }
            uid to checkedChild(File(imageDirectory, name), imageDirectory, mustExist = true)
        }.toList()
        if (sources.isEmpty()) return emptyMap()

        val destinationDirectory = checkedDirectory(File(filesDir.canonicalFile, "progress_photos"), create = true)
        val created = mutableListOf<File>()
        try {
            val restored = linkedMapOf<String, String>()
            sources.forEach { (uid, source) ->
                checkedChild(source, imageDirectory, mustExist = true)
                val destination = File(destinationDirectory, "photo_${UUID.randomUUID()}.jpg")
                check(storage.ownedFile(destination.toURI().toString()) == destination) { "Cannot safely restore images on this device." }
                created += destination
                copyImage(source, destination)
                checkedChild(destination, destinationDirectory, mustExist = true)
                restored[uid] = destination.toURI().toString()
            }
            created.forEach { issuedRestoredFiles[it.toURI().toString()] = it }
            return restored
        } catch (failure: Throwable) {
            cleanupCreated(created, destinationDirectory, null, failure)
            throw failure
        }
    }

    private fun photoReferences(payload: JSONObject): Map<String, String> {
        val data = payload.getJSONObject("data")
        val raw = data.opt("progress_photos") ?: return emptyMap()
        check(raw is JSONArray) { "Invalid progress-photo metadata." }
        val result = linkedMapOf<String, String>()
        for (index in 0 until raw.length()) {
            val photo = raw.getJSONObject(index)
            val uid = photo.getString("id")
            check(uid.isNotBlank() && uid !in result) { "Invalid or duplicate progress-photo ID." }
            val reference = photo.opt("file_uri")
            check(reference == null || reference == JSONObject.NULL || reference is String) { "Invalid progress-photo reference." }
            result[uid] = reference as? String ?: ""
        }
        return result
    }

    private fun checkedDirectory(directory: File, create: Boolean): File {
        check(!Files.isSymbolicLink(directory.toPath())) { "Symbolic links are not valid snapshot directories." }
        val canonical = directory.canonicalFile
        if (create) Files.createDirectories(canonical.toPath())
        check(canonical.isDirectory && !Files.isSymbolicLink(canonical.toPath()) && canonical.toPath().toRealPath() == canonical.toPath()) {
            "Recovery image directory is missing or unsafe."
        }
        return canonical
    }

    private fun checkedChild(file: File, directory: File, mustExist: Boolean): File {
        check(!Files.isSymbolicLink(directory.toPath()) && !Files.isSymbolicLink(file.toPath())) { "Symbolic links are not valid snapshot files." }
        val canonical = file.canonicalFile
        check(file.absoluteFile.normalize().parentFile == directory && canonical.parentFile == directory) { "Snapshot file is outside its private directory." }
        if (mustExist) check(canonical.isFile && canonical.length() > 0L && Files.isReadable(canonical.toPath()) && canonical.toPath().toRealPath() == canonical.toPath()) {
            "A recovery snapshot file is missing or unreadable."
        }
        else check(!canonical.exists()) { "Snapshot destination already exists." }
        return canonical
    }

    /** Only individual files created by this operation are removed; no recursive cleanup. */
    private fun cleanupCreated(created: List<File>, root: File, imageDirectory: File?, failure: Throwable) {
        created.asReversed().forEach { file ->
            runCatching {
                check(checkedDirectory(root, create = false) == root) { "Snapshot cleanup root changed." }
                val parent = file.absoluteFile.parentFile
                check(parent == root || parent == imageDirectory) { "Unsafe snapshot cleanup path." }
                check(parent != null && !Files.isSymbolicLink(parent.toPath())) { "Snapshot cleanup directory changed." }
                Files.deleteIfExists(file.toPath())
            }.exceptionOrNull()?.let(failure::addSuppressed)
        }
        imageDirectory?.let { directory ->
            runCatching {
                check(checkedDirectory(root, create = false) == root) { "Snapshot cleanup root changed." }
                check(directory.parentFile == root && !Files.isSymbolicLink(root.toPath()))
                Files.deleteIfExists(directory.toPath())
            }.exceptionOrNull()?.let(failure::addSuppressed)
        }
    }

    companion object {
        const val PHOTO_MANIFEST_KEY = "_ironlog_local_photo_manifest"
        private const val UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        private val CHECKPOINT_NAME = Regex("ironlog_backup_before_restore_$UUID_PATTERN\\.json")
        private val IMAGE_NAME = Regex("photo_$UUID_PATTERN\\.jpg")

        private fun copyImageDurably(source: File, destination: File) {
            FileChannel.open(source.toPath(), StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { input ->
                FileChannel.open(destination.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { output ->
                    val buffer = ByteBuffer.allocate(64 * 1024)
                    while (input.read(buffer) != -1) {
                        buffer.flip()
                        while (buffer.hasRemaining()) output.write(buffer)
                        buffer.clear()
                    }
                    output.force(true)
                }
            }
        }

        private fun writeDurably(file: File, bytes: ByteArray) {
            FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW, LinkOption.NOFOLLOW_LINKS).use { output ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) output.write(buffer)
                output.force(true)
            }
        }
    }
}
