package com.colinmoerbe.poecompanion.acquisition.behavior.internal

import com.colinmoerbe.poecompanion.acquisition.behavior.AcceptedDropProbabilityBehaviorChangePoint
import com.colinmoerbe.poecompanion.acquisition.behavior.DropProbabilityBehaviorSnapshot
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId

/** Dedicated canonical behavior knowledge persistence; numeric drop rates remain a separate authority. */
internal interface DropProbabilityBehaviorKnowledgePersistencePort {
    fun relationshipExists(dropRelationshipId: DropRelationshipId): Boolean

    fun recordSnapshot(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: DropProbabilityBehaviorSnapshot,
        expectedRevision: Long?,
    ): Long

    fun findAcceptedChangePoints(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedDropProbabilityBehaviorChangePoint>
}
