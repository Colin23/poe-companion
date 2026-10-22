package com.colinmoerbe.poecompanion.league

import org.springframework.stereotype.Service

/**
 * Compatibility API for existing personal evaluation consumers during C2 migration.
 * The persisted source of truth is the independently selected game environment.
 */
@Service
class AccountEvaluationService internal constructor(
    private val gameSelection: GameEnvironmentSelectionService,
    private val access: AccountContextAccessService,
) {
    /** Transitional entry point for the existing development fixture and personal-service tests. */
    fun select(
        accountContextId: AccountContextId,
        compatibilityVersion: CompatibilityVersion,
        gamePatch: GamePatch? = null,
    ) {
        val account = access.requireOwned(accountContextId)
        gameSelection.select(
            EvaluationContext(
                compatibilityVersion = compatibilityVersion,
                ruleset = account.ruleset,
                gamePatch = gamePatch,
                leagueDefinitionId = account.leagueDefinitionId,
            ),
        )
    }

    /**
     * Legacy personal view: no matching owned context means no personal evaluation,
     * even if a game environment was selected. C2B will consume game selection directly.
     */
    fun getSelected(): AccountEvaluation? {
        val context = gameSelection.getSelected() ?: return null
        val leagueId = requireNotNull(context.leagueDefinitionId)
        return access.findOwned(leagueId, context.ruleset)?.let { AccountEvaluation(it.id, context) }
    }
}
