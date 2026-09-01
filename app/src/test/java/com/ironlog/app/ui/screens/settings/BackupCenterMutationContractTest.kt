package com.ironlog.app.ui.screens.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCenterMutationContractTest {
    private val projectRoot = File(requireNotNull(System.getProperty("user.dir")))

    @Test
    fun `auto backup mutations enter latest serialized non cancellable IO before event returns`() {
        val screen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/settings/BackupCenterScreen.kt",
        ).readText()
        val start = screen.indexOf("fun submitAutoBackupMutation(")
        val end = screen.indexOf("LaunchedEffect(Unit)", start)
        val mutation = screen.substring(start, end)

        val issueToken = mutation.indexOf("AutoBackupMutationCoordinator.issueToken()")
        val launch = mutation.indexOf("scope.launch")
        assertTrue(start >= 0)
        assertTrue(end > start)
        assertTrue(issueToken >= 0)
        assertTrue(launch > issueToken)
        assertTrue(mutation.contains("CoroutineStart.UNDISPATCHED"))
        assertTrue(mutation.contains("withContext(Dispatchers.IO + NonCancellable)"))
        assertTrue(mutation.contains("AutoBackupMutationCoordinator.runIfCurrent(requestToken)"))
        assertTrue(mutation.contains("repo.setBoolean(\"auto_backup_enabled\", enabled)"))
        assertTrue(mutation.contains("BackupScheduler.scheduleDaily"))
        assertTrue(mutation.contains("BackupScheduler.cancel"))
        assertTrue(mutation.contains("WorkoutNotificationBridge.cancelReminderAfterDataMutation"))
        assertTrue(mutation.contains("AutoBackupMutationCoordinator.isCurrent(requestToken)"))
        assertTrue(mutation.contains("restoreAutoBackupUiState"))
    }

    @Test
    fun `toggle and schedule save share the same latest wins submission path`() {
        val screen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/settings/BackupCenterScreen.kt",
        ).readText()
        val toggleStart = screen.indexOf("onCheckedChange = { enabled ->")
        val toggleEnd = screen.indexOf("Row(Modifier.fillMaxWidth()", toggleStart + 1)
        val saveStart = screen.indexOf("Button(onClick = {", toggleEnd)
        val saveEnd = screen.indexOf("Create Encrypted Snapshot", saveStart)

        assertTrue(screen.substring(toggleStart, toggleEnd).contains("submitAutoBackupMutation("))
        assertTrue(screen.substring(saveStart, saveEnd).contains("submitAutoBackupMutation("))
    }

    @Test
    fun `backup scheduler awaits enqueue and cancellation operations`() {
        val scheduler = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/BackupScheduler.kt",
        ).readText()

        assertTrue(scheduler.contains("suspend fun scheduleDaily"))
        assertTrue(scheduler.contains("awaitOperation(workManager.enqueueUniquePeriodicWork"))
        assertTrue(scheduler.contains("suspend fun cancel"))
        assertTrue(scheduler.contains("awaitOperation(workManager.cancelUniqueWork"))
        assertTrue(scheduler.contains("suspendCancellableCoroutine"))
        assertTrue(scheduler.contains("withTimeout"))
    }

    @Test
    fun `encrypted snapshot separates cancellable preparation from non cancellable commit cleanup`() {
        val screen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/settings/BackupCenterScreen.kt",
        ).readText()
        val start = screen.indexOf("val exported = withContext(Dispatchers.IO)")
        val end = screen.indexOf("Create Encrypted Snapshot", start)
        val encryptedBackup = screen.substring(start, end)

        assertTrue(start >= 0)
        assertTrue(end > start)
        assertTrue(encryptedBackup.contains("commitBackupArtifactWithCleanup"))
        assertTrue(encryptedBackup.contains("repo.setString("))
        assertTrue(encryptedBackup.contains("\"last_successful_backup_ms\""))
        assertTrue(
            Regex("WorkoutNotificationBridge\\s*\\.cancelReminderAfterDataMutation")
                .containsMatchIn(encryptedBackup),
        )
        assertTrue(encryptedBackup.contains("catch (cancelled: CancellationException)"))
        assertFalse(encryptedBackup.contains("runCatching {"))
    }
}
