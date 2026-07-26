package com.ironlog.app.data.objectbox

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique

@Entity
data class AthleteCalibrationEntity(
    @Id var id: Long = 0,
    @Unique var offlineUserId: String = "",
    var trainingAgeMonths: Int = 0,
    var historicalTrainingDaysPerWeek: Int = 3,
    var firstVerifiedSessionAt: Long = 0L,
    var weightUnit: String = "kg",
    var bodyweightKg: Double? = null,
    var goalMode: String = "hypertrophy",
    var weeklyGoalDays: Int = 4,
    var importedHistory: Boolean = false,
    var confidence: Double = 0.5,
    var updatedAt: Long = 0L,
    var hasPastTraining: Boolean = false,
    var hasGymAccess: Boolean = true,
    var baselinePushups: Int = 0,
    var baselinePullups: Int = 0,
    var baselineBenchKg: Int = 0,
    var baselineLatPulldownKg: Int = 0,
    var baselineMileRunSeconds: Int = 0,
)
