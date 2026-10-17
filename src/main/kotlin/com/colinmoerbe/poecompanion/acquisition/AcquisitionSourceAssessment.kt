package com.colinmoerbe.poecompanion.acquisition

/**
 * Independent source-availability and source-to-Unique assessments for one canonical relationship.
 *
 * Neither relationship identity nor known source availability implies CAN_DROP.
 */
data class AcquisitionSourceAssessment(
    val relationship: DropRelationship,
    val sourceKnowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>,
    val relationshipKnowledge: ScopedAcquisitionKnowledge<DropRelationshipResolution>,
)
