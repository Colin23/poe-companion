package com.colinmoerbe.poecompanion.acquisition.attempts

import com.colinmoerbe.poecompanion.acquisition.attempts.internal.AvailableAttemptsPersistencePort
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.league.AccountContextId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Manages the current manually entered count of available attempts for a source in an account context.
 */
@Service
class AvailableAttemptsService internal constructor(private val persistencePort: AvailableAttemptsPersistencePort) {
    @Transactional
    fun setAvailableAttempts(
        accountContextId: AccountContextId,
        acquisitionSourceId: AcquisitionSourceId,
        count: Int,
    ) {
        persistencePort.save(AvailableAttempts(accountContextId, acquisitionSourceId, count))
    }

    fun getAvailableAttempts(
        accountContextId: AccountContextId,
        acquisitionSourceId: AcquisitionSourceId,
    ): AvailableAttempts? = persistencePort.findBy(accountContextId, acquisitionSourceId)

    @Transactional
    fun clearAvailableAttempts(accountContextId: AccountContextId, acquisitionSourceId: AcquisitionSourceId) {
        persistencePort.deleteBy(accountContextId, acquisitionSourceId)
    }
}
