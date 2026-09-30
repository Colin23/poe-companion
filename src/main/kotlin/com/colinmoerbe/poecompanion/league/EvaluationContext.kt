package com.colinmoerbe.poecompanion.league

/**
 * Concrete game context used to decide which mutable PoE knowledge may be applied.
 *
 * Patch and league precision are optional because historical or otherwise incomplete contexts may not know them.
 */
data class EvaluationContext(
    val compatibilityVersion: CompatibilityVersion,
    val ruleset: Ruleset,
    val gamePatch: GamePatch? = null,
    val leagueDefinitionId: LeagueDefinitionId? = null,
) {
    init {
        require(gamePatch == null || gamePatch.compatibilityVersion == compatibilityVersion) {
            "Game patch must belong to the evaluation context compatibility version"
        }
    }
}
