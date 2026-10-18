package com.colinmoerbe.poecompanion.acquisition.encounter

import com.colinmoerbe.poecompanion.league.GamePatch

/**
 * Complete canonical boss-encounter knowledge that becomes current at one game-patch change point.
 *
 * A null effective patch is the baseline for the timeline's compatibility version.
 */
data class BossEncounterKnowledgeSnapshot(
    val effectiveFromPatch: GamePatch?,
    val displayLabel: String,
    val availability: AcquisitionSourceAvailability,
    val bossParticipation: BossParticipation,
) {
    init {
        require(displayLabel.isNotBlank()) { "Boss encounter display label must not be blank" }
    }
}
