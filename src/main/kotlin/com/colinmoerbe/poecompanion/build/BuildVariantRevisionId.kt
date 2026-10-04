package com.colinmoerbe.poecompanion.build

import java.util.UUID

/**
 * Stable application-owned identity of a build variant revision.
 */
data class BuildVariantRevisionId(val value: UUID) {
    companion object {
        /** Creates a new application-owned build variant revision identity. */
        fun generate(): BuildVariantRevisionId = BuildVariantRevisionId(UUID.randomUUID())
    }
}
