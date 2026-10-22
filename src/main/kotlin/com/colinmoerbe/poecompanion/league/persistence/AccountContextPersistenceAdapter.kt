package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.security.identity.ApplicationUserId
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/** Immutable account-context identity and ownership persistence. */
@Repository
internal class AccountContextPersistenceAdapter(private val springDataRepository: SpringDataAccountContextRepository) {
    @Transactional
    fun save(accountContext: AccountContext) {
        val insertedRows =
            springDataRepository.insertIfAbsent(
                id = accountContext.id.value,
                leagueDefinitionId = accountContext.leagueDefinitionId.value,
                ruleset = accountContext.ruleset.name,
                ownerApplicationUserId = accountContext.ownerApplicationUserId.value,
            )

        if (insertedRows == 1) return

        val existing =
            springDataRepository.findById(accountContext.id.value).orElseThrow {
                IllegalStateException("Account context disappeared after an identity conflict")
            }

        require(
            existing.leagueDefinitionId == accountContext.leagueDefinitionId.value &&
                existing.ruleset == accountContext.ruleset &&
                existing.ownerApplicationUserId == accountContext.ownerApplicationUserId.value,
        ) {
            "Existing account context identity and owner cannot be redefined"
        }
    }

    fun findByOwnerAndLeagueAndRuleset(
        owner: ApplicationUserId,
        leagueId: LeagueDefinitionId,
        ruleset: Ruleset,
    ): AccountContext? = springDataRepository
        .findByOwnerApplicationUserIdAndLeagueDefinitionIdAndRuleset(owner.value, leagueId.value, ruleset)
        ?.let(::toDomain)

    fun findById(id: AccountContextId): AccountContext? =
        springDataRepository.findById(id.value).map(::toDomain).orElse(null)

    private fun toDomain(entity: AccountContextEntity): AccountContext = AccountContext(
        id = AccountContextId(entity.id),
        leagueDefinitionId = LeagueDefinitionId(entity.leagueDefinitionId),
        ruleset = entity.ruleset,
        ownerApplicationUserId = ApplicationUserId(entity.ownerApplicationUserId),
    )
}
