package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.league.CompatibilityVersion

/**
 * Application-facing persistence boundary for build revision lifecycle operations.
 */
internal interface BuildVariantRevisionPersistencePort {
    fun findById(id: BuildVariantRevisionId): BuildVariantRevision?

    fun findActiveBy(
        buildVariantId: BuildVariantId,
        compatibilityVersion: CompatibilityVersion,
    ): BuildVariantRevision?

    /**
     * Persists one lifecycle transition only if the durable status still matches [expectedStatus].
     *
     * @throws BuildRevisionActivationConflictException when concurrent lifecycle state prevents the transition.
     */
    fun transitionStatus(
        id: BuildVariantRevisionId,
        expectedStatus: BuildVariantRevisionStatus,
        newStatus: BuildVariantRevisionStatus,
    )
}
