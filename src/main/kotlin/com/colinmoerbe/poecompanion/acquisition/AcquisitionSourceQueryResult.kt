package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.AccountEvaluation

/**
 * Known source identities remain browsable without a selected account or accepted encounter knowledge.
 * A null sourceKnowledge means no account was selected; Missing/Unknown/Ambiguous remain distinct otherwise.
 */
data class AcquisitionSourceBrowseResult(
    val accountEvaluation: AccountEvaluation?,
    val sources: List<AcquisitionSourceSummary>,
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

/** No account means relationship identities are known but their contextual knowledge cannot yet be evaluated. */
data class AcquisitionSourceDetailResult(
    val source: AcquisitionSource,
    val accountEvaluation: AccountEvaluation?,
    val sourceKnowledge: ScopedAcquisitionKnowledge<BossEncounterKnowledgeResolution>?,
    val availableAttempts: AvailableAttempts?,
    val previewAttempts: Int?,
    val relationships: List<AcquisitionSourceRelationshipResult>,
)
