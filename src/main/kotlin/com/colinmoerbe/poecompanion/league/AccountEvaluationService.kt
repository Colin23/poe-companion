package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.AccountEvaluationSelectionPersistenceAdapter
import org.springframework.stereotype.Service

/**
 * Manages the single current account/evaluation selection used by V0.1.
 */
@Service
class AccountEvaluationService internal constructor(
    private val selectionPersistenceAdapter: AccountEvaluationSelectionPersistenceAdapter,
    private val accountContextPersistenceAdapter: AccountContextPersistenceAdapter,
) {
    fun select(
        accountContextId: AccountContextId,
        compatibilityVersion: CompatibilityVersion,
        gamePatch: GamePatch? = null,
    ) {
        require(gamePatch == null || gamePatch.compatibilityVersion == compatibilityVersion) {
            "Game patch must belong to the selected compatibility version"
        }
        require(accountContextPersistenceAdapter.findById(accountContextId) != null) {
            "Selected account context must exist"
        }

        selectionPersistenceAdapter.save(accountContextId, compatibilityVersion, gamePatch)
    }

    fun getSelected(): AccountEvaluation? {
        val selection = selectionPersistenceAdapter.find() ?: return null
        val accountContext =
            accountContextPersistenceAdapter.findById(selection.accountContextId)
                ?: throw IllegalStateException("Selected account context no longer exists")

        return AccountEvaluation(
            accountContextId = accountContext.id,
            evaluationContext =
                EvaluationContext(
                    compatibilityVersion = selection.compatibilityVersion,
                    ruleset = accountContext.ruleset,
                    gamePatch = selection.gamePatch,
                    leagueDefinitionId = accountContext.leagueDefinitionId,
                ),
        )
    }
}
