// app/src/main/java/com/ironlog/app/data/entity/GamificationProfileEntity.kt
package com.ironlog.app.data.objectbox

import io.objectbox.annotation.ConflictStrategy
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique

@Entity
data class GamificationProfileEntity(
    @Id var id: Long = 0,

    /** Stable user identifier (UUID string, set once on first launch). */
    @Unique var offlineUserId: String = "",

    /** Total accumulated XP across all time. */
    var totalXp: Long = 0L,

    /** Current level (1-100), derived from totalXp but cached for display. */
    var level: Int = 1,

    /** XP within the current level (0..xpForLevel(level)). */
    var xpInLevel: Long = 0L,

    /** Current Iron Ledger grade label or legacy bridge key. */
    var rank: String = "E",

    /** Active title (unlocked badge name). */
    var activeTitle: String = "Ledger Initiate",

    /** JSON-serialized RpgStats for fast display without recomputing. */
    var statsJson: String = "{}",

    /** XP earned this ISO week (for weekly leaderboard -- stored, not yet used). */
    var weeklyXp: Int = 0,

    /** ISO week key of last weeklyXp reset, e.g. "2026-W21". */
    var weeklyXpResetWeek: String = "",

    /** Current streak in qualifying weeks. */
    var streakWeeks: Int = 0,

    /** JSON map of ISO-week-key -> recovery circuit completions. Field name is legacy ObjectBox schema. */
    var makeupCompletionsJson: String = "{}",

    /** Comma-separated list of unlocked badge IDs. */
    var unlockedBadges: String = "",
)
