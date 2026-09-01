package com.ironlog.app.ui.screens.workout

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import com.ironlog.app.domain.gamification.parseHistoryInstant
import com.ironlog.app.domain.gamification.parseHistoryLocalDate
import com.ironlog.app.domain.gamification.CreditedProof
import com.ironlog.app.ui.screens.history.calendarSessionsByLocalDate
import com.ironlog.app.ui.state.rememberPresentationTime
import java.time.Instant
import java.time.ZoneId
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import com.ironlog.app.ui.theme.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ironlog.app.data.model.CreateCompletedWorkoutInput
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.model.HistoryEntry
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import com.ironlog.app.util.formatWeightFromKg
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// GAP-19: ISO week starts Monday to match the rest of the app
private val DAY_HEADERS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val MONTH_NAMES = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

fun getCalendarStreakCount(history: List<HistoryEntry>, now: Instant = Instant.now(), zoneId: ZoneId = ZoneId.systemDefault()): Int {
    val workoutDays = history.filter { CreditedProof.qualifies(it, now, zoneId) }
        .mapNotNull { parseHistoryLocalDate(it.date, zoneId) }.toSet()
    val today = now.atZone(zoneId).toLocalDate()
    var cursor = if (today in workoutDays) today else today.minusDays(1)
    var streak = 0
    while (cursor in workoutDays) {
        streak++
        cursor = cursor.minusDays(1)
    }
    return streak
}

fun formatDurationMins(seconds: Int?): String {
    if (seconds == null || seconds == 0) return "0min"
    val m = (seconds / 60.0).roundToInt()
    if (m < 60) return "${m}min"
    val h = m / 60
    val rem = m % 60
    return if (rem > 0) "${h}h ${rem}min" else "${h}h"
}

fun formatDateLong(isoString: String): String = runCatching {
    val instant = java.time.Instant.parse(isoString)
    java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
        .format(instant.atZone(java.time.ZoneId.systemDefault()))
}.getOrElse { isoString }

fun calcSessionVolume(session: HistoryEntry): Int = session.volume.roundToInt()

