package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId

/**
 * Current transferable Unique quantities for one isolated account context.
 *
 * The view is sparse: only positive quantities are retained. Missing entries therefore mean an owned quantity of zero.
 */
class CurrentOwnership(
    val accountContextId: AccountContextId,
    quantities: Map<UniqueDefinitionId, Int>,
) {
    val quantities: Map<UniqueDefinitionId, Int>

    init {
        require(quantities.values.all { it >= 0 }) { "Current ownership quantities must not be negative" }
        this.quantities = quantities.filterValues { it > 0 }.toMap()
    }

    fun quantityOf(uniqueDefinitionId: UniqueDefinitionId): Int = quantities[uniqueDefinitionId] ?: 0
}
