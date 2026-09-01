package com.ironlog.app.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** No DB polling; every fresh lifecycle collection immediately samples the clock. */
internal fun presentationClock(clock: Clock, intervalMs: Long = 60_000L): Flow<Long> = flow {
    require(intervalMs > 0L)
    while (true) { emit(clock.millis()); delay(intervalMs) }
}

@Composable
internal fun rememberPresentationTime(clock: Clock? = null): State<Long> {
    val resolved = remember(clock) { clock ?: Clock.systemDefaultZone() }
    val ticks = remember(resolved) { presentationClock(resolved) }
    return ticks.collectAsStateWithLifecycle(initialValue = resolved.millis())
}
