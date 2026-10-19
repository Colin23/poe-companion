package com.colinmoerbe.poecompanion.acquisition.probability

import java.util.UUID

/** Application-owned identity for one immutable curated withdrawal of fixed-probability knowledge. */
data class FixedProbabilityWithdrawalId(val value: UUID) {
    companion object {
        fun generate(): FixedProbabilityWithdrawalId = FixedProbabilityWithdrawalId(UUID.randomUUID())
    }
}
