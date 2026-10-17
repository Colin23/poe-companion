package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import org.springframework.stereotype.Component

/** Narrow development-bootstrap entry point; keeps League's JPA adapters inside the League module. */
@Component
internal class DevelopmentLeagueAccess(
    private val leagues: LeagueDefinitionPersistenceAdapter,
    private val accounts: AccountContextPersistenceAdapter,
) {
    fun saveLeague(league: LeagueDefinition) = leagues.save(league)

    fun findLeagueById(id: LeagueDefinitionId): LeagueDefinition? = leagues.findById(id)

    fun saveAccount(account: AccountContext) = accounts.save(account)

    fun findAccountById(id: AccountContextId): AccountContext? = accounts.findById(id)
}
