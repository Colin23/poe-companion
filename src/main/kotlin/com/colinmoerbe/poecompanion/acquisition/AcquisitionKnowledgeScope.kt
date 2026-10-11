package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.ApplicabilityResult
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.Ruleset

/**
 * Independently versioned scope for canonical acquisition knowledge.
 *
 * Compatibility version and ruleset are always explicit so Normal and Ruthless histories can evolve independently.
 * A missing league constraint means the canonical knowledge is known to be league-independent.
 */
data class AcquisitionKnowledgeScope(
    val compatibilityVersion: CompatibilityVersion,
    val ruleset: Ruleset,
    val leagueDefinitionId: LeagueDefinitionId? = null,
) {
    /**
     * Evaluates this scope against a concrete game context before patch change points are considered.
     */
    fun evaluateAgainst(context: EvaluationContext): ApplicabilityResult {
        if (compatibilityVersion != context.compatibilityVersion || ruleset != context.ruleset) {
            return ApplicabilityResult.DOES_NOT_APPLY
        }

        if (leagueDefinitionId != null &&
            context.leagueDefinitionId != null &&
            leagueDefinitionId != context.leagueDefinitionId
        ) {
            return ApplicabilityResult.DOES_NOT_APPLY
        }

        if (leagueDefinitionId != null && context.leagueDefinitionId == null) {
            return ApplicabilityResult.UNKNOWN
        }

        return ApplicabilityResult.APPLIES
    }
}
