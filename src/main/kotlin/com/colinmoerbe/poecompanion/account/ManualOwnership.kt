package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId

/**
 * One persisted manually maintained transferable Unique quantity in a specific account context.
 *
 * Persisted manual ownership is sparse, so a fact exists only for a positive quantity. Setting a quantity to zero is
 * handled by [ManualOwnershipService] as deletion rather than represented by this type.
 */
data class ManualOwnership(
    val accountContextId: AccountContextId,
    val uniqueDefinitionId: UniqueDefinitionId,
    val quantity: Int,
) {
    init {
        require(quantity > 0) { "Manual ownership quantity must be positive" }
    }
}
