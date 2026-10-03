package com.colinmoerbe.poecompanion.build

import java.util.UUID

/**
 * Stable application-owned identity of a build variant.
 */
data class BuildVariantId(val value: UUID) {
    companion object {
        /** Creates a new application-owned build variant identity. */
        fun generate(): BuildVariantId = BuildVariantId(UUID.randomUUID())
    }
}
