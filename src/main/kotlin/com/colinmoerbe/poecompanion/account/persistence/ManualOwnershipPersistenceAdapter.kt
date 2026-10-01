package com.colinmoerbe.poecompanion.account.persistence

import com.colinmoerbe.poecompanion.account.ManualOwnership
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId
import org.springframework.stereotype.Repository

/**
 * Persists manual ownership without exposing JPA representations to the domain.
 */
@Repository
internal class ManualOwnershipPersistenceAdapter(
    private val springDataRepository: SpringDataManualOwnershipRepository,
) {
    fun save(manualOwnership: ManualOwnership) {
        springDataRepository.save(
            ManualOwnershipEntity(
                id =
                ManualOwnershipEntityId(
                    accountContextId = manualOwnership.accountContextId.value,
                    uniqueDefinitionId = manualOwnership.uniqueDefinitionId.value,
                ),
                quantity = manualOwnership.quantity,
            ),
        )
    }

    fun findBy(accountContextId: AccountContextId, uniqueDefinitionId: UniqueDefinitionId): ManualOwnership? =
        springDataRepository
            .findById(
                ManualOwnershipEntityId(
                    accountContextId = accountContextId.value,
                    uniqueDefinitionId = uniqueDefinitionId.value,
                ),
            ).map { entity ->
                ManualOwnership(
                    accountContextId = AccountContextId(entity.id.accountContextId),
                    uniqueDefinitionId = UniqueDefinitionId(entity.id.uniqueDefinitionId),
                    quantity = entity.quantity,
                )
            }.orElse(null)

    fun findAllByAccountContextId(accountContextId: AccountContextId): List<ManualOwnership> = springDataRepository
        .findAllByAccountContextId(accountContextId.value)
        .map { entity ->
            ManualOwnership(
                accountContextId = AccountContextId(entity.id.accountContextId),
                uniqueDefinitionId = UniqueDefinitionId(entity.id.uniqueDefinitionId),
                quantity = entity.quantity,
            )
        }
}
