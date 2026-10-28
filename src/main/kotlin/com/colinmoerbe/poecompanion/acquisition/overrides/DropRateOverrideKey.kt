package com.colinmoerbe.poecompanion.acquisition.overrides

import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.EvaluationContext

/**
 * Exact personal probability slot for one account context, relationship and selected game environment.
 *
 * An unspecified GamePatch is a distinct confirmation scope, not a wildcard for all patches.
 * V0.1 supports one fixed Bernoulli model and its supported encounter-attempt semantics.
 * Later application services must verify that the AccountContext belongs to the specified league/ruleset.
 */
data class DropRateOverrideKey(
    val accountContextId: AccountContextId,
    val dropRelationshipId: DropRelationshipId,
    val evaluationContext: EvaluationContext,
) {
    init {
        requireNotNull(evaluationContext.leagueDefinitionId) {
            "Personal drop-rate override requires a specific league definition"
        }
    }
}
