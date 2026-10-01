package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Persists account-context identity/configuration without exposing JPA representations to the domain.
 *
 * Once an identity exists, its league and ruleset cannot be redefined.
 */
@Repository
internal class AccountContextPersistenceAdapter(private val springDataRepository: SpringDataAccountContextRepository) {
    @Transactional
    fun save(accountContext: AccountContext) {
        val existing = springDataRepository.findById(accountContext.id.value).orElse(null)

        if (existing != null) {
            require(
                existing.leagueDefinitionId == accountContext.leagueDefinitionId.value &&
                    existing.ruleset == accountContext.ruleset,
            ) {
                "Existing account context identity cannot be redefined"
            }
            return
        }

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
