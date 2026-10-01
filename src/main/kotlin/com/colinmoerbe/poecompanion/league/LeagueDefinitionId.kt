package com.colinmoerbe.poecompanion.league

import java.util.UUID

/**
 * Stable application-owned identity of a PoE league definition.
 */
data class LeagueDefinitionId(val value: UUID) {
    companion object {
        /** Creates a new application-owned league definition identity. */
        fun generate(): LeagueDefinitionId = LeagueDefinitionId(UUID.randomUUID())
    }
}
