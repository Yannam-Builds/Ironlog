package com.ironlog.app.ui.screens.body

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.ProgressPhotoEntity
import com.ironlog.app.data.objectbox.ProgressPhotoEntity_
import com.ironlog.app.data.objectbox.newUid
import com.ironlog.app.services.ShareService
import com.ironlog.app.ui.components.ScreenHeader
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.IronLogRadius
import com.ironlog.app.ui.theme.IronLogType
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import coil3.compose.AsyncImage
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.style.TextOverflow

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ProgressPhotosScreen(onBack: () -> Unit = {}) {
    val c = useTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoBox = remember { ObjectBox.store.boxFor(ProgressPhotoEntity::class.java) }
    var rows by remember { mutableStateOf<List<ProgressPhotoEntity>>(emptyList()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingPhotoDate by remember { mutableStateOf<LocalDate?>(null) }
    var showAddPhotoDialog by remember { mutableStateOf<LocalDate?>(null) }
    var selectedA by remember { mutableStateOf<ProgressPhotoEntity?>(null) }
    var selectedB by remember { mutableStateOf<ProgressPhotoEntity?>(null) }
    var compareMix by remember { mutableStateOf(0.5f) }
    var showViewer by remember { mutableStateOf(false) }
    var viewerStartIndex by remember { mutableStateOf(0) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var isExportingZip by remember { mutableStateOf(false) }
    // Calendar state
    var calendarMonth by remember { mutableStateOf(YearMonth.now()) }
    var calendarFilter by remember { mutableStateOf<LocalDate?>(null) }
    var calendarCompareMode by remember { mutableStateOf(false) }
    var compareDateA by remember { mutableStateOf<LocalDate?>(null) }
    var compareDateB by remember { mutableStateOf<LocalDate?>(null) }

    suspend fun refresh() {
        val loaded = withContext(Dispatchers.IO) {
            photoBox.query().orderDesc(ProgressPhotoEntity_.takenAt).build().use { it.find() }
        }
        withContext(Dispatchers.Main) { rows = loaded }
    }

    LaunchedEffect(Unit) { refresh() }

    val dateComparePhotos = remember(rows, compareDateA, compareDateB) {
        resolveDateComparePhotos(
            rows = rows,
            selectedA = compareDateA,
            selectedB = compareDateB,
        )
    }
    val activeSelectedA = if (calendarCompareMode) dateComparePhotos.first else selectedA
    val activeSelectedB = if (calendarCompareMode) dateComparePhotos.second else selectedB
    val activeCompareDateSet = remember(compareDateA, compareDateB) {
        buildSet {
            compareDateA?.let { add(it) }
            compareDateB?.let { add(it) }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val dateForPhoto = pendingPhotoDate
        pendingPhotoDate = null
        scope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val now = System.currentTimeMillis()
            val takenAt = dateForPhoto
                ?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: now
            // Compress/resize to max 1080px before storing
            val savedUri = compressAndSavePhoto(context, uri) ?: uri
            photoBox.put(
                ProgressPhotoEntity().apply {
                    uid = newUid()
                    fileUri = savedUri.toString()
                    this.takenAt = takenAt
                    createdAt = now
                    updatedAt = now
                    notes = ""
                },
            )
            refresh()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (!success || uri == null) { pendingCameraUri = null; pendingPhotoDate = null; return@rememberLauncherForActivityResult }
        // Capture before launch — pendingPhotoDate is nulled on the main thread immediately
        // after scope.launch returns, so reading it inside Dispatchers.IO would race.
        val capturedDate = pendingPhotoDate
        pendingCameraUri = null
        pendingPhotoDate = null
        scope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val takenAt = capturedDate
                ?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: now
            // Compress/resize camera JPEG to max 1080px and save to app private storage
            val savedUri = compressAndSavePhoto(context, uri) ?: uri
            if (savedUri != uri) deleteOwnedProgressPhoto(context, uri)
            photoBox.put(
                ProgressPhotoEntity().apply {
                    uid = newUid()
                    fileUri = savedUri.toString()
                    this.takenAt = takenAt
                    createdAt = now
                    updatedAt = now
                    notes = ""
                },
            )
            refresh()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(c.bg).statusBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "PROGRESS PHOTOS", onBack = onBack) }

        // ── Month calendar ────────────────────────────────────────────────────
        item {
            val photoDates = remember(rows) {
                rows.map { r -> progressPhotoLocalDate(r.takenAt) }.toSet()
            }
            PhotoCalendar(
                month = calendarMonth,
                photoDates = photoDates,
                selectedDate = calendarFilter,
                compareDateA = compareDateA,
                compareDateB = compareDateB,
                compareMode = calendarCompareMode,
                onPrevMonth = { calendarMonth = calendarMonth.minusMonths(1) },
                onNextMonth = { calendarMonth = calendarMonth.plusMonths(1) },
                onDayClick = { day ->
                    if (calendarCompareMode) {
                        val updated = updateDateCompareSelection(compareDateA, compareDateB, day)
                        compareDateA = updated.first
                        compareDateB = updated.second
                    } else {
                        calendarFilter = if (calendarFilter == day) null else day
                    }
                },
                onAddForDay = { day -> showAddPhotoDialog = day },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        calendarCompareMode = false
                        compareDateA = null
                        compareDateB = null
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Filter day", color = if (!calendarCompareMode) c.accent else c.muted) }
                TextButton(
                    onClick = {
                        calendarCompareMode = true
                        calendarFilter = null
                        selectedA = null
                        selectedB = null
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Compare dates", color = if (calendarCompareMode) c.accent else c.muted) }
            }
        }
        if (calendarCompareMode) {
            item {
                Text(
                    when {
                        compareDateA == null -> "Tap a date with a photo to set compare slot A."
                        compareDateB == null -> "Tap a second date to build the side-by-side compare."
                        else -> "Comparing the latest photo from each selected date."
                    },
                    color = c.muted,
                    fontSize = IronLogType.meta.fontSize.sp,
                )
            }
        }

        // ── Add photo buttons ─────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val out = createProgressPhotoUri(context)
                        pendingCameraUri = out
                        cameraLauncher.launch(out)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Camera") }
                Button(
                    onClick = { photoPicker.launch(arrayOf("image/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text("From Device") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val latest = rows.firstOrNull() ?: return@Button
                        val uri = runCatching { Uri.parse(latest.fileUri) }.getOrNull() ?: return@Button
                        context.startActivity(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Ironlog progress photo")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            },
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Share Latest") }
                Button(
                    enabled = !isExportingZip && rows.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            isExportingZip = true
                            runCatching {
                                val zipUri = buildProgressPhotosZip(context, rows)
                                if (zipUri != null) {
                                    context.startActivity(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "application/zip"
                                            putExtra(Intent.EXTRA_STREAM, zipUri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Ironlog progress photos")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        },
                                    )
                                }
                            }
                            isExportingZip = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    if (isExportingZip) CircularProgressIndicator(modifier = androidx.compose.ui.Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Export All")
                }
                Button(
                    onClick = { showClearAllConfirm = true },
                    modifier = Modifier.weight(1f),
                ) { Text("Clear All") }
            }
        }

        // ── Compare action bar ────────────────────────────────────────────────
        if (activeSelectedA != null || activeSelectedB != null || compareDateA != null || compareDateB != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.accentSoft)
                        .border(1.dp, c.accentBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Slot A
                    Column(Modifier.weight(1f)) {
                        Text("A", color = c.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            when {
                                calendarCompareMode && compareDateA != null -> "${compareDateA}  ${activeSelectedA?.let { formatTakenAt(it.takenAt) } ?: ""}".trim()
                                else -> activeSelectedA?.let { formatTakenAt(it.takenAt) } ?: "Tap photo to select"
                            },
                            color = if (activeSelectedA != null || compareDateA != null) c.text else c.muted,
                            fontSize = IronLogType.meta.fontSize.sp,
                            maxLines = 1,
                        )
                    }
                    Text("vs", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    // Slot B
                    Column(Modifier.weight(1f)) {
                        Text("B", color = c.accent, fontSize = IronLogType.micro.fontSize.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            when {
                                calendarCompareMode && compareDateB != null -> "${compareDateB}  ${activeSelectedB?.let { formatTakenAt(it.takenAt) } ?: ""}".trim()
                                else -> activeSelectedB?.let { formatTakenAt(it.takenAt) } ?: "Tap second photo"
                            },
                            color = if (activeSelectedB != null || compareDateB != null) c.text else c.muted,
                            fontSize = IronLogType.meta.fontSize.sp,
                            maxLines = 1,
                        )
                    }
                    // Compare CTA
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeSelectedB != null) c.accent else c.faint)
                            .clickable(enabled = activeSelectedB != null) {
                                showViewer = true
                                viewerStartIndex = rows.indexOfFirst { it.uid == activeSelectedA?.uid }.coerceAtLeast(0)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "COMPARE",
                            color = if (activeSelectedB != null) c.textOnAccent else c.muted,
                            fontSize = IronLogType.micro.fontSize.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }

        val displayRows = if (calendarCompareMode && activeCompareDateSet.isNotEmpty()) {
            rows.filter { r -> progressPhotoLocalDate(r.takenAt) in activeCompareDateSet }
        } else if (calendarFilter != null) {
            rows.filter { r ->
                progressPhotoLocalDate(r.takenAt) == calendarFilter
            }
        } else rows

        if (displayRows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when {
                            calendarCompareMode && activeCompareDateSet.isNotEmpty() -> "No photos found on the selected compare dates."
                            calendarFilter != null -> "No photos on $calendarFilter"
                            else -> "No progress photos yet.\nTap Camera or From Device to add one."
                        },
                        color = c.muted,
                        fontSize = IronLogType.body.fontSize.sp,
                    )
                }
            }
        } else {
            items(displayRows, key = { it.uid }) { row ->
                val isA = activeSelectedA?.uid == row.uid
                val isB = activeSelectedB?.uid == row.uid
                val isSelected = isA || isB
                Card(
                    colors = CardDefaults.cardColors(containerColor = c.card),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) c.accent else c.cardBorder,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (calendarCompareMode) {
                                    calendarCompareMode = false
                                    compareDateA = null
                                    compareDateB = null
                                }
                                when {
                                    isA -> selectedA = null
                                    isB -> selectedB = null
                                    selectedA == null -> selectedA = row
                                    selectedB == null -> selectedB = row
                                    else -> { selectedA = row; selectedB = null }
                                }
                            }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // ── Thumbnail ─────────────────────────────────────────
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(c.faint),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = row.fileUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            // A / B badge
                            if (isA || isB) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(c.accent),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (isA) "A" else "B",
                                        color = c.textOnAccent,
                                        fontSize = IronLogType.micro.fontSize.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }

                        // ── Info + actions ────────────────────────────────────
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                formatTakenAt(row.takenAt),
                                color = c.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = IronLogType.body.fontSize.sp,
                            )
                            if (row.notes.isNotBlank() && row.notes != "Imported from gallery" && row.notes != "Captured from camera") {
                                Text(
                                    row.notes,
                                    color = c.muted,
                                    fontSize = IronLogType.micro.fontSize.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                if (isSelected) if (isA) "Selected as A" else "Selected as B"
                                else "Tap to select for compare",
                                color = if (isSelected) c.accent else c.muted,
                                fontSize = IronLogType.meta.fontSize.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        if (calendarCompareMode) {
                                            calendarCompareMode = false
                                            compareDateA = null
                                            compareDateB = null
                                        }
                                        viewerStartIndex = rows.indexOfFirst { it.uid == row.uid }.coerceAtLeast(0)
                                        if (selectedA == null) selectedA = row
                                        showViewer = true
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) { Text("View", fontSize = IronLogType.meta.fontSize.sp) }
                                TextButton(
                                    onClick = {
                                        val uid = row.uid
                                        if (selectedA?.uid == uid) selectedA = null
                                        if (selectedB?.uid == uid) selectedB = null
                                        if (compareDateA != null && progressPhotoLocalDate(row.takenAt) == compareDateA) compareDateA = null
                                        if (compareDateB != null && progressPhotoLocalDate(row.takenAt) == compareDateB) compareDateB = null
                                        // Single coroutine so refresh runs after remove completes.
                                        scope.launch(Dispatchers.IO) {
                                            photoBox.remove(row)
                                            refresh()
                                        }
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) { Text("Delete", color = c.danger, fontSize = IronLogType.meta.fontSize.sp) }
                            }
                        }
                    }
                }
            }
        }
        // Bottom scroll clearance
        item { Spacer(Modifier.height(16.dp).navigationBarsPadding()) }
    }

    if (showViewer && activeSelectedA != null) {
        val pagerState = rememberPagerState(
            initialPage = viewerStartIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0)),
            pageCount = { rows.size.coerceAtLeast(1) },
        )
        // Full-screen overlay viewer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bg.copy(alpha = 0.97f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (activeSelectedB != null) "BEFORE / AFTER" else "PHOTO VIEWER",
                        color = c.accent,
                        fontSize = IronLogType.eyebrow.fontSize.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                    )
                    TextButton(onClick = { showViewer = false }) {
                        Text("CLOSE", color = c.muted, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (activeSelectedB == null) {
                    // Single photo pager
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val row = rows.getOrNull(page)
                            AsyncImage(
                                model = row?.fileUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    val currentPhoto = rows.getOrNull(pagerState.currentPage)
                    Text(
                        "${pagerState.currentPage + 1} / ${rows.size}  •  ${currentPhoto?.let { formatTakenAt(it.takenAt) } ?: ""}",
                        color = c.subtext,
                        fontSize = IronLogType.meta.fontSize.sp,
                    )
                    var notesDraft by remember(currentPhoto?.uid) { mutableStateOf(currentPhoto?.notes.orEmpty()) }
                    OutlinedTextField(
                        value = notesDraft,
                        onValueChange = { notesDraft = it },
                        label = { Text("Notes", color = c.muted) },
                        placeholder = { Text("Add a note for this photo…", color = c.muted, fontSize = IronLogType.body.fontSize.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                    if (notesDraft != currentPhoto?.notes.orEmpty()) {
                        Button(
                            onClick = {
                                val photo = currentPhoto ?: return@Button
                                scope.launch(Dispatchers.IO) {
                                    photo.notes = notesDraft
                                    photo.updatedAt = System.currentTimeMillis()
                                    photoBox.put(photo)
                                    refresh()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("SAVE NOTE") }
                    }
                } else {
                    // Before / after compare with slider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(c.card),
                    ) {
                        AsyncImage(
                            model = activeSelectedA?.fileUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
                            contentDescription = "Before",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        AsyncImage(
                            model = activeSelectedB?.fileUri?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) },
                            contentDescription = "After",
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    val revealWidth = size.width * compareMix
                                    clipRect(left = 0f, top = 0f, right = revealWidth, bottom = size.height) {
                                        this@drawWithContent.drawContent()
                                    }
                                },
                            contentScale = ContentScale.Crop,
                        )
                        // A / B labels
                        Row(
                            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(c.bg.copy(alpha = 0.75f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            // GAP-08: slider right → more B (After) visible; labels corrected
                            ) { Text("◀  A", color = c.text, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold) }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(c.bg.copy(alpha = 0.75f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) { Text("B  ▶", color = c.text, fontSize = IronLogType.meta.fontSize.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Slider(value = compareMix, onValueChange = { compareMix = it })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("B: ${activeSelectedB?.let { formatTakenAt(it.takenAt) }}", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                        Text("A: ${activeSelectedA?.let { formatTakenAt(it.takenAt) }}", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    }
                }
            }
        }
    }

    // Add-photo-for-day dialog (empty calendar day tap)
    showAddPhotoDialog?.let { dayToAdd ->
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAddPhotoDialog = null },
            title = { Text("Add photo for ${fmt.format(dayToAdd)}") },
            text = { Text("Choose how to add a photo for this date.") },
            confirmButton = {
                TextButton(onClick = {
                    showAddPhotoDialog = null
                    pendingPhotoDate = dayToAdd
                    val out = createProgressPhotoUri(context)
                    pendingCameraUri = out
                    cameraLauncher.launch(out)
                }) { Text("Camera") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        showAddPhotoDialog = null
                        pendingPhotoDate = dayToAdd
                        photoPicker.launch(arrayOf("image/*"))
                    }) { Text("Gallery") }
                    TextButton(onClick = { showAddPhotoDialog = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (showClearAllConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Clear all progress photos?") },
            text = { Text("This deletes all saved progress photos permanently.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearAllConfirm = false
                    scope.launch(Dispatchers.IO) {
                        // Collect file URIs before removing from DB
                        val fileUris = runCatching {
                            photoBox.query().build().find().mapNotNull { it.fileUri }
                        }.getOrElse { emptyList() }
                        photoBox.removeAll()
                        // Delete underlying files from storage.
                        // URIs are content:// from FileProvider — .path returns the provider's
                        // virtual path, not a real filesystem path. Reconstruct from filesDir.
                        fileUris.forEach { uriStr ->
                            runCatching {
                                val uri = android.net.Uri.parse(uriStr)
                                when (uri.scheme) {
                                    "file" -> java.io.File(uri.path!!).delete()
                                    else -> {
                                        // Last path segment is the filename; files live in
                                        // context.filesDir/progress_photos/
                                        val filename = uri.lastPathSegment
                                        if (filename != null) {
                                            java.io.File(context.filesDir, "progress_photos/$filename").delete()
                                        }
                                    }
                                }
                            }
                        }
                        selectedA = null
                        selectedB = null
                        compareDateA = null
                        compareDateB = null
                        refresh()
                    }
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PhotoCalendar(
    month: YearMonth,
    photoDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    compareDateA: LocalDate?,
    compareDateB: LocalDate?,
    compareMode: Boolean,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    onAddForDay: (LocalDate) -> Unit = {},
) {
    val c = useTheme()
    val today = LocalDate.now()
    val firstOfMonth = month.atDay(1)
    // Start week on Monday (ISO): Monday=1 … Sunday=7; offset so col 0 = Monday
    val startOffset = (firstOfMonth.dayOfWeek.value - 1)
    val daysInMonth = month.lengthOfMonth()
    val totalCells = startOffset + daysInMonth
    val rows = (totalCells + 6) / 7 // ceiling

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(IronLogRadius.lg.dp))
            .background(c.card)
            .border(1.dp, c.cardBorder, RoundedCornerShape(IronLogRadius.lg.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Month navigation ─────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevMonth) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month", tint = c.muted, modifier = Modifier.size(20.dp))
            }
            Text(
                "${month.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())} ${month.year}",
                color = c.text,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = IronLogType.section.fontSize.sp,
            )
            IconButton(onClick = onNextMonth) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next month", tint = c.muted, modifier = Modifier.size(20.dp))
            }
        }

        // ── Day-of-week headers ──────────────────────────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M","T","W","T","F","S","S").forEach { lbl ->
                Box(modifier = Modifier.weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(lbl, color = c.muted, fontSize = IronLogType.micro.fontSize.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                }
            }
        }

        // ── Calendar grid ────────────────────────────────────────────────────
        repeat(rows) { rowIdx ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { colIdx ->
                    val cellIdx = rowIdx * 7 + colIdx
                    val dayNum = cellIdx - startOffset + 1
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val date = month.atDay(dayNum)
                            val hasPhoto = date in photoDates
                            val isSelected = date == selectedDate
                            val isCompareA = date == compareDateA
                            val isCompareB = date == compareDateB
                            val isToday = date == today
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> c.accent
                                            isCompareA -> c.accent
                                            isCompareB -> c.accentSoft
                                            hasPhoto   -> c.accentSoft
                                            else       -> androidx.compose.ui.graphics.Color.Transparent
                                        }
                                    )
                                    .then(
                                        if ((isToday && !isSelected && !isCompareA) || isCompareB)
                                            Modifier.border(1.dp, c.accentBorder, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { if (hasPhoto) onDayClick(date) else onAddForDay(date) },
                                contentAlignment = androidx.compose.ui.Alignment.Center,
                            ) {
                                Text(
                                    "$dayNum",
                                    color = when {
                                        isSelected -> c.textOnAccent
                                        isCompareA -> c.textOnAccent
                                        hasPhoto   -> c.accent
                                        else       -> c.subtext
                                    },
                                    fontSize = IronLogType.meta.fontSize.sp,
                                    fontWeight = if (hasPhoto || isToday)
                                        androidx.compose.ui.text.font.FontWeight.Bold
                                    else androidx.compose.ui.text.font.FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Legend ────────────────────────────────────────────────────────────
        if (photoDates.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accentSoft).border(1.dp, c.accent, CircleShape))
                Text(
                    if (compareMode) "has photo  ·  tap two dates to compare"
                    else "has photo  ·  tap to filter",
                    color = c.muted,
                    fontSize = IronLogType.micro.fontSize.sp,
                )
            }
        }
    }
}

private fun createProgressPhotoUri(context: android.content.Context): Uri {
    val dir = File(context.filesDir, "progress_photos").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val file = File(dir, "progress_$stamp.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun deleteOwnedProgressPhoto(context: android.content.Context, uri: Uri) {
    if (uri.authority != "${context.packageName}.fileprovider") return
    val filename = uri.lastPathSegment?.substringAfterLast('/') ?: return
    File(context.filesDir, "progress_photos/$filename").takeIf { it.isFile }?.delete()
}

private fun formatTakenAt(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(Date(millis))
}

/** Reads [sourceUri], downscales to [maxDim]px on the longest side, re-saves as JPEG at [quality]% in app storage. Returns the saved file Uri. */
private suspend fun compressAndSavePhoto(
    context: android.content.Context,
    sourceUri: Uri,
    maxDim: Int = 1080,
    quality: Int = 85,
): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val photoDir = File(context.filesDir, "progress_photos").also { it.mkdirs() }
        val outFile = File(photoDir, "photo_${System.currentTimeMillis()}.jpg")

        // Decode bounds first to compute sample size
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(sourceUri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
        val rawW = opts.outWidth; val rawH = opts.outHeight
        if (rawW <= 0 || rawH <= 0) return@runCatching null

        // Sample size: power-of-2 that brings longest dim <= maxDim*2 (decode smaller)
        val longest = maxOf(rawW, rawH)
        var sample = 1
        while (longest / (sample * 2) > maxDim) sample *= 2
        val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(sourceUri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return@runCatching null

        // Scale down to maxDim if still larger
        val bmp = if (maxOf(decoded.width, decoded.height) > maxDim) {
            val scale = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
            val w = (decoded.width * scale).toInt(); val h = (decoded.height * scale).toInt()
            val scaled = android.graphics.Bitmap.createScaledBitmap(decoded, w, h, true)
            decoded.recycle()
            scaled
        } else decoded

        FileOutputStream(outFile).use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, it) }
        bmp.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
    }.getOrNull()
}

private suspend fun buildProgressPhotosZip(
    context: android.content.Context,
    rows: List<ProgressPhotoEntity>,
): Uri? = withContext(Dispatchers.IO) {
    runCatching {
        val cacheDir = File(context.cacheDir, "photo_exports").also { it.mkdirs() }
        val zipFile = File(cacheDir, "ironlog_progress_photos.zip")
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            rows.forEachIndexed { idx, row ->
                val entryName = "photo_${dateFmt.format(Date(row.takenAt))}_${idx + 1}.jpg"
                runCatching {
                    val uri = Uri.parse(row.fileUri)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        zos.putNextEntry(ZipEntry(entryName))
                        input.copyTo(zos)
                        zos.closeEntry()
                    }
                }
            }
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
    }.getOrNull()
}


