package com.ironlog.app.domain.gamification

import com.ironlog.app.domain.badges.BadgeDefinitions

internal fun gradeTitle(grade: IronGrade): String = when (grade) {
    IronGrade.UNCALIBRATED -> "Ledger Initiate"
    IronGrade.GRAPHITE -> "Graphite Trainee"
    IronGrade.IRON -> "Iron Regular"
    IronGrade.STEEL -> "Steel Builder"
    IronGrade.TITANIUM -> "Titanium Athlete"
    IronGrade.OBSIDIAN -> "Obsidian Veteran"
    IronGrade.IRIDIUM -> "Iridium Specialist"
    IronGrade.AETHER -> "Aether Standard"
    IronGrade.APEX -> "Apex Ledger"
}

internal fun earnedBadgeTitle(id: String): String = BadgeDefinitions.all.firstOrNull { it.id == id }?.title
    ?: IronGrade.entries.firstOrNull { it.label == id }?.let(::gradeTitle)
    ?: id.replace('_', ' ').replaceFirstChar(Char::titlecase)
