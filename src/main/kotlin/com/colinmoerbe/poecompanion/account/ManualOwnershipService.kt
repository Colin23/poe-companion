package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextAccessService
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import org.springframework.stereotype.Service
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
    @Transactional
    fun setOwnedQuantityForSelectedEnvironment(uniqueDefinitionId: UniqueDefinitionId, quantity: Int) {
        require(quantity >= 0) { "Owned quantity must not be negative" }
        if (quantity == 0) {
            val selected = requireNotNull(selection.getSelected()) { "No game environment selected" }
            val leagueId = requireNotNull(selected.leagueDefinitionId)
            val existing = access.findOwned(leagueId, selected.ruleset) ?: return
            setOwnedQuantity(existing.id, uniqueDefinitionId, quantity)
            return
        }

        val context = access.getOrCreateForSelectedEnvironment()
        setOwnedQuantity(context.id, uniqueDefinitionId, quantity)
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
