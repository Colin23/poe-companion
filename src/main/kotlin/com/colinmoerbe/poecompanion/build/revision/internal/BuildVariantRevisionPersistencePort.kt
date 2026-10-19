package com.colinmoerbe.poecompanion.build.revision.internal

import com.colinmoerbe.poecompanion.build.identity.BuildVariantId
import com.colinmoerbe.poecompanion.build.revision.BuildRevisionActivationConflictException
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevision
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevisionId
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevisionStatus
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
