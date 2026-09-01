package com.ironlog.app.ui.screens.plans

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.theme.Text

/** Native menu alternatives also work with TalkBack, switches, and keyboards. */
@Composable
internal fun PlanExerciseMoveActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isSaving: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text("Move up") },
        onClick = onMoveUp,
        enabled = canMoveUp && !isSaving,
        modifier = Modifier.heightIn(min = 48.dp),
    )
    DropdownMenuItem(
        text = { Text("Move down") },
        onClick = onMoveDown,
        enabled = canMoveDown && !isSaving,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}
