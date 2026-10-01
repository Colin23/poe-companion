package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import org.springframework.stereotype.Repository

/**
 * Persists account-context identity/configuration without exposing JPA representations to the domain.
 */
@Repository
internal class AccountContextPersistenceAdapter(
    private val springDataRepository: SpringDataAccountContextRepository,
) {
    fun save(accountContext: AccountContext) {
        springDataRepository.save(
            AccountContextEntity(
                id = accountContext.id.value,
                leagueDefinitionId = accountContext.leagueDefinitionId.value,
                ruleset = accountContext.ruleset,
            ),
        )
    }

    fun findById(id: AccountContextId): AccountContext? = springDataRepository
        .findById(id.value)
        .map { entity ->
            AccountContext(
                id = AccountContextId(entity.id),
                leagueDefinitionId = LeagueDefinitionId(entity.leagueDefinitionId),
                ruleset = entity.ruleset,
            )
        }.orElse(null)
}
