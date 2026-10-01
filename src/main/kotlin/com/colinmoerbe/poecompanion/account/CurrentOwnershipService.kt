package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.account.persistence.ManualOwnershipPersistenceAdapter
import com.colinmoerbe.poecompanion.league.AccountContextId
import org.springframework.stereotype.Service

/**
 * Derives current ownership for one account context from the active V0.1 manual authority.
 */
@Service
class CurrentOwnershipService(
    private val manualOwnershipPersistenceAdapter: ManualOwnershipPersistenceAdapter,
) {
    fun getCurrentOwnership(accountContextId: AccountContextId): CurrentOwnership {
        val quantities =
            manualOwnershipPersistenceAdapter
                .findAllByAccountContextId(accountContextId)
                .associate { it.uniqueDefinitionId to it.quantity }

        return CurrentOwnership(accountContextId, quantities)
    }
}
