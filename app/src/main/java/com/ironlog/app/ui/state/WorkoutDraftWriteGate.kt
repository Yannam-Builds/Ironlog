package com.ironlog.app.ui.state

/** Serializes short native writes with lifecycle snapshots and terminal-session invalidation. */
internal class WorkoutDraftWriteGate {
    private var revision = 0L
    private var paused = false

    @Synchronized fun capture(): Long? = if (paused) null else ++revision

    @Synchronized fun writeIfCurrent(captured: Long, write: () -> Unit) {
        if (!paused && captured == revision) write()
    }

    @Synchronized fun pause() {
        paused = true
        revision++
    }

    @Synchronized fun resume() {
        paused = false
    }
}
