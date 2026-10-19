package com.colinmoerbe.poecompanion.acquisition.probability

import java.util.UUID

/**
 * Stable application-owned identity for one canonical drop estimate record.
 */
data class DropEstimateId(val value: UUID) {
    companion object {
        /** Creates a new canonical estimate identity. */
        fun generate(): DropEstimateId = DropEstimateId(UUID.randomUUID())
    }
}
