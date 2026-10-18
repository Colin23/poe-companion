package com.colinmoerbe.poecompanion.acquisition.probability.internal

import com.colinmoerbe.poecompanion.acquisition.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimateTimeline
import com.colinmoerbe.poecompanion.acquisition.FixedProbabilityChangePoint
import com.colinmoerbe.poecompanion.acquisition.FixedProbabilityPublicationId

/**
 * Boundary for publishing immutable accepted probability knowledge and rebuilding exact-scope game timelines.
 */
internal interface FixedDropEstimatePersistencePort {
    fun findDropRelationshipById(id: DropRelationshipId): DropRelationship?

    /**
     * One active record (numeric or withdrawal) may occupy an exact game change point.
     * Null means the curator expects an empty slot; all races and stale expectations are explicit conflicts.
     */
    fun publish(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        changePoint: FixedProbabilityChangePoint,
        expectedActivePublicationId: FixedProbabilityPublicationId?,
    )

    fun findAcceptedTimeline(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): FixedDropEstimateTimeline?
}
