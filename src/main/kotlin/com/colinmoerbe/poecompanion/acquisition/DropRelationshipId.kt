package com.colinmoerbe.poecompanion.acquisition

import java.util.UUID

/**
 * Stable application-owned identity of one canonical source-to-Unique drop relationship.
 */
data class DropRelationshipId(val value: UUID) {
    companion object {
        /** Creates a new application-owned drop relationship identity. */
        fun generate(): DropRelationshipId = DropRelationshipId(UUID.randomUUID())
    }
}
