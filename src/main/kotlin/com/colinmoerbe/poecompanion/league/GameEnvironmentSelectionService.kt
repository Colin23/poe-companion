package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.league.persistence.AccountEvaluationSelectionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.security.identity.CurrentApplicationUser
import org.springframework.stereotype.Service

/**
 * Stores the signed-in user's game-environment preference independently of personal ownership.
 * Selecting an EvaluationContext does not require an owned AccountContext for that league.
 */
@Service
class GameEnvironmentSelectionService internal constructor(
    private val selection: AccountEvaluationSelectionPersistenceAdapter,
    private val leagues: LeagueDefinitionPersistenceAdapter,
    private val currentUser: CurrentApplicationUser,
) {
    fun select(context: EvaluationContext) {
        val leagueId = requireNotNull(context.leagueDefinitionId) {
            "A selected game environment must identify a curated league"
        }
        require(context.ruleset == Ruleset.NORMAL) { "Ruthless is unsupported in V0.1" }
        requireNotNull(leagues.findById(leagueId)) { "Unknown league definition" }
        selection.save(currentUser.requireId(), context)
    }

    fun getSelected(): EvaluationContext? {
        val settings = selection.find(currentUser.requireId()) ?: return null
        return EvaluationContext(
            compatibilityVersion = settings.compatibilityVersion,
            ruleset = settings.ruleset,
            gamePatch = settings.gamePatch,
            leagueDefinitionId = settings.leagueDefinitionId,
        )
    }
}
