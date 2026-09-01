package com.ironlog.app.ui.screens.settings

internal enum class SettingsDestination {
    CONSOLE,
    TRAINING,
    INTELLIGENCE,
    APPEARANCE,
    NOTIFICATIONS,
    DATA_PRIVACY,
    ABOUT,
}

internal data class SettingsDestinationSpec(
    val destination: SettingsDestination,
    val title: String,
    val description: String,
    val keywords: Set<String>,
)

internal val settingsConsoleDestinations = listOf(
    SettingsDestinationSpec(
        destination = SettingsDestination.TRAINING,
        title = "Training",
        description = "Profile, workout behavior, equipment and tracking tools",
        keywords = setOf("athlete", "goal", "days", "weight", "unit", "rest", "barbell", "haptics", "exercise", "gym", "body weight", "plan"),
    ),
    SettingsDestinationSpec(
        destination = SettingsDestination.INTELLIGENCE,
        title = "Intelligence",
        description = "Choose and configure the coaching engine",
        keywords = setOf("built in", "apex", "nano", "cloud", "api", "provider", "model", "coaching", "ai"),
    ),
    SettingsDestinationSpec(
        destination = SettingsDestination.APPEARANCE,
        title = "Appearance",
        description = "Theme, typography, spacing and optional effects",
        keywords = setOf("theme", "font", "type", "spacing", "shine", "glass", "motion", "visual"),
    ),
    SettingsDestinationSpec(
        destination = SettingsDestination.NOTIFICATIONS,
        title = "Notifications",
        description = "Workout reminders, milestones, quiet hours and channels",
        keywords = setOf("reminder", "alert", "milestone", "quiet hours", "time", "channel", "test"),
    ),
    SettingsDestinationSpec(
        destination = SettingsDestination.DATA_PRIVACY,
        title = "Data & Privacy",
        description = "Import, backup, export, privacy and destructive data actions",
        keywords = setOf("backup", "restore", "import", "export", "csv", "privacy", "delete history", "clear history", "reset pr"),
    ),
    SettingsDestinationSpec(
        destination = SettingsDestination.ABOUT,
        title = "About",
        description = "Version information and the app tutorial",
        keywords = setOf("version", "build", "tutorial", "onboarding", "help"),
    ),
)

internal fun filterSettingsConsoleDestinations(query: String): List<SettingsDestinationSpec> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    if (terms.isEmpty()) return settingsConsoleDestinations
    return settingsConsoleDestinations.filter { spec ->
        val searchable = buildString {
            append(spec.title)
            append(' ')
            append(spec.description)
            append(' ')
            append(spec.keywords.joinToString(" "))
        }.lowercase()
        terms.all(searchable::contains)
    }
}

internal enum class SettingsDangerAction {
    CLEAR_HISTORY,
    RESET_PERSONAL_RECORDS,
}

internal val settingsDangerActions = listOf(
    SettingsDangerAction.CLEAR_HISTORY,
    SettingsDangerAction.RESET_PERSONAL_RECORDS,
)
