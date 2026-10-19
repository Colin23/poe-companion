package com.colinmoerbe.poecompanion.acquisition.probability

import com.colinmoerbe.poecompanion.league.GamePatch
import java.time.Instant

/**
 * Immutable curated evidence that a previous fixed probability is no longer justified from a game change point.
 * It does not mean that a drop is impossible or that the source or relationship became unavailable.
 */
data class FixedProbabilityWithdrawal(
    val id: FixedProbabilityWithdrawalId,
    val effectiveFromPatch: GamePatch?,
    val reason: String,
    val evidenceReference: String,
    val reviewedAt: Instant,
) {
    init {
        require(reason.isNotBlank()) { "Fixed probability withdrawal must explain the invalidation" }
        require(evidenceReference.isNotBlank()) { "Fixed probability withdrawal requires an evidence reference" }
    }
}
