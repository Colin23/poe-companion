package com.colinmoerbe.poecompanion.acquisition

/**
 * Application-facing boundary for canonical fixed-estimate publication and exact-scope reconstruction.
 */
internal interface FixedDropEstimatePersistencePort {
    fun findDropRelationshipById(id: DropRelationshipId): DropRelationship?

    /**
     * Publish one new immutable estimate, superseding only the exact existing active change point if expected.
     *
     * A null expected identity means that the caller observed no active estimate at the exact key.
     * Concurrent changes raise [FixedDropEstimatePublicationConflictException].
     */
    fun publish(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: FixedDropEstimateSnapshot,
        expectedActiveEstimateId: DropEstimateId?,
    )

    fun findAcceptedTimeline(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): FixedDropEstimateTimeline?
}
