package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.league.AccountContextId
import org.springframework.stereotype.Service

/**
 * Derives current ownership for one account context from the active V0.1 manual authority.
 */
@Service
class CurrentOwnershipService internal constructor(private val manualOwnershipRepository: ManualOwnershipRepository) {
    fun getCurrentOwnership(accountContextId: AccountContextId): CurrentOwnership {
        val quantities =
            manualOwnershipRepository
                .findAllByAccountContextId(accountContextId)
                .associate { it.uniqueDefinitionId to it.quantity }

        return CurrentOwnership(accountContextId, quantities)
    }
}
