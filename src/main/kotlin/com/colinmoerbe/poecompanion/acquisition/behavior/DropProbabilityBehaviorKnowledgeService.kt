package com.colinmoerbe.poecompanion.acquisition.behavior

import com.colinmoerbe.poecompanion.acquisition.behavior.internal.DropProbabilityBehaviorKnowledgePersistencePort
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.security.authorization.CanonicalCurationAuthorization
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ADMIN-curated probability behavior for one known drop relationship and precise knowledge scope.
 *
 * This service never creates a numeric probability or derives CAN_DROP and source availability.
 */
@Service
class DropProbabilityBehaviorKnowledgeService internal constructor(
    private val persistencePort: DropProbabilityBehaviorKnowledgePersistencePort,
    private val authorization: CanonicalCurationAuthorization,
) {
    @Transactional
    fun recordSnapshot(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: DropProbabilityBehaviorSnapshot,
        expectedRevision: Long?,
    ): Long {
        authorization.requireAdmin()
        require(expectedRevision == null || expectedRevision >= 0) { "Expected revision must not be negative" }
        require(persistencePort.relationshipExists(dropRelationshipId)) {
            "Relationship must exist before its probability behavior can be curated"
        }
        DropProbabilityBehaviorTimeline(dropRelationshipId, scope, listOf(snapshot))
        return persistencePort.recordSnapshot(dropRelationshipId, scope, snapshot, expectedRevision)
    }

    @Transactional(readOnly = true)
    fun findAcceptedChangePoints(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedDropProbabilityBehaviorChangePoint> = persistencePort.findAcceptedChangePoints(dropRelationshipId, scope)

    @Transactional(readOnly = true)
    fun findTimeline(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): DropProbabilityBehaviorTimeline? {
        val snapshots = findAcceptedChangePoints(dropRelationshipId, scope).map { it.snapshot }
        return if (snapshots.isEmpty()) null else DropProbabilityBehaviorTimeline(dropRelationshipId, scope, snapshots)
    }
}
