package com.colinmoerbe.poecompanion.acquisition.attempts

import com.colinmoerbe.poecompanion.acquisition.attempts.internal.AvailableAttemptsPersistencePort
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.league.AccountContextAccessService
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import com.colinmoerbe.poecompanion.league.UnrecognizedSelectedGameEnvironmentException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/**
 * Manages the current manually entered count of available attempts for a source in an account context.
 */
@Service
class AvailableAttemptsService internal constructor(
    private val persistencePort: AvailableAttemptsPersistencePort,
    private val access: AccountContextAccessService,
    private val selection: GameEnvironmentSelectionService,
) {
    /** Explicitly saving zero attempts creates a recorded personal fact. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun setAvailableAttemptsForSelectedEnvironment(acquisitionSourceId: AcquisitionSourceId, count: Int) {
        require(count >= 0) { "Available attempt count must not be negative" }
        val context = access.getOrCreateForSelectedEnvironment()
            ?: throw UnrecognizedSelectedGameEnvironmentException()
        setAvailableAttempts(context.id, acquisitionSourceId, count)
    }

    /** Clearing an absent count must not create a personal context. */
    @Transactional
    fun clearAvailableAttemptsForSelectedEnvironment(acquisitionSourceId: AcquisitionSourceId) {
        val selected = requireNotNull(selection.getSelected()) { "No game environment selected" }
        val leagueId = requireNotNull(selected.leagueDefinitionId)
        val existing = access.findOwned(leagueId, selected.ruleset) ?: return
        clearAvailableAttempts(existing.id, acquisitionSourceId)
    }

    @Transactional
    fun setAvailableAttempts(
        accountContextId: AccountContextId,
        acquisitionSourceId: AcquisitionSourceId,
        count: Int,
    ) {
        access.requireOwned(accountContextId)
        persistencePort.save(AvailableAttempts(accountContextId, acquisitionSourceId, count))
    }

    fun getAvailableAttempts(
        accountContextId: AccountContextId,
        acquisitionSourceId: AcquisitionSourceId,
    ): AvailableAttempts? {
        access.requireOwned(accountContextId)
        return persistencePort.findBy(accountContextId, acquisitionSourceId)
    }

    @Transactional
    fun clearAvailableAttempts(accountContextId: AccountContextId, acquisitionSourceId: AcquisitionSourceId) {
        access.requireOwned(accountContextId)
        persistencePort.deleteBy(accountContextId, acquisitionSourceId)
    }
}
