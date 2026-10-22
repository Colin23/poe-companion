package com.colinmoerbe.poecompanion.league

import org.springframework.stereotype.Service

/**
 * Resolves the selected game environment together with the current user's matching personal context.
 * Game environment selection is persisted independently of personal ownership.
 */
@Service
class AccountEvaluationService internal constructor(
    private val gameSelection: GameEnvironmentSelectionService,
    private val access: AccountContextAccessService,
) {
    /** Selects the game environment identified by an owned context's league and ruleset. */
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

    /** Returns null when no game environment or matching owned AccountContext is available. */
    fun getSelected(): AccountEvaluation? {
        val context = gameSelection.getSelected() ?: return null
        val leagueId = requireNotNull(context.leagueDefinitionId)
        return access.findOwned(leagueId, context.ruleset)?.let { AccountEvaluation(it.id, context) }
    }
}
