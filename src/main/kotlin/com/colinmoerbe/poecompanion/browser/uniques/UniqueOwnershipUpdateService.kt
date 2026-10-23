package com.colinmoerbe.poecompanion.browser.uniques

import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.account.SelectedOwnershipWriteResult
import com.colinmoerbe.poecompanion.catalog.UniqueCatalogQueryService
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/** Coordinates a browser ownership command through the existing Catalog, League, and Account APIs. */
@Service
internal class UniqueOwnershipUpdateService(
    private val catalog: UniqueCatalogQueryService,
    private val gameSelection: GameEnvironmentSelectionService,
    private val ownership: ManualOwnershipService,
) {
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun update(uniqueId: UniqueDefinitionId, quantity: Int): UniqueOwnershipUpdateResult {
        require(quantity >= 0) { "Owned quantity must not be negative" }
        if (catalog.findById(uniqueId) == null) return UniqueOwnershipUpdateResult.UNKNOWN_UNIQUE

        if (gameSelection.getSelected() == null) return UniqueOwnershipUpdateResult.NO_ENVIRONMENT_SELECTED
        return when (ownership.setOwnedQuantityForSelectedEnvironment(uniqueId, quantity)) {
            SelectedOwnershipWriteResult.ACCEPTED -> UniqueOwnershipUpdateResult.UPDATED
            SelectedOwnershipWriteResult.UNRECOGNIZED_ENVIRONMENT ->
                UniqueOwnershipUpdateResult.UNRECOGNIZED_ENVIRONMENT
        }
    }
}

internal enum class UniqueOwnershipUpdateResult {
    UPDATED,
    NO_ENVIRONMENT_SELECTED,
    UNRECOGNIZED_ENVIRONMENT,
    UNKNOWN_UNIQUE,
}
