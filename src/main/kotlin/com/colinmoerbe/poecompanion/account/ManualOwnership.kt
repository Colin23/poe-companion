package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId

/**
 * One manually maintained transferable Unique quantity in a specific account context.
 */
data class ManualOwnership(
    val accountContextId: AccountContextId,
    val uniqueDefinitionId: UniqueDefinitionId,
    val quantity: Int,
) {
    init {
        require(quantity >= 0) { "Owned quantity must not be negative" }
    }
}
