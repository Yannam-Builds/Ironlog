package com.ironlog.app.services

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutNotificationLifecycleContractTest {
    private val projectRoot = File(requireNotNull(System.getProperty("user.dir")))

    @Test
    fun `foreground service is session bound non sticky and removes its notification`() {
        val source = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/WorkoutForegroundService.kt"
        ).readText()

        assertTrue(source.contains("START_NOT_STICKY"))
        assertTrue(source.contains("active_workout_id"))
        assertTrue(source.contains("STOP_FOREGROUND_REMOVE"))
        assertTrue(source.contains("notificationManager.cancel(NOTIFICATION_ID)"))
        assertTrue(source.contains("EXTRA_WORKOUT_ID"))
        assertTrue(source.contains("setChronometerCountDown(restActive)"))
        assertTrue(source.contains("sessionOwner.compareAndSet(owner, null)"))
        assertTrue(source.contains("WorkoutTimerClock"))
        assertTrue(source.contains("PowerManager.PARTIAL_WAKE_LOCK"))
        assertTrue(source.contains("PendingIntent.getBroadcast"))
        assertFalse(source.contains("return START_STICKY"))
    }

    @Test
    fun `terminal stop falls back to orphan cleanup when the live owner cannot handle it`() {
        val source = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/WorkoutForegroundService.kt"
        ).readText()

        assertTrue(source.contains("if (service.stopIfCurrentSession(workoutId)) return"))
        assertTrue(source.contains("private fun stopIfCurrentSession(workoutId: String): Boolean"))
    }

    @Test
    fun `rest completion tap identity is bound to the originating workout`() {
        val source = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationServices.kt"
        ).readText()

        assertTrue(source.contains("Uri.parse(\"ironlog://workout/${'$'}{Uri.encode(workoutId)}/rest-complete\")"))
        assertTrue(source.contains("restCompleteRequestCode(workoutId)"))
    }

    @Test
    fun `first set service startup is awaited inside the serialized mutation`() {
        val source = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt"
        ).readText()
        val start = source.indexOf("private suspend fun startTimerOnFirstSet")
        val end = source.indexOf("private fun syncForegroundNotification", start)

        assertTrue(start >= 0)
        assertTrue(end > start)
        assertFalse(source.substring(start, end).contains("viewModelScope.launch"))
    }

    @Test
    fun `manifest uses the declared special use type and no data sync workaround`() {
        val manifest = projectRoot.resolve("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))
        assertTrue(manifest.contains("android:foregroundServiceType=\"specialUse\""))
        assertTrue(manifest.contains("android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"))
        assertFalse(manifest.contains("android:foregroundServiceType=\"dataSync\""))
        assertFalse(manifest.contains("android.permission.USE_FULL_SCREEN_INTENT"))
        assertTrue(manifest.contains("android.permission.WAKE_LOCK"))
        assertTrue(manifest.contains("android:name=\".services.WorkoutNotificationActionReceiver\""))
    }

    @Test
    fun `terminal workout writes and notification cleanup are non cancellable`() {
        val terminal = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/state/WorkoutTerminalCleanup.kt"
        ).readText()
        val activeWorkout = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt"
        ).readText()
        val navigator = projectRoot.resolve(
            "src/main/java/com/ironlog/app/navigation/AppNavigator.kt"
        ).readText()

        assertTrue(terminal.contains("withContext(NonCancellable)"))
        assertTrue(activeWorkout.contains("commitWorkoutTerminalMutation { workoutRepo.abandonWorkout(id) }"))
        assertTrue(navigator.contains("commitWorkoutTerminalMutation {"))
        assertTrue(navigator.contains("workoutRepo.abandonWorkout(requestedId)"))
    }

    @Test
    fun `notification actions become durable before the private entry opens the launcher`() {
        val activity = projectRoot.resolve("src/main/java/com/ironlog/app/MainActivity.kt").readText()
        val entry = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationEntryActivity.kt"
        ).readText()
        val screen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt"
        ).readText()

        val persist = entry.indexOf("persistNotificationRequest(intent)")
        val openLauncher = entry.indexOf("startActivity(Intent(this, MainActivity::class.java)")
        assertTrue(persist >= 0)
        assertTrue(openLauncher > persist)
        assertTrue(entry.contains("WorkoutNotificationActionInbox.commitNotificationTap"))
        assertTrue(screen.contains("LaunchedEffect(activeWorkoutId)"))
        assertTrue(screen.contains("consumeForSession(sessionId)"))
        assertTrue(activity.contains("override fun onResume()"))
        assertTrue(activity.contains("NotificationCoordinator.reconcile(this@MainActivity)"))
        assertFalse(activity.contains("WorkoutNotificationActionInbox.commitNotificationTap"))
    }

    @Test
    fun `ordinary activity resume preserves healthy reminder work`() {
        val activity = projectRoot.resolve("src/main/java/com/ironlog/app/MainActivity.kt").readText()
        val start = activity.indexOf("override fun onResume()")
        val end = activity.indexOf("private fun requestMaxRefreshRate", start)

        assertTrue(start >= 0)
        assertTrue(end > start)
        assertTrue(activity.substring(start, end).contains("NotificationCoordinator.reconcile(this@MainActivity)"))
        assertFalse(activity.substring(start, end).contains("forceReschedule = true"))
        assertTrue(activity.substring(start, end).contains("acknowledgeAppForeground"))
        assertTrue(activity.indexOf("acknowledgeAppForeground") < activity.indexOf("NotificationCoordinator.reconcile"))
    }

    @Test
    fun `delayed due occurrence cannot re engage after the app was opened`() {
        val worker = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/DailyReminderWorker.kt"
        ).readText()
        val bridge = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationServices.kt"
        ).readText()

        assertTrue(worker.contains("occurrenceEpochMillis"))
        assertTrue(worker.contains("showDailyReminder("))
        assertTrue(bridge.contains("LAST_APP_FOREGROUND_EPOCH_MS"))
        assertTrue(bridge.contains("reminderOccurrenceAcknowledgedByAppOpen"))
        assertTrue(bridge.contains("NotificationDeliveryResult.APP_OPEN_ACKNOWLEDGED"))
    }

    @Test
    fun `expired current occurrence clears stale reminder while stale generation has no authority`() {
        val worker = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/DailyReminderWorker.kt"
        ).readText()
        val bridge = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationServices.kt"
        ).readText()

        assertTrue(worker.contains("clearExpiredReminderIfCurrent"))
        assertTrue(bridge.contains("expiredOccurrenceMayClearVisibleReminder"))
        assertTrue(bridge.contains("cancelReminderNotification(context)"))
    }

    @Test
    fun `backup mutations invalidate a visible backup integrity reminder`() {
        val backupCenter = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/settings/BackupCenterScreen.kt"
        ).readText()

        assertTrue(
            backupCenter.windowed("cancelReminderAfterDataMutation".length)
                .count { it == "cancelReminderAfterDataMutation" } >= 2,
        )
    }

    @Test
    fun `reminder enqueue completion is awaited and stale workers cannot append`() {
        val scheduler = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/ReminderScheduler.kt"
        ).readText()
        val worker = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/DailyReminderWorker.kt"
        ).readText()
        val bridge = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationServices.kt"
        ).readText()

        assertTrue(scheduler.contains("suspendCancellableCoroutine"))
        assertTrue(scheduler.contains("invokeOnCancellation"))
        assertTrue(scheduler.contains("reminderOccurrenceWorkName"))
        assertTrue(scheduler.contains("ExistingWorkPolicy.KEEP"))
        assertFalse(scheduler.contains("APPEND_OR_REPLACE"))
        assertTrue(scheduler.contains("cancelAllWorkByTag"))
        assertTrue(bridge.contains("reminderScheduleGenerationMatches"))
        assertTrue(worker.contains("inputData.getLong"))
    }

    @Test
    fun `paused rest is authoritative outside the draft and resynchronized on resume`() {
        val timer = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/WorkoutTimerClock.kt"
        ).readText()
        val screen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt"
        ).readText()
        val actions = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/WorkoutNotificationActionQueue.kt"
        ).readText()

        assertTrue(timer.contains("REST_PAUSED_REMAINING_MS"))
        assertTrue(actions.contains("PAUSE_REST"))
        assertTrue(screen.contains("SyncPausedRest"))
        assertTrue(screen.contains("vm.syncRestFromPersistence()"))
    }

    @Test
    fun `foreground service start is surfaced and durable timer is reconciled on every resume`() {
        val screen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt"
        ).readText()
        val resumeReconciliation = screen
            .substringAfter("fun reconcileForegroundNotificationOnResume()")
            .substringBefore("\n    /**")

        assertTrue(screen.contains("requestForegroundService"))
        assertTrue(screen.contains("Android blocked the status notification"))
        assertTrue(screen.contains("vm.reconcileForegroundNotificationOnResume()"))
        assertTrue(resumeReconciliation.contains("mutationMutex.withLock"))
        assertTrue(resumeReconciliation.contains("settingsRepo.getActiveWorkoutId()"))
        assertTrue(
            resumeReconciliation.contains(
                "settingsRepo.getString(WorkoutTimerSettingKeys.START_WALL_MS)",
            ),
        )
        assertTrue(resumeReconciliation.contains("requestForegroundService(workoutId, startMs)"))
        assertFalse(resumeReconciliation.contains("foregroundStartPending"))
        assertTrue(
            screen.indexOf("setLongSettingsIfStringMatches") <
                screen.indexOf("_timerStarted.value = true"),
        )
    }

    @Test
    fun `notification action delivery is signal driven and lifecycle aware`() {
        val screen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt"
        ).readText()
        val inbox = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/WorkoutNotificationActionQueue.kt"
        ).readText()

        assertTrue(screen.contains("repeatOnLifecycle(Lifecycle.State.STARTED)"))
        assertTrue(screen.contains("signalsForSession(sessionId)"))
        assertFalse(screen.contains("delay(900)"))
        assertTrue(inbox.contains("MutableSharedFlow"))
    }

    @Test
    fun `startup orphan cleanup validates the referenced workout row`() {
        val service = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/WorkoutForegroundService.kt"
        ).readText()

        assertTrue(service.contains("WorkoutEntity_"))
        assertTrue(service.contains("status == \"active\""))
        assertTrue(service.contains("REST_PAUSED_REMAINING_MS"))
    }

    @Test
    fun `updater cannot lose an initial deadline signal or spin on persistent failures`() {
        val service = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/WorkoutForegroundService.kt"
        ).readText()

        val clearSignal = service.indexOf("deadlineRefreshRequested.set(false)")
        val initialRead = service.indexOf("val initialRestState = readRestState()")
        assertTrue(clearSignal >= 0)
        assertTrue(initialRead > clearSignal)
        assertTrue(service.contains("workoutNotificationUpdaterFailureBackoffMs"))
        assertTrue(service.contains("MAX_CONSECUTIVE_UPDATER_FAILURES"))
        assertTrue(service.contains("awaitUpdaterSignal(backoffMs)"))
    }

    @Test
    fun `training reminder opt out is serialized with delivery and clears visible reminder`() {
        val settings = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/settings/SettingsScreen.kt"
        ).readText()
        val coordinator = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationCoordinator.kt"
        ).readText()

        assertTrue(settings.contains("NotificationCoordinator.setTrainingRemindersEnabled"))
        assertTrue(coordinator.contains("fun setTrainingRemindersEnabled"))
        assertTrue(coordinator.contains("cancelReminderNotification"))
    }

    @Test
    fun `authoritative reminder evaluation and tracked data mutations remove stale visible reminders`() {
        val bridge = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationServices.kt"
        ).readText()
        val activeWorkout = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt"
        ).readText()
        val navigator = projectRoot.resolve(
            "src/main/java/com/ironlog/app/navigation/AppNavigator.kt"
        ).readText()

        val staleBranch = bridge.substring(
            bridge.indexOf("if (expectedScheduleGeneration != null)"),
            bridge.indexOf("if (occurrenceEpochMillis != null)"),
        )
        assertTrue(staleBranch.contains("NotificationDeliveryResult.STALE_SCHEDULE"))
        assertFalse(staleBranch.contains("finishDailyReminderEvaluation"))
        assertTrue(bridge.contains("finishDailyReminderEvaluation(context, blocked.result)"))
        assertTrue(bridge.contains("NotificationDeliveryResult.NO_CANDIDATE"))
        assertTrue(bridge.contains("NotificationDeliveryResult.POLICY_SUPPRESSED"))
        assertTrue(bridge.contains("withContext(NonCancellable)"))
        assertTrue(activeWorkout.split("cancelReminderAfterDataMutation").size >= 3)
        assertTrue(navigator.contains("bodyVm.addAndAwait"))
        assertTrue(navigator.contains("bodyVm.removeAndAwait"))
        assertTrue(navigator.contains("cancelReminderAfterDataMutation"))
    }

    @Test
    fun `history goal and recovery proof mutations dismiss reminders only after persistence`() {
        val historicalHost = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/history/HistoricalWorkoutEntryHost.kt"
        ).readText()
        val history = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/stats/HistoryScreen.kt"
        ).readText()
        val settings = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/settings/SettingsScreen.kt"
        ).readText()
        val navigator = projectRoot.resolve(
            "src/main/java/com/ironlog/app/navigation/AppNavigator.kt"
        ).readText()

        val historicalSave = historicalHost.substring(
            historicalHost.indexOf("repo.saveHistoricalWorkout"),
            historicalHost.indexOf("LaunchedEffect(viewId)"),
        )
        assertTrue(historicalSave.contains("HistoricalWorkoutSaveResult.Saved"))
        assertTrue(historicalSave.contains("dismissReminderAfterHistoricalWorkoutSave"))

        listOf(
            "historyRepository.deleteWorkouts(toDelete)",
            "historyRepository.updateWorkout(",
            "historyRepository.deleteWorkout(entry.id)",
        ).forEach { write ->
            val writeIndex = history.indexOf(write)
            val cleanupIndex = history.indexOf("dismissReminderAfterHistoryMutation(context)", writeIndex)
            assertTrue("Missing post-commit reminder cleanup after $write", writeIndex >= 0 && cleanupIndex > writeIndex)
        }

        val weeklyGoalSave = settings.substring(
            settings.indexOf("fun saveWeeklyGoal"),
            settings.indexOf("val notificationPermissionLauncher"),
        )
        assertTrue(
            weeklyGoalSave.indexOf("vm.updateSettings(settings.copy(weeklyGoalDays = normalized))") <
                weeklyGoalSave.indexOf("cancelReminderAfterDataMutation"),
        )

        val circuitStart = navigator.indexOf("val result = gamificationVm.completeRecoveryCircuit")
        assertTrue(circuitStart >= 0)
        val circuitCompletion = navigator.substring(
            circuitStart,
            (circuitStart + 2_000).coerceAtMost(navigator.length),
        )
        assertTrue(circuitCompletion.contains("RecoveryCircuitResult.RECORDED"))
        assertTrue(circuitCompletion.contains("cancelReminderAfterDataMutation"))
    }

    @Test
    fun `notification settings issue intent tokens before launching IO and pass them to every mutation`() {
        val settings = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/settings/SettingsScreen.kt"
        ).readText()
        val runner = settings.substring(
            settings.indexOf("fun runReminderMutation"),
            settings.indexOf("fun saveWeeklyGoal"),
        )

        assertTrue(runner.contains("target: NotificationMutationTarget"))
        assertTrue(runner.contains("mutation: suspend (requestToken: Long) -> Unit"))
        assertTrue(
            runner.indexOf("NotificationCoordinator.issueMutationToken(target)") <
                runner.indexOf("scope.launch"),
        )
        assertTrue(runner.contains("NotificationCoordinator.isMutationTokenCurrent(target, requestToken)"))

        NotificationMutationTarget.entries.forEach { target ->
            assertTrue(
                "Settings does not route ${target.name} through its own mutation target",
                settings.contains("target = NotificationMutationTarget.${target.name}"),
            )
        }
        assertTrue(settings.contains("setMilestoneAlertsEnabled(enabled, requestToken)"))
        assertTrue(settings.contains("setFrequencyProfile(context, id, requestToken)"))
        assertTrue(settings.contains("updateReminderTime(context, minutes, requestToken)"))
        assertTrue(settings.contains("updateQuietHours(context, s, e, requestToken)"))
    }

    @Test
    fun `restore reconciles notifications durably before destination refresh and broadcast work is time bounded`() {
        val navigator = projectRoot.resolve(
            "src/main/java/com/ironlog/app/navigation/AppNavigator.kt"
        ).readText()
        val coordinator = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/NotificationCoordinator.kt"
        ).readText()

        val durableBoundary = navigator.substring(
            navigator.indexOf("internal suspend fun reconcileNotificationsAfterRestore"),
            navigator.indexOf("private suspend fun settingsRepoSaveOnboardingDataFull"),
        )
        assertTrue(durableBoundary.contains("withContext(Dispatchers.IO + NonCancellable)"))
        assertTrue(durableBoundary.contains("cancelReminderAfterDataMutation(app)"))
        assertTrue(durableBoundary.contains("clearOrphanedNotification(app)"))
        assertTrue(durableBoundary.contains("NotificationCoordinator.reconcile(app, forceReschedule = true)"))

        val restoreCall = "reconcileNotificationsAfterRestore(app)"
        val callIndexes = buildList {
            var index = navigator.indexOf(restoreCall)
            while (index >= 0) {
                add(index)
                index = navigator.indexOf(restoreCall, index + restoreCall.length)
            }
        }
        assertTrue("Every restore destination must enter the durable boundary", callIndexes.size == 3)
        callIndexes.forEach { callIndex ->
            val refreshIndex = navigator.indexOf("appVm.refresh().join()", callIndex)
            assertTrue(
                "Restore notification reconciliation must precede cancellable UI refresh",
                refreshIndex in (callIndex + 1)..(callIndex + 500),
            )
        }
        assertTrue(coordinator.contains("withTimeout(RECEIVER_RECONCILE_TIMEOUT_MS)"))
    }

    @Test
    fun `task removal preserves a valid workout and UI rest writes are ordered`() {
        val service = projectRoot.resolve(
            "src/main/java/com/ironlog/app/services/WorkoutForegroundService.kt"
        ).readText()
        val screen = projectRoot.resolve(
            "src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt"
        ).readText()
        val taskRemoved = service.substring(
            service.indexOf("override fun onTaskRemoved"),
            service.indexOf("override fun onBind"),
        )

        assertTrue(taskRemoved.contains("!isSessionActive(owner.workoutId)"))
        assertFalse(screen.contains("private val restControlMutex = Mutex()"))
        assertTrue(screen.split("mutationMutex.withLock").size >= 8)
        assertTrue(screen.contains("commitRestControl("))
        assertTrue(screen.contains("restControlPending"))
    }
}