fun toDisplayVolume(kgValue: Int, unit: String): Int = if (unit == "lbs") (kgValue * 2.2046226218).roundToInt() else kgValue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutCalendarScreen(
    history: List<HistoryEntry>,
    weightUnit: String = "kg",
    onBack: () -> Unit = {},
    onLogHistorical: (dateKey: String) -> Unit = {},
) {
    val colors = useTheme()
    val nowMillis by rememberPresentationTime()
    val zoneId = ZoneId.systemDefault()
    val now = Instant.ofEpochMilli(nowMillis)
    val todayDate = now.atZone(zoneId).toLocalDate()
    val today = remember(todayDate, zoneId) { java.util.GregorianCalendar.from(todayDate.atStartOfDay(zoneId)) }
    var viewYear by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var selectedSession by remember { mutableStateOf<HistoryEntry?>(null) }
    // Week view state — weekOffset 0 = current week, -1 = last week, etc.
    var showWeekView by remember { mutableStateOf(false) }
    var weekOffset by remember { mutableIntStateOf(0) }

    var addForDate by remember { mutableStateOf<String?>(null) }

    val sessionsByDate = remember(history, zoneId) { calendarSessionsByLocalDate(history, zoneId) }
    val streak = remember(history, now, zoneId) { getCalendarStreakCount(history, now, zoneId) }
    val monthSessions = remember(history, viewYear, viewMonth, zoneId) {
        history.filter {
            val date = parseHistoryLocalDate(it.date, zoneId) ?: return@filter false
            date.year == viewYear && date.monthValue == viewMonth + 1
        }
    }
    val monthTotalVolume = remember(monthSessions) { monthSessions.sumOf { calcSessionVolume(it) } }
    // GAP-19: ISO week Mon=0..Sun=6; Calendar.DAY_OF_WEEK is Sun=1..Sat=7, so Mon=(dow-2+7)%7
    val firstDayOfWeek = remember(viewYear, viewMonth) {
        val dow = Calendar.getInstance().apply { set(viewYear, viewMonth, 1) }.get(Calendar.DAY_OF_WEEK)
        (dow - 2 + 7) % 7
    }
    val daysInMonth = remember(viewYear, viewMonth) { Calendar.getInstance().apply { set(viewYear, viewMonth + 1, 0) }.get(Calendar.DAY_OF_MONTH) }
    val todayKey = todayDate.toString()
    val fmt = remember(zoneId) { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone(zoneId) } }

    // Week view: compute the 7 days of the offset week (Sun..Sat)
    val weekDays = remember(weekOffset, todayDate, zoneId) {
        val monday = todayDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).plusWeeks(weekOffset.toLong())
        (0..6).map { offset ->
            java.util.GregorianCalendar.from(monday.plusDays(offset.toLong()).atStartOfDay(zoneId))
        }
    }
    val weekLabel = remember(weekDays) {
        val startFmt = SimpleDateFormat("MMM d", Locale.US)
        val endFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
        "${startFmt.format(weekDays.first().time)} – ${endFmt.format(weekDays.last().time)}"
    }
    // Sessions for the selected week (agenda list)
    val weekSessions = remember(sessionsByDate, weekDays) {
        val weekKeys = weekDays.map { fmt.format(it.time) }.toSet()
        weekKeys.flatMap { sessionsByDate[it].orEmpty() }.sortedBy { parseHistoryInstant(it.date, zoneId) }
    }

    fun prevMonth() { if (viewMonth == 0) { viewMonth = 11; viewYear -= 1 } else viewMonth -= 1 }
    fun nextMonth() { if (viewMonth == 11) { viewMonth = 0; viewYear += 1 } else viewMonth += 1 }
    fun dayKey(day: Int) = "%04d-%02d-%02d".format(viewYear, viewMonth + 1, day)

    Column(Modifier.fillMaxSize().background(colors.bg).statusBarsPadding().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp)) {
        ScreenHeader(title = "CALENDAR", onBack = onBack)

        // View toggle: Month | Week
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(IronLogRadius.lg.dp))
                .background(colors.surface)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
                .appPadding(4.dp),
            horizontalArrangement = appSpacedBy(4.dp),
        ) {
            listOf("Month" to false, "Week" to true).forEach { (label, weekMode) ->
                val selected = showWeekView == weekMode
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(IronLogRadius.md.dp))
                        .background(if (selected) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { showWeekView = weekMode }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = if (selected) colors.textOnAccent else colors.subtext,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = IronLogType.body.fontSize.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(appGapDp(12.dp)))

        // Stats row
        Row(horizontalArrangement = appSpacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("STREAK", streak.toString(), Modifier.weight(1f))
            if (showWeekView) {
                StatCard("THIS WEEK", weekSessions.size.toString(), Modifier.weight(1f))
                val wVol = toDisplayVolume(weekSessions.sumOf { calcSessionVolume(it) }, weightUnit)
                StatCard("VOLUME ($weightUnit)", if (wVol >= 1000) "%.1fk".format(wVol / 1000.0) else wVol.toString(), Modifier.weight(1f))
            } else {
                StatCard("THIS MONTH", monthSessions.size.toString(), Modifier.weight(1f))
                val displayVol = toDisplayVolume(monthTotalVolume, weightUnit)
                StatCard("VOLUME ($weightUnit)", if (displayVol >= 1000) "%.1fk".format(displayVol / 1000.0) else displayVol.toString(), Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(appGapDp(12.dp)))

        if (showWeekView) {
            // ── Week view ────────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { weekOffset -= 1 }, contentAlignment = Alignment.Center) {
                    Text("‹", color = colors.text, fontSize = IronLogType.display.fontSize.sp)
                }
                Text(weekLabel, color = colors.text, fontSize = IronLogType.body.fontSize.sp, fontWeight = FontWeight.Medium)
                Box(Modifier.size(44.dp).clickable { weekOffset += 1 }, contentAlignment = Alignment.Center) {
                    Text("›", color = colors.text, fontSize = IronLogType.display.fontSize.sp)
                }
            }
            // Day cells
            Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(16.dp)).border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp)).padding(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    DAY_HEADERS.forEach { Text(it, color = colors.muted, modifier = Modifier.weight(1f), fontSize = IronLogType.meta.fontSize.sp) }
                }
                Spacer(Modifier.height(appGapDp(4.dp)))
                Row(Modifier.fillMaxWidth()) {
                    weekDays.forEach { cal ->
                        val key = fmt.format(cal.time)
                        val sessions = sessionsByDate[key].orEmpty()
                        val isToday = key == todayKey
                        val isPast = key <= todayKey   // includes today (can log on today)
                        val isFuture = key > todayKey
                        Box(
                            Modifier.weight(1f).height(56.dp).padding(2.dp)
                                .background(if (isToday) colors.accentSoft else colors.surface, RoundedCornerShape(10.dp))
                                .border(1.dp, when {
                                    sessions.isNotEmpty() -> colors.accent
                                    !isFuture -> colors.cardBorder
                                    else -> colors.faint
                                }, RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (!isFuture) {
                                            addForDate = key
                                        }
                                    },
                                    onLongClick = {
                                        if (!isFuture) addForDate = key
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = appSpacedBy(2.dp)) {
                                Text(cal.get(Calendar.DAY_OF_MONTH).toString(),
                                    color = if (isFuture) colors.muted else colors.text,
                                    fontSize = IronLogType.body.fontSize.sp)
                                // GAP-06: session count badge
                                if (sessions.isNotEmpty()) {
                                    if (sessions.size > 1) {
                                        Box(
                                            Modifier.size(14.dp).background(colors.accent, CircleShape),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(sessions.size.toString(), color = colors.textOnAccent, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                                    }
                                } else if (!isFuture) {
                                    // Subtle + icon on empty past cells
                                    Icon(Icons.Filled.Add, contentDescription = "Log workout",
                                        tint = colors.muted, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }
            // Agenda list for the week
            if (weekSessions.isNotEmpty()) {
                Spacer(Modifier.height(appGapDp(16.dp)))
                Text(
                    "SESSIONS",
                    color = colors.muted,
                    fontSize = IronLogType.eyebrow.fontSize.sp,
                    fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                    letterSpacing = IronLogType.eyebrow.letterSpacing.sp,
                )
                Spacer(Modifier.height(appGapDp(8.dp)))
                weekSessions.forEach { session ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(IronLogRadius.md.dp))
                            .background(colors.card)
                            .border(1.dp, colors.cardBorder, RoundedCornerShape(IronLogRadius.md.dp))
                            .clickable { selectedSession = session }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = appSpacedBy(2.dp)) {
                            Text(session.name.uppercase(), color = colors.accent, fontWeight = FontWeight.Bold, fontSize = IronLogType.body.fontSize.sp)
                            Text(
                                "${session.sets} sets · ${formatDurationMins(session.duration)}",
                                color = colors.muted,
                                fontSize = IronLogType.meta.fontSize.sp,
                            )
                        }
                        Text(
                            parseHistoryLocalDate(session.date, zoneId)?.toString().orEmpty(),
                            color = colors.subtext,
                            fontSize = IronLogType.meta.fontSize.sp,
                        )
                    }
                    Spacer(Modifier.height(appGapDp(8.dp)))
                }
            } else {
                Spacer(Modifier.height(appGapDp(24.dp)))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No workouts this week", color = colors.muted, fontSize = IronLogType.body.fontSize.sp)
                }
            }
        } else {
            // ── Month view ───────────────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { prevMonth() }, contentAlignment = Alignment.Center) {
                    Text("‹", color = colors.text, fontSize = IronLogType.display.fontSize.sp)
                }
                Text("${MONTH_NAMES[viewMonth]} $viewYear", color = colors.text, fontSize = IronLogType.title.fontSize.sp)
                Box(Modifier.size(44.dp).clickable { nextMonth() }, contentAlignment = Alignment.Center) {
                    Text("›", color = colors.text, fontSize = IronLogType.display.fontSize.sp)
                }
            }
            Column(Modifier.fillMaxWidth().background(colors.card, RoundedCornerShape(16.dp)).border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp)).padding(8.dp)) {
                Row(Modifier.fillMaxWidth()) { DAY_HEADERS.forEach { Text(it, color = colors.muted, modifier = Modifier.weight(1f), fontSize = IronLogType.meta.fontSize.sp) } }
                val cells = buildList<Int?> { repeat(firstDayOfWeek) { add(null) }; for (d in 1..daysInMonth) add(d); while (size % 7 != 0) add(null) }
                cells.chunked(7).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { day ->
                            val key = day?.let { dayKey(it) }
                            val sessions = key?.let { sessionsByDate[it] }.orEmpty()
                            val isToday = key == todayKey
                            val isFuture = key != null && key > todayKey
                            val isPast = key != null && !isFuture
                            Box(
                                Modifier.weight(1f).height(48.dp).padding(2.dp)
                                    .background(if (isToday) colors.accentSoft else colors.surface, RoundedCornerShape(10.dp))
                                    .border(1.dp, when {
                                        sessions.isNotEmpty() -> colors.accent
                                        isPast -> colors.cardBorder
                                        else -> colors.faint
                                    }, RoundedCornerShape(10.dp))
                                    .then(if (key != null) Modifier.combinedClickable(
                                        onClick = {
                                            if (isPast) {
                                                addForDate = key
                                            }
                                        },
                                        onLongClick = {
                                            if (isPast) addForDate = key
                                        },
                                    ) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                // GAP-06: session count badge for month view
                                if (day != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(day.toString(),
                                            color = if (isFuture) colors.muted else colors.text,
                                            fontSize = IronLogType.body.fontSize.sp)
                                        if (sessions.isNotEmpty()) {
                                            if (sessions.size > 1) {
                                                Box(
                                                    Modifier.size(13.dp).background(colors.accent, CircleShape),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(sessions.size.toString(), color = colors.textOnAccent, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                Box(Modifier.size(5.dp).background(colors.accent, CircleShape))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selectedSession?.let { session ->
        SessionDetailDialog(session = session, weightUnit = weightUnit, onDismiss = { selectedSession = null })
    }

    addForDate?.let { dateKey ->
        val sessions = sessionsByDate[dateKey].orEmpty()
        AlertDialog(
            onDismissRequest = { addForDate = null },
            title = { Text(dateKey, color = colors.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = appSpacedBy(8.dp)) {
                    Text("Recorded sessions", color = colors.subtext)
                    if (sessions.isEmpty()) Text("No workout recorded yet.", color = colors.muted)
                    sessions.forEach { session ->
                        TextButton(onClick = { addForDate = null; selectedSession = session },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("${parseHistoryInstant(session.date, zoneId)?.atZone(zoneId)?.toLocalTime()?.withSecond(0)?.withNano(0)} · ${session.name}", color = colors.text)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { addForDate = null; onLogHistorical(dateKey) }) { Text("Log workout") } },
            dismissButton = { TextButton(onClick = { addForDate = null }) { Text("Cancel") } },
            containerColor = colors.card,
        )
    }
}

@Composable
private fun SessionDetailDialog(session: HistoryEntry, weightUnit: String, onDismiss: () -> Unit) {
    val c = useTheme()
    val volume = calcSessionVolume(session)
    val displayVol = toDisplayVolume(volume, weightUnit)
    val volStr = if (displayVol >= 1000) "%.1fk".format(displayVol / 1000.0) else displayVol.toString()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(IronLogRadius.xl.dp))
                .background(c.card)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Column(Modifier.fillMaxWidth().appPadding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    session.name.uppercase(),
                    color = c.accent,
                    fontWeight = FontWeight(IronLogType.section.fontWeight),
                    fontSize = IronLogType.section.fontSize.sp,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    formatDateLong(session.date),
                    color = c.subtext,
                    fontSize = IronLogType.body.fontSize.sp,
                )
            }
            // Stats row: SETS | DURATION | VOL
            Row(
                Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = c.faint, shape = RoundedCornerShape(0.dp))
                    .appPadding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatPill(value = session.sets.toString(), label = "SETS")
                Box(Modifier.width(1.dp).height(32.dp).background(c.faint))
                StatPill(value = formatDurationMins(session.duration), label = "DURATION")
                Box(Modifier.width(1.dp).height(32.dp).background(c.faint))
                StatPill(value = volStr, label = "VOL ($weightUnit)")
            }
            // Exercise breakdown
            if (session.exercises.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().appPadding(16.dp), verticalArrangement = appSpacedBy(8.dp)) {
                    Text(
                        "EXERCISES",
                        color = c.muted,
                        fontSize = IronLogType.eyebrow.fontSize.sp,
                        fontWeight = FontWeight(IronLogType.eyebrow.fontWeight),
                        letterSpacing = 2.sp,
                    )
                    session.exercises.forEach { ex ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(IronLogRadius.sm.dp))
                                .background(c.bg)
                                .border(1.dp, c.faint, RoundedCornerShape(IronLogRadius.sm.dp))
                                .appPadding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = appSpacedBy(2.dp),
                        ) {
                            Text(ex.name, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = IronLogType.body.fontSize.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (ex.sets.isNotEmpty()) {
                                val setStr = ex.sets.joinToString(" / ") { s ->
                                    val w = if (s.weight > 0) formatWeightFromKg(s.weight, weightUnit) else "BW"
                                    "$w × ${s.reps.roundToInt()}"
                                }
                                Text(setStr, color = c.muted, fontSize = IronLogType.meta.fontSize.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().appPadding(20.dp), contentAlignment = Alignment.Center) {
                    Text("No exercise details recorded.", color = c.muted, fontSize = IronLogType.body.fontSize.sp)
                }
            }
            // Close
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.CenterEnd) {
                TextButton(onClick = onDismiss) { Text("CLOSE", color = c.accent) }
            }
        }
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    val c = useTheme()
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = appSpacedBy(2.dp)) {
        Text(value, color = c.text, fontWeight = FontWeight.Bold, fontSize = IronLogType.title.fontSize.sp)
        Text(label, color = c.muted, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight(IronLogType.eyebrow.fontWeight), letterSpacing = 1.sp)
    }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = useTheme()
    Column(modifier.background(colors.card, RoundedCornerShape(12.dp)).border(1.dp, colors.cardBorder, RoundedCornerShape(12.dp)).appPadding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = colors.text, fontSize = IronLogType.title.fontSize.sp)
        Text(label, color = colors.muted, fontSize = IronLogType.micro.fontSize.sp)
    }
}

