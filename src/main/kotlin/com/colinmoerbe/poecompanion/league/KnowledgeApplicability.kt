package com.colinmoerbe.poecompanion.league

/**
 * Resolved constraints describing where one piece of mutable PoE knowledge applies.
 *
 * A null dimension means that the canonical knowledge is known to be broad on that dimension. It must not be used to
 * represent missing or unknown provider/source applicability. Explicit unknown applicability is not modeled yet and
 * must remain unresolved outside this type.
 *
 * A constrained patch is matched exactly; temporal "effective from" semantics are intentionally deferred until a
 * concrete domain use case requires them.
 */
data class KnowledgeApplicability(
    val compatibilityVersion: CompatibilityVersion? = null,
    val ruleset: Ruleset? = null,
    val gamePatch: GamePatch? = null,
    val leagueDefinitionId: LeagueDefinitionId? = null,
) {
    init {
        require(gamePatch == null || compatibilityVersion != null) {
            "Patch applicability requires a compatibility version"
        }
        require(gamePatch == null || gamePatch.compatibilityVersion == compatibilityVersion) {
            "Patch applicability must belong to its compatibility version"
        }
    }

    /**
     * Evaluates these resolved constraints against a concrete context.
     *
     * A known mismatch is definitive. Missing context precision for a constrained optional dimension yields UNKNOWN
     * rather than being treated as a match.
     */
    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    fun evaluateAgainst(context: EvaluationContext): ApplicabilityResult {
        if (compatibilityVersion != null && compatibilityVersion != context.compatibilityVersion) {
            return ApplicabilityResult.DOES_NOT_APPLY
        }

        if (ruleset != null && ruleset != context.ruleset) {
            return ApplicabilityResult.DOES_NOT_APPLY
        }

        if (gamePatch != null && context.gamePatch != null && gamePatch != context.gamePatch) {
            return ApplicabilityResult.DOES_NOT_APPLY
        }

        if (leagueDefinitionId != null &&
            context.leagueDefinitionId != null &&
            leagueDefinitionId != context.leagueDefinitionId
        ) {
            return ApplicabilityResult.DOES_NOT_APPLY
        }

        if (gamePatch != null && context.gamePatch == null) {
            return ApplicabilityResult.UNKNOWN
        }

        if (leagueDefinitionId != null && context.leagueDefinitionId == null) {
            return ApplicabilityResult.UNKNOWN
        }

        return ApplicabilityResult.APPLIES
    }
}
