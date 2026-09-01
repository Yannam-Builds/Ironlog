package com.ironlog.app.ui.screens.stats

import androidx.compose.runtime.Composable
import com.ironlog.app.ui.components.CloudSummaryHost
import com.ironlog.app.ui.components.CloudSummaryState

internal typealias CloudStatsSummaryState = CloudSummaryState

/** Root-scoped so scrolling the card offscreen does not repeat a network request. */
@Composable
internal fun CloudStatsSummaryHost(
    enabled: Boolean,
    requestKey: Any?,
    load: suspend () -> String,
): CloudStatsSummaryState = CloudSummaryHost(enabled, requestKey, load)
