package com.colinmoerbe.poecompanion.build

import java.util.UUID

/**
 * Stable application-owned identity of a build archetype.
 */
data class BuildArchetypeId(val value: UUID) {
    companion object {
        /** Creates a new application-owned build archetype identity. */
        fun generate(): BuildArchetypeId = BuildArchetypeId(UUID.randomUUID())
    }
}
