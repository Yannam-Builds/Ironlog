package com.ironlog.app.data.repository

import org.junit.Assert.*
import org.junit.Test

class RestoreConsentTest {
    @Test fun `old merge preview cannot authorize replacement in same frame`() {
        val preview = RestoreImpact(ImportPreview(valid = true, replacementSafe = true), "hash", emptyMap(), "merge")
        assertFalse(canConfirmRestore(preview, "replace", "REPLACE"))
        assertTrue(canConfirmRestore(preview, "merge", ""))
    }
    @Test fun `replacement requires both safe preview and explicit confirmation`() {
        val preview = RestoreImpact(ImportPreview(valid = true, replacementSafe = true), "hash", emptyMap(), "replace")
        assertFalse(canConfirmRestore(preview, "replace", ""))
        assertFalse(canConfirmRestore(null, "replace", "REPLACE"))
        assertTrue(canConfirmRestore(preview, "replace", "REPLACE"))
        assertFalse(canConfirmRestore(preview.copy(preview = ImportPreview(valid = true)), "replace", "REPLACE"))
    }
}
