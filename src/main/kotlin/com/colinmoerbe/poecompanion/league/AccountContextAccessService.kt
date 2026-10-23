package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.SelectableLeagueVersionPersistenceAdapter
import com.colinmoerbe.poecompanion.security.identity.ApplicationUserId
import com.colinmoerbe.poecompanion.security.identity.CurrentApplicationUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/**
 * Single application boundary for access to personal PoE contexts, regardless of caller module.
 * Unknown and foreign contexts share one rejection message.
 */
@Service
class AccountContextAccessService internal constructor(
    private val currentUser: CurrentApplicationUser,
    private val accountContexts: AccountContextPersistenceAdapter,
    private val selection: GameEnvironmentSelectionService,
    private val curatedVersions: SelectableLeagueVersionPersistenceAdapter,
) {
    fun currentUserId(): ApplicationUserId = currentUser.requireId()

    /** Resolve only the signed-in user's state for an independently selected game environment. */
    fun findOwned(leagueId: LeagueDefinitionId, ruleset: Ruleset): AccountContext? =
        accountContexts.findByOwnerAndLeagueAndRuleset(currentUser.requireId(), leagueId, ruleset)

    /**
     * Called only for meaningful writes. A historical unrecognized selection can use
     * an existing personal context, but cannot create a new one.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun getOrCreateForSelectedEnvironment(): AccountContext {
        val selected = requireNotNull(selection.getSelected()) { "No game environment selected" }
        val leagueId = requireNotNull(selected.leagueDefinitionId) { "Selected environment has no league" }
        val owner = currentUser.requireId()

        accountContexts.findByOwnerAndLeagueAndRuleset(owner, leagueId, selected.ruleset)?.let { return it }

        if (selected.ruleset != Ruleset.NORMAL || !curatedVersions.isRecognized(leagueId, selected.compatibilityVersion)) {
            throw UnrecognizedSelectedGameEnvironmentException()
        }

        accountContexts.insertForOwnerIfAbsent(
            AccountContext(AccountContextId.generate(), leagueId, selected.ruleset, owner),
        )
        return checkNotNull(accountContexts.findByOwnerAndLeagueAndRuleset(owner, leagueId, selected.ruleset)) {
            "Account context was not available after conflict-safe creation"
        }
    }

    fun requireOwned(id: AccountContextId): AccountContext {
        val user = currentUser.requireId()
        return requireNotNull(accountContexts.findById(id)?.takeIf { it.ownerApplicationUserId == user }) {
            "Account context unavailable"
        }
    }
}
