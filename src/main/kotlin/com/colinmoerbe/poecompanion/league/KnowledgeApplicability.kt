package com.colinmoerbe.poecompanion.league

/**
 * Optional dimensions restricting where one piece of mutable PoE knowledge applies.
 *
 * An omitted dimension is broad for that dimension. A constrained patch is matched exactly; temporal "effective from"
 * semantics are intentionally deferred until a concrete domain use case requires them.
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
     * Evaluates these constraints against a concrete context.
     *
     * A known mismatch is definitive. Missing context precision for a constrained optional dimension yields UNKNOWN
     * rather than being treated as a match.
     */
    fun evaluateAgainst(context: EvaluationContext): ApplicabilityResult {
        if (compatibilityVersion != null && compatibilityVersion != context.compatibilityVersion) {
            return ApplicabilityResult.DOES_NOT_APPLY
        }
        if (ruleset != null && ruleset != context.ruleset) {
            return ApplicabilityResult.DOES_NOT_APPLY
        }

        var missingPrecision = false

        if (gamePatch != null) {
            when {
                context.gamePatch == null -> missingPrecision = true
                gamePatch != context.gamePatch -> return ApplicabilityResult.DOES_NOT_APPLY
            }
        }

        if (leagueDefinitionId != null) {
            when {
                context.leagueDefinitionId == null -> missingPrecision = true
                leagueDefinitionId != context.leagueDefinitionId -> return ApplicabilityResult.DOES_NOT_APPLY
            }
        }

        return if (missingPrecision) ApplicabilityResult.UNKNOWN else ApplicabilityResult.APPLIES
    }
}
