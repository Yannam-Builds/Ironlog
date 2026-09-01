package com.ironlog.app.ui.screens.body

import com.ironlog.app.data.objectbox.ProgressPhotoEntity

internal sealed interface ProgressPhotoViewerState {
    data class Single(val photoId: String) : ProgressPhotoViewerState
    data class Compare(val beforeId: String, val afterId: String) : ProgressPhotoViewerState
}

internal fun resolveProgressPhotoViewer(
    state: ProgressPhotoViewerState,
    rows: List<ProgressPhotoEntity>,
): List<ProgressPhotoEntity> {
    val ids = when (state) {
        is ProgressPhotoViewerState.Single -> listOf(state.photoId)
        is ProgressPhotoViewerState.Compare -> listOf(state.beforeId, state.afterId)
    }
    return ids.map { id -> rows.firstOrNull { it.uid == id } ?: return emptyList() }
}

internal enum class PhotoViewerDismissal { CLOSE, CONFIRM_DISCARD, WAIT_FOR_SAVE }

internal fun photoViewerDismissal(savedNote: String, draft: String, saving: Boolean): PhotoViewerDismissal = when {
    saving -> PhotoViewerDismissal.WAIT_FOR_SAVE
    savedNote != draft -> PhotoViewerDismissal.CONFIRM_DISCARD
    else -> PhotoViewerDismissal.CLOSE
}
