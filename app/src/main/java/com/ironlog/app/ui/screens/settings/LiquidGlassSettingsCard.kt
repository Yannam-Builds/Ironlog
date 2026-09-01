package com.ironlog.app.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironlog.app.ui.context.useTheme
import com.ironlog.app.ui.components.IronLogSwitch
import com.ironlog.app.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LiquidGlassSettingsCard(store: LiquidGlassStore = LiquidGlassRuntime.store(LocalContext.current)) {
    val enabled by store.enabled.collectAsStateWithLifecycle()
    val c = useTheme()
    var saving by remember(store) { mutableStateOf(false) }
    var saveError by remember(store) { mutableStateOf<String?>(null) }

    fun save(value: Boolean) {
        saving = true
        saveError = null
        AppearancePersistence.enqueue {
            try {
                withContext(Dispatchers.IO) { store.update(value) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                saveError = "Liquid glass navigation could not be saved. Please try again."
            } finally {
                saving = false
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .toggleable(value = enabled, enabled = !saving, role = Role.Switch, onValueChange = ::save)
                .appPadding(vertical = 12.dp),
            horizontalArrangement = appSpacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = appSpacedBy(4.dp)) {
                Text("Liquid glass navigation", color = c.text, style = MaterialTheme.typography.titleSmall)
                Text("Refractive glass and fluid tab highlights. Off restores the solid bar.",
                    color = c.subtext, style = MaterialTheme.typography.bodySmall)
            }
            IronLogSwitch(checked = enabled, onCheckedChange = null, enabled = !saving)
        }
        saveError?.let {
            Text(it, color = c.danger, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}
