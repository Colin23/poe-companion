package com.colinmoerbe.poecompanion.browser.uniques

import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.catalog.UniqueCatalogQueryService
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Coordinates a browser ownership command through the existing Catalog, League, and Account APIs. */
@Service
internal class UniqueOwnershipUpdateService(
    private val catalog: UniqueCatalogQueryService,
    private val accountEvaluation: AccountEvaluationService,
    private val ownership: ManualOwnershipService,
) {
    @Transactional
    fun update(uniqueId: UniqueDefinitionId, quantity: Int): UniqueOwnershipUpdateResult {
        require(quantity >= 0) { "Owned quantity must not be negative" }
        if (catalog.findById(uniqueId) == null) return UniqueOwnershipUpdateResult.UNKNOWN_UNIQUE

        val selected = accountEvaluation.getSelected()
        return if (selected == null) {
            UniqueOwnershipUpdateResult.NO_ACCOUNT_SELECTED
        } else {
            ownership.setOwnedQuantity(selected.accountContextId, uniqueId, quantity)
            UniqueOwnershipUpdateResult.UPDATED
        }
    }
}

internal enum class UniqueOwnershipUpdateResult {
    UPDATED,
    NO_ACCOUNT_SELECTED,
    UNKNOWN_UNIQUE,
}
