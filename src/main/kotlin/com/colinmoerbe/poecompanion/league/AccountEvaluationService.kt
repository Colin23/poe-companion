package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.league.persistence.AccountEvaluationSelectionPersistenceAdapter
import org.springframework.stereotype.Service

/** Manages the authenticated application's user-scoped AccountContext/evaluation selection. */
@Service
class AccountEvaluationService internal constructor(
    private val selectionPersistenceAdapter: AccountEvaluationSelectionPersistenceAdapter,
    private val access: AccountContextAccessService,
) {
    fun select(
        accountContextId: AccountContextId,
        compatibilityVersion: CompatibilityVersion,
        gamePatch: GamePatch? = null,
    ) {
        require(gamePatch == null || gamePatch.compatibilityVersion == compatibilityVersion) {
            "Game patch must belong to the selected compatibility version"
        }
        access.requireOwned(accountContextId)
        selectionPersistenceAdapter.save(access.currentUserId(), accountContextId, compatibilityVersion, gamePatch)
    }

    fun getSelected(): AccountEvaluation? {
        val selection = selectionPersistenceAdapter.find(access.currentUserId()) ?: return null
        val accountContext = access.requireOwned(selection.accountContextId)

        return AccountEvaluation(
            accountContextId = accountContext.id,
            evaluationContext = EvaluationContext(
                compatibilityVersion = selection.compatibilityVersion,
                ruleset = accountContext.ruleset,
                gamePatch = selection.gamePatch,
                leagueDefinitionId = accountContext.leagueDefinitionId,
            ),
        )
    }
}
