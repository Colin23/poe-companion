package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Applies user-driven changes to manually maintained ownership.
 */
@Service
class ManualOwnershipService internal constructor(private val manualOwnershipRepository: ManualOwnershipRepository) {
    @Transactional
    fun setOwnedQuantity(accountContextId: AccountContextId, uniqueDefinitionId: UniqueDefinitionId, quantity: Int) {
        require(quantity >= 0) { "Owned quantity must not be negative" }

        if (quantity == 0) {
            manualOwnershipRepository.deleteBy(accountContextId, uniqueDefinitionId)
            return
        }

        manualOwnershipRepository.save(
            ManualOwnership(
                accountContextId = accountContextId,
                uniqueDefinitionId = uniqueDefinitionId,
                quantity = quantity,
            ),
        )
    }
}
