package com.ironlog.app.services

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.Calendar
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

object BackupScheduler {
    private const val BACKUP_WORK_NAME = "ironlog_auto_backup"

    suspend fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val req = PeriodicWorkRequestBuilder<BackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay(hour, minute), TimeUnit.MILLISECONDS)
            .build()
        val workManager = WorkManager.getInstance(context)
        awaitOperation(workManager.enqueueUniquePeriodicWork(
            BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        ))
    }

    suspend fun cancel(context: Context) {
        val workManager = WorkManager.getInstance(context)
        awaitOperation(workManager.cancelUniqueWork(BACKUP_WORK_NAME))
    }

    private fun initialDelay(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (next.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)
    }

    private suspend fun awaitOperation(operation: Operation) {
        withTimeout(WORK_MANAGER_OPERATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val future = operation.result
                future.addListener(
                    {
                        if (continuation.isActive) {
                            continuation.resumeWith(runCatching {
                                future.get()
                                Unit
                            })
                        }
                    },
                    DIRECT_EXECUTOR,
                )
                continuation.invokeOnCancellation { future.cancel(true) }
            }
        }
    }

    private val DIRECT_EXECUTOR = Executor { command -> command.run() }
    private const val WORK_MANAGER_OPERATION_TIMEOUT_MS = 10_000L
}
