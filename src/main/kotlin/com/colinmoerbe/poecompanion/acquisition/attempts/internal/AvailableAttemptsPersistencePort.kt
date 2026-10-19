package com.colinmoerbe.poecompanion.acquisition.attempts.internal

import com.colinmoerbe.poecompanion.acquisition.AvailableAttempts
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.league.AccountContextId

/**
 * Application-facing persistence boundary for manually recorded available attempts.
 */
internal interface AvailableAttemptsPersistencePort {
    fun save(availableAttempts: AvailableAttempts)

    fun findBy(accountContextId: AccountContextId, acquisitionSourceId: AcquisitionSourceId): AvailableAttempts?

    fun deleteBy(accountContextId: AccountContextId, acquisitionSourceId: AcquisitionSourceId)
}
