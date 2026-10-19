package com.colinmoerbe.poecompanion.acquisition.probability.internal

import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateTimeline
import com.colinmoerbe.poecompanion.acquisition.probability.FixedProbabilityChangePoint
import com.colinmoerbe.poecompanion.acquisition.probability.FixedProbabilityPublicationId
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId

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
