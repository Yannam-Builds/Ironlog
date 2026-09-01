package com.ironlog.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.data.repository.NextSessionNoteRepository
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.theme.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Never automatically overwrites plan instructions or the current draft. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NextSessionNoteControl(exerciseId: String, currentNote: String, onUseNote: (String) -> Unit) {
    if (exerciseId.isBlank()) return
    val repository = remember { NextSessionNoteRepository() }
    val source = remember(repository, exerciseId) { repository.observe(exerciseId) }
    val saved by source.collectAsStateWithLifecycle(initialValue = "")
    val colors = useTheme()
    val scope = rememberCoroutineScope()
    var saving by remember(exerciseId) { mutableStateOf(false) }
    var error by remember(exerciseId) { mutableStateOf<String?>(null) }
    fun save(value: String) {
        saving = true
        error = null
        scope.launch {
            try { repository.save(exerciseId, value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "Reminder could not be saved. Retry." }
            finally { saving = false }
        }
    }
    if (saved.isNotBlank() || currentNote.isNotBlank() || error != null) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (saved.isNotBlank()) {
                Text("NEXT-SESSION NOTE", color = colors.muted, fontSize = 12.sp)
                Text(saved, color = colors.text, fontSize = 14.sp)
                Text("Kept for future sessions until cleared.", color = colors.muted, fontSize = 12.sp)
            }
            error?.let { Text(it, color = colors.danger, fontSize = 12.sp) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentNote.isNotBlank() && currentNote.trim() != saved) TextButton(
                    enabled = !saving, onClick = { save(currentNote) }, modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(if (saving) "Saving…" else "Remember note") }
                if (saved.isNotBlank()) {
                    if (saved != currentNote) TextButton(enabled = !saving, onClick = { onUseNote(saved) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Use this session") }
                    TextButton(enabled = !saving, onClick = { save("") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Clear reminder") }
                }
            }
        }
    }
}
