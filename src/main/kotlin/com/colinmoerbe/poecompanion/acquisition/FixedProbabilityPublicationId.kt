package com.colinmoerbe.poecompanion.acquisition

import java.util.UUID

/** Type-safe identity of the currently accepted estimate OR withdrawal at an exact game change point. */
sealed interface FixedProbabilityPublicationId {
    val value: UUID

    data class Estimate(val estimateId: DropEstimateId) : FixedProbabilityPublicationId {
        override val value: UUID = estimateId.value
    }

    data class Withdrawal(val withdrawalId: FixedProbabilityWithdrawalId) : FixedProbabilityPublicationId {
        override val value: UUID = withdrawalId.value
    }
}
