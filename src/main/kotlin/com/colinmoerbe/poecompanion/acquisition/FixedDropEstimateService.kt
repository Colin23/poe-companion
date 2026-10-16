package com.colinmoerbe.poecompanion.acquisition

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Publishes canonical fixed-probability knowledge and retrieves currently accepted estimates for an exact scope.
 *
 * Selection across overlapping scopes and composition with CAN_DROP/source availability belong to a later read model.
 */
@Service
class FixedDropEstimateService internal constructor(private val persistencePort: FixedDropEstimatePersistencePort) {
    @Transactional
    fun publish(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: FixedDropEstimateSnapshot,
        expectedActiveEstimateId: DropEstimateId?,
    ) {
        require(snapshot.id != expectedActiveEstimateId) {
            "A replacement estimate must have a new identity"
        }
        val relationship =
            requireNotNull(persistencePort.findDropRelationshipById(dropRelationshipId)) {
                "Unknown drop relationship: $dropRelationshipId"
            }

        // Reuse the exact domain invariants before writing; the database enforces them independently.
        FixedDropEstimateTimeline(
            dropRelationship = relationship,
            scope = scope,
            attemptDefinition = CompletedBossEncounterAttempt(relationship.acquisitionSourceId),
            snapshots = listOf(snapshot),
        )
        persistencePort.publish(dropRelationshipId, scope, snapshot, expectedActiveEstimateId)
    }

    @Transactional(readOnly = true)
    fun findAcceptedTimeline(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): FixedDropEstimateTimeline? = persistencePort.findAcceptedTimeline(dropRelationshipId, scope)
}
