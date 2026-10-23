package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextAccessService
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/**
 * Applies user-driven changes to manually maintained ownership.
 */
@Service
class ManualOwnershipService internal constructor(
    private val manualOwnershipRepository: ManualOwnershipRepository,
    private val access: AccountContextAccessService,
    private val selection: GameEnvironmentSelectionService,
) {
    /**
     * A zero edit on a contextless environment is a no-op; a positive edit
     * creates/reuses the matching owned context in this transaction.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun setOwnedQuantityForSelectedEnvironment(
        uniqueDefinitionId: UniqueDefinitionId,
        quantity: Int,
    ): SelectedOwnershipWriteResult {
        require(quantity >= 0) { "Owned quantity must not be negative" }
        val context = if (quantity == 0) {
            val selected = requireNotNull(selection.getSelected()) { "No game environment selected" }
            val leagueId = requireNotNull(selected.leagueDefinitionId)
            access.findOwned(leagueId, selected.ruleset)
        } else {
            access.getOrCreateForSelectedEnvironment()
        }

        return when {
            context != null -> {
                setOwnedQuantity(context.id, uniqueDefinitionId, quantity)
                SelectedOwnershipWriteResult.ACCEPTED
            }

            quantity == 0 -> SelectedOwnershipWriteResult.ACCEPTED
            else -> SelectedOwnershipWriteResult.UNRECOGNIZED_ENVIRONMENT
        }
    }

    @Transactional
    fun setOwnedQuantity(accountContextId: AccountContextId, uniqueDefinitionId: UniqueDefinitionId, quantity: Int) {
        require(quantity >= 0) { "Owned quantity must not be negative" }
        access.requireOwned(accountContextId)

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

/** A rejected historical first write is an expected result, not a transaction failure. */
enum class SelectedOwnershipWriteResult {
    ACCEPTED,
    UNRECOGNIZED_ENVIRONMENT,
}
