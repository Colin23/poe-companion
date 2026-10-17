package com.colinmoerbe.poecompanion.acquisition

/**
 * Persists accepted source-to-Unique applicability change points independently of relationship identity.
 */
internal interface DropRelationshipKnowledgePersistencePort {
    fun relationshipExists(id: DropRelationshipId): Boolean

    /**
     * A null expected revision creates an absent change point; a revision replaces that exact accepted fact.
     *
     * Replacing increments its revision; concurrent modifications raise [DropRelationshipKnowledgeConflictException].
     */
    fun recordSnapshot(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
        snapshot: DropRelationshipSnapshot,
        expectedRevision: Long?,
    ): Long

    fun findAcceptedChangePoints(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): List<AcceptedDropRelationshipChangePoint>
}
