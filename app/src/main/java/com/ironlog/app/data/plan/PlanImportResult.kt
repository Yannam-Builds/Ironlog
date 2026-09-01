package com.ironlog.app.data.plan

/** Completed, committed outcome of a plan import. */
data class PlanImportResult(
    val importedPlanIds: List<String>,
    val importedExercises: Int,
    val createdCustomExercises: Int,
    val unresolvedExercises: List<String>,
    val skipped: Int,
) {
    val imported: Int get() = importedPlanIds.size
    val unresolved: Int get() = unresolvedExercises.size
}
