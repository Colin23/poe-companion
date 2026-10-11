package com.colinmoerbe.poecompanion.acquisition

import java.util.UUID

/**
 * Stable application-owned identity of an independently meaningful acquisition opportunity.
 *
 * Display labels, provider identifiers, areas, and participating bosses do not define source identity.
 */
data class AcquisitionSourceId(val value: UUID) {
    companion object {
        /** Creates a new application-owned acquisition source identity. */
        fun generate(): AcquisitionSourceId = AcquisitionSourceId(UUID.randomUUID())
    }
}
