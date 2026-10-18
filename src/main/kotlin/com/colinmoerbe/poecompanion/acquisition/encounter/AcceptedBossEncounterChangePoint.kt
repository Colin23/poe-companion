package com.colinmoerbe.poecompanion.acquisition.encounter

/**
 * The currently accepted complete encounter snapshot and its curator-edit concurrency token.
 */
data class AcceptedBossEncounterChangePoint(val snapshot: BossEncounterKnowledgeSnapshot, val revision: Long) {
    init {
        require(revision >= 0) { "Accepted boss encounter revision must not be negative" }
    }
}
