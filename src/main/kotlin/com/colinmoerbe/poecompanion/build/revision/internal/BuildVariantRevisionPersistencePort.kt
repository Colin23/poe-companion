package com.colinmoerbe.poecompanion.build.revision.internal

import com.colinmoerbe.poecompanion.build.BuildRevisionActivationConflictException
import com.colinmoerbe.poecompanion.build.BuildVariantId
import com.colinmoerbe.poecompanion.build.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.build.BuildVariantRevisionStatus
import com.colinmoerbe.poecompanion.league.CompatibilityVersion

/**
 * Application-facing persistence boundary for build revision lifecycle operations.
 */
internal interface BuildVariantRevisionPersistencePort {
    fun findById(id: BuildVariantRevisionId): BuildVariantRevision?

    fun findActiveBy(buildVariantId: BuildVariantId, compatibilityVersion: CompatibilityVersion): BuildVariantRevision?

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
