package com.ironlog.app.ui.theme

import org.junit.Assert.*
import org.junit.Test

class SpacingRoleTest {
    @Test fun `legacy spacing becomes the fallback for every role`() {
        for (role in SpacingRole.entries) {
            assertEquals(.85f, role.initialValue(mapOf("spacing_scale" to .85f)), .001f)
            assertEquals(1f, role.initialValue(emptyMap<String, Any>()), .001f)
        }
    }

    @Test fun `roles override legacy independently and clamp malformed values`() {
        val preferences = mapOf("spacing_scale" to .85f, "spacing_cards" to 1.25f, "spacing_content" to Float.NaN)
        assertEquals(1.25f, SpacingRole.CARDS.initialValue(preferences), .001f)
        assertEquals(.85f, SpacingRole.PADDING.initialValue(preferences), .001f)
        assertEquals(1f, SpacingRole.CONTENT.initialValue(preferences), .001f)
        assertEquals(3, SpacingRole.entries.map { it.preferenceKey }.distinct().size)
    }

    @Test fun `saving one role never changes the other roles or legacy preference`() {
        val preferences = mutableMapOf<String, Any>("spacing_scale" to .9f)
        fun store(role: SpacingRole) = SpacingStore(object : SpacingStorage {
            override fun read() = role.initialValue(preferences)
            override fun write(scale: Float) { preferences[role.preferenceKey] = scale }
        })
        store(SpacingRole.CARDS).update(1.25f)
        assertEquals(1.25f, store(SpacingRole.CARDS).scale.value, .001f)
        assertEquals(.9f, store(SpacingRole.CONTENT).scale.value, .001f)
        assertEquals(.9f, store(SpacingRole.PADDING).scale.value, .001f)
        assertEquals(.9f, preferences["spacing_scale"] as Float, .001f)
    }
}
