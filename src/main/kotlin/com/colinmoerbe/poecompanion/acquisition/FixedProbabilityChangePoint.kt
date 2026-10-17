package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.GamePatch

/**
 * A currently accepted game change point is either a known numeric estimate or an explicit invalidation.
 * Neither variant encodes an unknown probability as zero or a nullable numeric model.
 */
sealed interface FixedProbabilityChangePoint {
    val effectiveFromPatch: GamePatch?
    val publicationId: FixedProbabilityPublicationId

    data class Known(val estimate: FixedDropEstimateSnapshot) : FixedProbabilityChangePoint {
        override val effectiveFromPatch: GamePatch? get() = estimate.effectiveFromPatch
        override val publicationId: FixedProbabilityPublicationId
            get() = FixedProbabilityPublicationId.Estimate(estimate.id)
    }

    data class Withdrawn(val withdrawal: FixedProbabilityWithdrawal) : FixedProbabilityChangePoint {
        override val effectiveFromPatch: GamePatch? get() = withdrawal.effectiveFromPatch
        override val publicationId: FixedProbabilityPublicationId
            get() = FixedProbabilityPublicationId.Withdrawal(withdrawal.id)
    }
}
