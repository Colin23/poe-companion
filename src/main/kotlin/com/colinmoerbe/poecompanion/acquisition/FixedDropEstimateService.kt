package com.colinmoerbe.poecompanion.acquisition

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Publishes known estimates and explicit withdrawals without inferring CAN_DROP or source availability.
 */
@Service
class FixedDropEstimateService internal constructor(private val persistencePort: FixedDropEstimatePersistencePort) {
    @Transactional
    fun publish(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: FixedDropEstimateSnapshot,
        expectedActivePublicationId: FixedProbabilityPublicationId?,
    ) = recordChangePoint(
        dropRelationshipId,
        scope,
        FixedProbabilityChangePoint.Known(snapshot),
        expectedActivePublicationId,
    )

    @Transactional
    fun withdraw(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        withdrawal: FixedProbabilityWithdrawal,
        expectedActivePublicationId: FixedProbabilityPublicationId?,
    ) = recordChangePoint(
        dropRelationshipId,
        scope,
        FixedProbabilityChangePoint.Withdrawn(withdrawal),
        expectedActivePublicationId,
    )

    private fun recordChangePoint(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        changePoint: FixedProbabilityChangePoint,
        expectedActivePublicationId: FixedProbabilityPublicationId?,
    ) {
        require(changePoint.publicationId.value != expectedActivePublicationId?.value) {
            "A replacement probability publication must have a new identity"
        }
        val relationship =
            requireNotNull(persistencePort.findDropRelationshipById(dropRelationshipId)) {
                "Unknown drop relationship: $dropRelationshipId"
            }
        FixedDropEstimateTimeline(
            dropRelationship = relationship,
            scope = scope,
            attemptDefinition = CompletedBossEncounterAttempt(relationship.acquisitionSourceId),
            snapshots =
            if (changePoint is FixedProbabilityChangePoint.Known) listOf(changePoint.estimate) else emptyList(),
            withdrawals = if (changePoint is FixedProbabilityChangePoint.Withdrawn) {
                listOf(changePoint.withdrawal)
            } else {
                emptyList()
            },
        )
        persistencePort.publish(dropRelationshipId, scope, changePoint, expectedActivePublicationId)
    }

    @Transactional(readOnly = true)
    fun findAcceptedTimeline(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): FixedDropEstimateTimeline? = persistencePort.findAcceptedTimeline(dropRelationshipId, scope)
}
