package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountContextId

/**
 * Application-facing persistence boundary for manual ownership.
 */
internal interface ManualOwnershipRepository {
    fun save(manualOwnership: ManualOwnership)

    fun deleteBy(
        accountContextId: AccountContextId,
        uniqueDefinitionId: UniqueDefinitionId,
    )

    fun findAllByAccountContextId(accountContextId: AccountContextId): List<ManualOwnership>
}
