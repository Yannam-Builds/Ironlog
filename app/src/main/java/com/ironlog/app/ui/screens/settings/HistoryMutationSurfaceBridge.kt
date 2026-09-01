package com.ironlog.app.ui.screens.settings

import android.content.Context
import com.ironlog.app.services.NotificationCoordinator
import com.ironlog.app.services.WorkoutNotificationBridge
import com.ironlog.app.ui.viewmodel.AppDataViewModel
import com.ironlog.app.widget.WidgetUpdateWorker

/** Reconciles every derived consumer after a history or PR-baseline mutation, from any screen. */
internal suspend fun <T> commitHistoryMutationAcrossSurfaces(
    context: Context,
    viewModel: AppDataViewModel,
    mutation: suspend () -> T,
): HistoryMutationOutcome<T> {
    val appContext = context.applicationContext
    return runHistoryMutationTerminally(
        mutation = mutation,
        refreshAppState = { viewModel.refreshAwaited() },
        dismissStaleReminder = {
            WorkoutNotificationBridge.cancelReminderAfterDataMutation(appContext)
        },
        reconcileNotifications = {
            NotificationCoordinator.reconcile(appContext, forceReschedule = true)
        },
        enqueueWidgets = {
            WidgetUpdateWorker.enqueueOneTime(appContext)
        },
    )
}
