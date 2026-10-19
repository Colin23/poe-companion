package com.colinmoerbe.poecompanion.acquisition.sourceidentity

import java.util.UUID

/**
 * Stable application-owned identity of a boss/game entity.
 *
 * Display names and provider identifiers are not boss identity.
 */
data class BossDefinitionId(val value: UUID) {
    companion object {
        /** Creates a new application-owned boss identity. */
        fun generate(): BossDefinitionId = BossDefinitionId(UUID.randomUUID())
    }
}
