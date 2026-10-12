package com.colinmoerbe.poecompanion.acquisition.persistence

import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.AvailableAttempts
import com.colinmoerbe.poecompanion.acquisition.AvailableAttemptsPersistencePort
import com.colinmoerbe.poecompanion.league.AccountContextId
import org.springframework.stereotype.Repository

/**
 * Maps manual available-attempt facts to persistence without exposing JPA representations to the domain.
 */
@Repository
internal class AvailableAttemptsPersistenceAdapter(
    private val springDataRepository: SpringDataAvailableAttemptsRepository,
) : AvailableAttemptsPersistencePort {
    override fun save(availableAttempts: AvailableAttempts) {
        springDataRepository.save(
            AvailableAttemptsEntity(
                id =
                AvailableAttemptsEntityId(
                    accountContextId = availableAttempts.accountContextId.value,
                    acquisitionSourceId = availableAttempts.acquisitionSourceId.value,
                ),
                attemptCount = availableAttempts.count,
            ),
        )
    }

    override fun findBy(
        accountContextId: AccountContextId,
        acquisitionSourceId: AcquisitionSourceId,
    ): AvailableAttempts? {
        val id = AvailableAttemptsEntityId(accountContextId.value, acquisitionSourceId.value)
        return springDataRepository
            .findById(id)
            .map { entity ->
                AvailableAttempts(
                    accountContextId = AccountContextId(entity.id.accountContextId),
                    acquisitionSourceId = AcquisitionSourceId(entity.id.acquisitionSourceId),
                    count = entity.attemptCount,
                )
            }.orElse(null)
    }

    override fun deleteBy(accountContextId: AccountContextId, acquisitionSourceId: AcquisitionSourceId) {
        springDataRepository.deleteById(AvailableAttemptsEntityId(accountContextId.value, acquisitionSourceId.value))
    }
}
