package com.colinmoerbe.poecompanion.acquisition.probability

import com.colinmoerbe.poecompanion.league.GamePatch
import java.time.Instant

/**
 * One accepted fixed-probability estimate that becomes effective at a game-patch change point.
 *
 * V0.1 admits only fixed independent chances without unsupported modifier-dependent conditions. Curators must
 * substantiate that assumption; this value validates the numeric model and required evidence metadata.
 *
 * A null effective patch represents the baseline for the enclosing timeline's compatibility version.
 */
data class FixedDropEstimateSnapshot(
    val id: DropEstimateId,
    val effectiveFromPatch: GamePatch?,
    val model: FixedBernoulliModel,
    val provenance: DropEstimateProvenance,
    val evidenceReference: String,
    val reviewedAt: Instant,
) {
    init {
        require(evidenceReference.isNotBlank()) { "Canonical drop estimate must have an evidence reference" }
    }
}
