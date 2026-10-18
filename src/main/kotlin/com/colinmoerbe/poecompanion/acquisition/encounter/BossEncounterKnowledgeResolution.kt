package com.colinmoerbe.poecompanion.acquisition.encounter

import com.colinmoerbe.poecompanion.league.ApplicabilityResult

/**
 * Result of resolving a boss-encounter knowledge timeline against an evaluation context.
 */
sealed interface BossEncounterKnowledgeResolution {
    val applicability: ApplicabilityResult

    data class Resolved(val snapshot: BossEncounterKnowledgeSnapshot) : BossEncounterKnowledgeResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.APPLIES
    }

    data object DoesNotApply : BossEncounterKnowledgeResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.DOES_NOT_APPLY
    }

    data object Unknown : BossEncounterKnowledgeResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.UNKNOWN
    }
}
