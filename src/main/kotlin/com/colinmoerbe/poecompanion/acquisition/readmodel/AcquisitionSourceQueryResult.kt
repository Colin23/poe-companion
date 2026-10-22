package com.colinmoerbe.poecompanion.acquisition.readmodel

import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttempts
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeResolution
import com.colinmoerbe.poecompanion.acquisition.knowledge.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.league.AccountEvaluation
import com.colinmoerbe.poecompanion.league.EvaluationContext

/**
 * Known source identities remain browsable without a selected account or accepted encounter knowledge.
 * A null sourceKnowledge means no game environment was selected; Missing/Unknown/Ambiguous remain distinct otherwise.
 */
data class AcquisitionSourceBrowseResult(
    val accountEvaluation: AccountEvaluation?,
    val sources: List<AcquisitionSourceSummary>,
    val evaluationContext: EvaluationContext? = accountEvaluation?.evaluationContext,
)

data class AcquisitionSourceSummary(
    val source: AcquisitionSource,
    val relationshipCount: Int,
    val sourceKnowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>?,
)

data class AcquisitionSourceRelationshipResult(
    val relationship: DropRelationship,
    val assessment: AcquisitionSourceAssessment?,
)

/** No game environment means relationship identities are known but their contextual knowledge cannot yet be evaluated. */
data class AcquisitionSourceDetailResult(
    val source: AcquisitionSource,
    val accountEvaluation: AccountEvaluation?,
    val sourceKnowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>?,
    val availableAttempts: AvailableAttempts?,
    val previewAttempts: Int?,
    val relationships: List<AcquisitionSourceRelationshipResult>,
    val evaluationContext: EvaluationContext? = accountEvaluation?.evaluationContext,
)

/**
 * Stable relationships are discoverable without a personal account; contextual assessments require a selected game environment.
 * Each candidate's null assessment means unevaluated, not CANNOT_DROP or an unavailable source.
 */
data class AcquisitionUniqueCandidatesResult(
    val accountEvaluation: AccountEvaluation?,
    val candidates: List<AcquisitionSourceRelationshipResult>,
    val evaluationContext: EvaluationContext? = accountEvaluation?.evaluationContext,
)
