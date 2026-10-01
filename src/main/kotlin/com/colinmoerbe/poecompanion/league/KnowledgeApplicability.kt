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
    fun evaluateAgainst(context: EvaluationContext): ApplicabilityResult {
        val knownMismatch =
            (compatibilityVersion != null && compatibilityVersion != context.compatibilityVersion) ||
                (ruleset != null && ruleset != context.ruleset) ||
                (gamePatch != null && context.gamePatch != null && gamePatch != context.gamePatch) ||
                (leagueDefinitionId != null &&
                    context.leagueDefinitionId != null &&
                    leagueDefinitionId != context.leagueDefinitionId)

        val missingPrecision =
            (gamePatch != null && context.gamePatch == null) ||
                (leagueDefinitionId != null && context.leagueDefinitionId == null)

        return when {
            knownMismatch -> ApplicabilityResult.DOES_NOT_APPLY
            missingPrecision -> ApplicabilityResult.UNKNOWN
            else -> ApplicabilityResult.APPLIES
        }
    }
}
