package com.colinmoerbe.poecompanion.acquisition.overrides

import java.util.UUID

/**
 * Stable application-owned identity for one personal drop-rate override confirmation revision.
 */
data class DropRateOverrideRevisionId(val value: UUID) {
    companion object {
        /** Creates a new confirmed personal probability revision identity. */
        fun generate(): DropRateOverrideRevisionId = DropRateOverrideRevisionId(UUID.randomUUID())
    }
}
