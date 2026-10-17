package com.colinmoerbe.poecompanion.acquisition

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
