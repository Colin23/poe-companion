package com.colinmoerbe.poecompanion.acquisition

/**
 * Independent source-availability and source-to-Unique assessments for one canonical relationship.
 *
 * Neither relationship identity nor known source availability implies CAN_DROP.
 * Canonical facts and personalized attempt counts are retained even when no actionable calculation is possible.
 */
data class AcquisitionSourceAssessment(
    val relationship: DropRelationship,
    val sourceKnowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>,
    val relationshipKnowledge: ScopedAcquisitionKnowledge<DropRelationshipResolution>,
    val probabilityKnowledge: ScopedAcquisitionKnowledge<FixedDropEstimateResolution>,
    val availableAttempts: AvailableAttempts?,
    val calculation: FixedProbabilityCalculation?,
)
