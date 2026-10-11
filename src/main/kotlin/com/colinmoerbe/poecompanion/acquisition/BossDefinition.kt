package com.colinmoerbe.poecompanion.acquisition

/**
 * Represents one canonical boss/game entity known to the Companion.
 *
 * Boss identity remains stable independently from the acquisition encounters in which the boss participates.
 */
class BossDefinition(val id: BossDefinitionId, val displayName: String) {
    init {
        require(displayName.isNotBlank()) { "Boss display name must not be blank" }
    }
}
