package com.colinmoerbe.poecompanion.acquisition.relationship

import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.relationship.internal.DropRelationshipKnowledgePersistencePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Curates accepted relationship applicability facts and reconstructs exact-scope game-patch timelines.
 *
 * Cross-scope applicability selection and source-availability composition remain separate read-model concerns.
 */
@Service
class DropRelationshipKnowledgeService internal constructor(
    private val persistencePort: DropRelationshipKnowledgePersistencePort,
) {
    @Transactional
    fun recordSnapshot(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: DropRelationshipSnapshot,
        expectedRevision: Long?,
    ): Long {
        require(expectedRevision == null || expectedRevision >= 0) { "Expected revision must not be negative" }
        require(persistencePort.relationshipExists(dropRelationshipId)) {
            "Drop relationship must exist before its applicability knowledge can be curated"
        }

        // Apply the existing timeline domain validation, including matching patch compatibility.
        DropRelationshipTimeline(dropRelationshipId, scope, listOf(snapshot))
        return persistencePort.recordSnapshot(dropRelationshipId, scope, snapshot, expectedRevision)
    }

    @Transactional(readOnly = true)
    fun findAcceptedChangePoints(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedDropRelationshipChangePoint> = persistencePort.findAcceptedChangePoints(dropRelationshipId, scope)

    @Transactional(readOnly = true)
    fun findTimeline(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): DropRelationshipTimeline? {
        val snapshots = findAcceptedChangePoints(dropRelationshipId, scope).map { it.snapshot }
        return if (snapshots.isEmpty()) {
            null
        } else {
            DropRelationshipTimeline(dropRelationshipId, scope, snapshots)
        }
    }
}
