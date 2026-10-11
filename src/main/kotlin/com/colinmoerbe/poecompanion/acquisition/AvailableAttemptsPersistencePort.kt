package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.AccountContextId

/**
 * Application-facing persistence boundary for manually recorded available attempts.
 */
internal interface AvailableAttemptsPersistencePort {
    fun save(availableAttempts: AvailableAttempts)

    fun findBy(accountContextId: AccountContextId, acquisitionSourceId: AcquisitionSourceId): AvailableAttempts?

    fun deleteBy(accountContextId: AccountContextId, acquisitionSourceId: AcquisitionSourceId)
}
