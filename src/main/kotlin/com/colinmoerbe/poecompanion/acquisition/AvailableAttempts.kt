package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.AccountContextId

/**
 * One manually recorded available-attempt count for a source in one account context.
 *
 * A recorded zero is meaningful; absence of an AvailableAttempts record means the count has not been entered.
 */
data class AvailableAttempts(
    val accountContextId: AccountContextId,
    val acquisitionSourceId: AcquisitionSourceId,
    val count: Int,
) {
    init {
        require(count >= 0) { "Available attempt count must not be negative" }
    }
}
