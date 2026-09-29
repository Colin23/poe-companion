package com.colinmoerbe.poecompanion.catalog

import java.util.UUID

/**
 * Stable application-owned identity of a catalog unique definition.
 *
 * Display names and provider identifiers are not internal unique definition identity.
 */
data class UniqueDefinitionId(val value: UUID) {
    companion object {
        /** Creates a new application-owned unique definition identity. */
        fun generate(): UniqueDefinitionId = UniqueDefinitionId(UUID.randomUUID())
    }
}
