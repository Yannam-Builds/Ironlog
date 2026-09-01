package com.ironlog.app.ui.screens.body

import com.ironlog.app.ui.theme.appGapDp
import com.ironlog.app.ui.theme.appPadding
import com.ironlog.app.ui.theme.appSpacedBy
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.ironlog.app.ui.theme.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ironlog.app.data.objectbox.ObjectBox
import com.ironlog.app.data.objectbox.ProgressPhotoEntity
import com.ironlog.app.data.objectbox.ProgressPhotoEntity_
import com.ironlog.app.data.objectbox.newUid
import com.ironlog.app.data.photos.ProgressPhotoStorage
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
    var viewerState by remember { mutableStateOf<ProgressPhotoViewerState?>(null) }
    var viewerOpener by remember { mutableStateOf<FocusRequester?>(null) }
    val compareFocus = remember { FocusRequester() }
    var photoError by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    val photoStorage = remember(context) { ProgressPhotoStorage(context.filesDir, "${context.packageName}.fileprovider") }
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
        withContext(Dispatchers.Main) {
            rows = loaded
            selectedA = selectedA?.let { selected -> loaded.firstOrNull { it.uid == selected.uid } }
            selectedB = selectedB?.let { selected -> loaded.firstOrNull { it.uid == selected.uid } }
            if (viewerState?.let { resolveProgressPhotoViewer(it, loaded).isEmpty() } == true) viewerState = null
        }
    }

    fun dismissViewer() {
        viewerState = null
        scope.launch {
            withFrameNanos { }
            runCatching { viewerOpener?.requestFocus() }
        }
    }

    fun deletePhotos(targets: List<ProgressPhotoEntity>) {
        if (isDeleting) return
        isDeleting = true
        photoError = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    photoStorage.deletePhotos(targets) { photoBox.remove(it) }
                }
                refresh()
                val orphanFailures = withContext(Dispatchers.IO) {
                    photoStorage.reconcileOrphans(rows.map { it.fileUri }, System.currentTimeMillis() - 86_400_000L)
                }
                if (result.failedPhotoIds.isNotEmpty() || orphanFailures.isNotEmpty()) {
                    photoError = "Some photos could not be deleted. Please try again."
                }
                if (rows.isEmpty()) { compareDateA = null; compareDateB = null }
            } catch (exception: kotlinx.coroutines.CancellationException) {
                throw exception
            } catch (_: Exception) {
                photoError = "Could not finish deleting photos. Please try again."
            } finally {
                isDeleting = false
            }
        }
    }

    LaunchedEffect(Unit) {
        try { refresh() }
        catch (exception: kotlinx.coroutines.CancellationException) { throw exception }
        catch (_: Exception) { photoError = "Could not load progress photos. Please reopen this screen." }
    }

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
            val savedUri = compressAndSavePhoto(context, uri)
            if (savedUri == null) {
                withContext(Dispatchers.Main) { photoError = "Could not copy this photo. The original has not been changed." }
                return@launch
            }
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
            if (savedUri != uri && !photoStorage.deleteOwnedReference(uri.toString())) {
                withContext(Dispatchers.Main) { photoError = "Photo saved, but the temporary camera copy could not be removed." }
            }
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
        verticalArrangement = com.ironlog.app.ui.theme.appCardSpacedBy(12.dp),
    ) {
        item { ScreenHeader(title = "PROGRESS PHOTOS", onBack = onBack) }
        photoError?.let { message -> item { Text(message, color = c.danger) } }

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
            Row(horizontalArrangement = appSpacedBy(8.dp)) {
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
            Row(horizontalArrangement = appSpacedBy(8.dp)) {
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
            Row(horizontalArrangement = appSpacedBy(8.dp)) {
                Button(
                    onClick = {
                        val latest = rows.firstOrNull() ?: return@Button
                        val file = photoStorage.ownedFile(latest.fileUri)?.takeIf { it.isFile }
                        if (file == null) {
                            photoError = "This image is unavailable on this device."
                            return@Button
                        }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
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
                    enabled = !isDeleting,
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
                        .appPadding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = appSpacedBy(8.dp),
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
                            .focusRequester(compareFocus)
                            .clickable(enabled = activeSelectedB != null) {
                                val before = activeSelectedA ?: return@clickable
                                val after = activeSelectedB ?: return@clickable
                                viewerOpener = compareFocus
                                viewerState = ProgressPhotoViewerState.Compare(before.uid, after.uid)
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
                        .appPadding(vertical = 32.dp),
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
                val viewFocus = remember(row.uid) { FocusRequester() }
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
                            .appPadding(10.dp),
                        horizontalArrangement = appSpacedBy(12.dp),
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
                                model = photoStorage.ownedFile(row.fileUri)?.takeIf { it.isFile },
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
                            verticalArrangement = appSpacedBy(4.dp),
                        ) {
                            Text(
                                formatTakenAt(row.takenAt),
                                color = c.text,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = IronLogType.body.fontSize.sp,
                            )
                            if (photoStorage.ownedFile(row.fileUri)?.isFile != true) {
                                Text("Image unavailable on this device", color = c.muted, fontSize = IronLogType.micro.fontSize.sp)
                            }
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
                            Row(horizontalArrangement = appSpacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        viewerOpener = viewFocus
                                        viewerState = ProgressPhotoViewerState.Single(row.uid)
                                    },
                                    modifier = Modifier.focusRequester(viewFocus),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) { Text("View", fontSize = IronLogType.meta.fontSize.sp) }
                                TextButton(
                                    enabled = !isDeleting,
                                    onClick = { deletePhotos(listOf(row)) },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                ) { Text("Delete", color = c.danger, fontSize = IronLogType.meta.fontSize.sp) }
                            }
                        }
                    }
                }
            }
        }
        // Bottom scroll clearance
        item { Spacer(Modifier.height(appGapDp(16.dp)).navigationBarsPadding()) }
    }

    viewerState?.let { state ->
        val viewerPhotos = resolveProgressPhotoViewer(state, rows)
        if (viewerPhotos.isNotEmpty()) ProgressPhotoViewerDialog(
            photos = viewerPhotos,
            onDismiss = ::dismissViewer,
            onSaveNote = { photoId, note ->
                withContext(Dispatchers.IO) {
                    ObjectBox.store.runInTx {
                        val latest = photoBox.get(photoId) ?: error("Photo no longer exists")
                        check(latest.uid == viewerPhotos.first().uid) { "Photo has changed" }
                        latest.notes = note
                        latest.updatedAt = System.currentTimeMillis()
                        photoBox.put(latest)
                    }
                }
                refresh()
            },
        )
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
                Row(horizontalArrangement = appSpacedBy(4.dp)) {
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
                    deletePhotos(rows)
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
internal fun ProgressPhotoViewerDialog(
    photos: List<ProgressPhotoEntity>,
    onDismiss: () -> Unit,
    onSaveNote: suspend (Long, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = useTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val closeFocus = remember { FocusRequester() }
    val photo = photos.firstOrNull() ?: return
    val after = photos.getOrNull(1)
    val storage = remember(context) { ProgressPhotoStorage(context.filesDir, "${context.packageName}.fileprovider") }
    var compareMix by remember(photo.uid, after?.uid) { mutableStateOf(0.5f) }
    var savedNote by remember(photo.uid) { mutableStateOf(photo.notes) }
    var notesDraft by remember(photo.uid) { mutableStateOf(photo.notes) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    fun requestDismiss() {
        when (photoViewerDismissal(savedNote, notesDraft, saving)) {
            PhotoViewerDismissal.CLOSE -> onDismiss()
            PhotoViewerDismissal.CONFIRM_DISCARD -> { confirmDiscard = true }
            PhotoViewerDismissal.WAIT_FOR_SAVE -> Unit
        }
    }

    Dialog(
        onDismissRequest = ::requestDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val focusManager = LocalFocusManager.current
        BoxWithConstraints(
            modifier.fillMaxSize().background(c.bg).safeDrawingPadding().imePadding().padding(16.dp),
        ) {
            LaunchedEffect(photo.uid, after?.uid) { closeFocus.requestFocus() }
            // Fill the normal viewport, but grow beyond it when controls and the minimum photo slot
            // need more room. Intrinsic height keeps a weighted photo from collapsing in a scrollable column.
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight).height(IntrinsicSize.Min)
                    .semantics { paneTitle = if (after == null) "Photo viewer" else "Before and after comparison" },
                verticalArrangement = appSpacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (after == null) "PHOTO VIEWER" else "BEFORE / AFTER",
                        color = c.accent, fontSize = IronLogType.eyebrow.fontSize.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    TextButton(onClick = { focusManager.clearFocus(); requestDismiss() }, enabled = !saving, modifier = Modifier.focusRequester(closeFocus)) {
                        Text("CLOSE", color = c.muted)
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).heightIn(min = 160.dp).clip(RoundedCornerShape(16.dp)).background(c.card), contentAlignment = Alignment.Center) {
                    val beforeFile = storage.ownedFile(photo.fileUri)?.takeIf { it.isFile }
                    if (beforeFile == null) Text("Image unavailable on this device", color = c.muted)
                    else AsyncImage(
                        model = beforeFile,
                        contentDescription = if (after == null) "Progress photo taken ${formatTakenAt(photo.takenAt)}" else "Before photo",
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    )
                    if (after != null) {
                        val afterFile = storage.ownedFile(after.fileUri)?.takeIf { it.isFile }
                        if (afterFile == null) Text("After image unavailable on this device", color = c.muted, modifier = Modifier.align(Alignment.BottomCenter))
                        else AsyncImage(
                            model = afterFile, contentDescription = "After photo",
                            modifier = Modifier.fillMaxSize().drawWithContent {
                                clipRect(right = size.width * compareMix) { this@drawWithContent.drawContent() }
                            },
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                if (after == null) {
                    Text(formatTakenAt(photo.takenAt), color = c.subtext, fontSize = IronLogType.meta.fontSize.sp)
                    OutlinedTextField(
                        value = notesDraft,
                        onValueChange = { notesDraft = it; saveError = null },
                        enabled = !saving,
                        label = { Text("Notes", color = c.muted) },
                        modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3,
                    )
                    saveError?.let { Text(it, color = c.danger) }
                    if (notesDraft != savedNote || saving) Button(
                        enabled = !saving,
                        onClick = {
                            val idToSave = photo.objectBoxId
                            val noteToSave = notesDraft
                            saving = true
                            saveError = null
                            scope.launch {
                                try {
                                    onSaveNote(idToSave, noteToSave)
                                    savedNote = noteToSave
                                    focusManager.clearFocus()
                                } catch (exception: kotlinx.coroutines.CancellationException) {
                                    throw exception
                                } catch (_: Exception) {
                                    saveError = "Could not save note. Please try again."
                                } finally { saving = false }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (saving) "SAVING…" else "SAVE NOTE") }
                } else {
                    Slider(value = compareMix, onValueChange = { compareMix = it })
                    Text("Before: ${formatTakenAt(photo.takenAt)}", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                    Text("After: ${formatTakenAt(after.takenAt)}", color = c.muted, fontSize = IronLogType.meta.fontSize.sp)
                }
            }
        }
        if (confirmDiscard) AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard unsaved note?") },
            text = { Text("Your changes have not been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
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
            .appPadding(12.dp),
        verticalArrangement = appSpacedBy(8.dp),
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
                                    .appPadding(2.dp)
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
                horizontalArrangement = appSpacedBy(6.dp),
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
        val cacheDir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val zipFile = File(cacheDir, "ironlog_progress_photos.zip")
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val storage = ProgressPhotoStorage(context.filesDir, "${context.packageName}.fileprovider")
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            rows.forEachIndexed { idx, row ->
                val entryName = "photo_${dateFmt.format(Date(row.takenAt))}_${idx + 1}.jpg"
                runCatching {
                    val file = storage.ownedFile(row.fileUri)?.takeIf { it.isFile } ?: return@runCatching
                    file.inputStream().use { input ->
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


