package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttempts
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeResolution
import com.colinmoerbe.poecompanion.acquisition.knowledge.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateResolution
import com.colinmoerbe.poecompanion.acquisition.probability.FixedProbabilityCalculation
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipResolution

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
