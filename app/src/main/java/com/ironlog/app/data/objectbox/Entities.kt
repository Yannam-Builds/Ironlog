package com.ironlog.app.data.objectbox

import io.objectbox.annotation.Backlink
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.annotation.Unique
import io.objectbox.annotation.ConflictStrategy
import io.objectbox.relation.ToMany
import io.objectbox.relation.ToOne
import java.util.UUID

internal fun newUid(): String = UUID.randomUUID().toString()

/** ObjectBox entity: db_smoke_tests */
@Entity
class DbSmokeTestEntity {
    @Id var objectBoxId: Long = 0
    var label: String = ""
    var createdAt: Long = 0L
}

/** ObjectBox entity: exercises */
@Entity
class ExerciseEntity {
    @Id var objectBoxId: Long = 0

    /** Stable string id for cross-device and export/import identity. */
    @Unique(onConflict = ConflictStrategy.FAIL)
    var uid: String = newUid()

    @Index var name: String = ""
    @Index var normalizedName: String = ""
    @Index var primaryMuscle: String = ""
    @Index var equipment: String = ""
    var category: String = "strength"
    @Index var isCustom: Boolean = false
    var source: String = ""
    var notes: String = ""
    var createdAt: Long = 0L
    var updatedAt: Long = 0L

    /** Optional metadata present in exerciseLibrary.json but not in schema v2. */
    var equipmentDetail: String? = null
    var apparatusJson: String? = null
    var trackingType: String? = null
    var isBodyweight: Boolean = false
    var requiresExternalLoad: Boolean = false
    var movementPattern: String? = null
    var difficulty: String? = null
    var aliasesJson: String? = null
    var sourceTagsJson: String? = null
    var secondaryMusclesJson: String? = null

    @Backlink(to = "exercise")
    lateinit var muscles: ToMany<ExerciseMuscleEntity>
}

/** ObjectBox entity: exercise_muscles */
@Entity
class ExerciseMuscleEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var uid: String = newUid()

    lateinit var exercise: ToOne<ExerciseEntity>
    @Index var exerciseUid: String = ""
    @Index var muscle: String = ""
    var role: String = "secondary"
    var contributionFraction: Double = 0.0
    var createdAt: Long = 0L
    var updatedAt: Long = 0L
}

/** ObjectBox entity: plans */
@Entity
class PlanEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.FAIL)
    var uid: String = newUid()

    var name: String = ""
    var goal: String = ""
    var description: String = ""
    @Index var isActive: Boolean = false
    var createdAt: Long = 0L
    @Index var updatedAt: Long = 0L

    @Backlink(to = "plan")
    lateinit var days: ToMany<PlanDayEntity>
}

/** ObjectBox entity: plan_days */
@Entity
class PlanDayEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.FAIL)
    var uid: String = newUid()

    lateinit var plan: ToOne<PlanEntity>
    @Index var planUid: String = ""
    var name: String = ""
    var color: String = "#FF4500"
    @Index var orderIndex: Int = 0
    var createdAt: Long = 0L
    var updatedAt: Long = 0L

    @Backlink(to = "planDay")
    lateinit var exercises: ToMany<PlanExerciseEntity>
}

/** ObjectBox entity: plan_exercises */
@Entity
class PlanExerciseEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.FAIL)
    var uid: String = newUid()

    lateinit var planDay: ToOne<PlanDayEntity>
    @Index var planDayUid: String = ""
    lateinit var exercise: ToOne<ExerciseEntity>
    @Index var exerciseUid: String = ""
    @Index var orderIndex: Int = 0
    var sets: Int = 1
    var reps: String = ""
    var restSeconds: Int = 0
    var supersetGroup: String = ""
    var isWarmup: Boolean = false
    var notes: String = ""
    var createdAt: Long = 0L
    var updatedAt: Long = 0L
}

/** ObjectBox entity: workouts */
@Entity
class WorkoutEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.FAIL)
    var uid: String = newUid()

    lateinit var plan: ToOne<PlanEntity>
    @Index var planUid: String? = null
    lateinit var planDay: ToOne<PlanDayEntity>
    @Index var planDayUid: String? = null
    var name: String = ""
    @Index var startedAt: Long = 0L
    var completedAt: Long? = null
    var durationSeconds: Int = 0
    var rating: Double? = null
    var notes: String = ""
    @Index var status: String = "active"
    /** True when this workout came from an external/legacy import rather than local logging. */
    var imported: Boolean = false
    var createdAt: Long = 0L
    var updatedAt: Long = 0L

    @Backlink(to = "workout")
    lateinit var exercises: ToMany<WorkoutExerciseEntity>
}

/** ObjectBox entity: workout_exercises */
@Entity
class WorkoutExerciseEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.FAIL)
    var uid: String = newUid()

    lateinit var workout: ToOne<WorkoutEntity>
    @Index var workoutUid: String = ""
    lateinit var exercise: ToOne<ExerciseEntity>
    @Index var exerciseUid: String = ""
    @Index var orderIndex: Int = 0
    var supersetGroup: String = ""
    var notes: String = ""
    var createdAt: Long = 0L
    var updatedAt: Long = 0L

    @Backlink(to = "workoutExercise")
    lateinit var sets: ToMany<WorkoutSetEntity>
}

/** ObjectBox entity: workout_sets */
@Entity
class WorkoutSetEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.FAIL)
    var uid: String = newUid()

    lateinit var workoutExercise: ToOne<WorkoutExerciseEntity>
    @Index var workoutExerciseUid: String = ""
    @Index var setIndex: Int = 1
    var weight: Double = 0.0
    var reps: Double = 0.0
    var rpe: Double? = null
    var rir: Double? = null
    var restSeconds: Int = 0
    @Index var isWarmup: Boolean = false
    var isDropset: Boolean = false
    var isAmrap: Boolean = false
    var toFailure: Boolean = false
    var completedAt: Long? = null
    var createdAt: Long = 0L
    var updatedAt: Long = 0L
}

/** ObjectBox entity: body_measurements */
@Entity
class BodyMeasurementEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.FAIL)
    var uid: String = newUid()
    @Index var measuredAt: Long = 0L
    var bodyweight: Double? = null
    var waist: Double? = null
    var chest: Double? = null
    var arm: Double? = null
    var thigh: Double? = null
    var notes: String = ""
    var createdAt: Long = 0L
    var updatedAt: Long = 0L
}

/** ObjectBox entity: app_settings */
@Entity
class AppSettingEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.REPLACE)
    var key: String = ""
    var value: String = ""
    var valueType: String = "string"
    var updatedAt: Long = 0L
}

/** ObjectBox entity: progress_photos */
@Entity
class ProgressPhotoEntity {
    @Id var objectBoxId: Long = 0
    @Unique(onConflict = ConflictStrategy.FAIL)
    var uid: String = newUid()
    var fileUri: String = ""
    @Index var takenAt: Long = 0L
    var bodyweight: Double? = null
    var notes: String = ""
    var createdAt: Long = 0L
    var updatedAt: Long = 0L
}
