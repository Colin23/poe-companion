package com.colinmoerbe.poecompanion.browser.acquisitions

import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionReadModelService
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import com.colinmoerbe.poecompanion.league.UnrecognizedSelectedGameEnvironmentException
import org.springframework.stereotype.Service

/** Validates Browser commands before delegating owner-scoped mutations to Acquisition. */
@Service
internal class AcquisitionAttemptsUpdateService(
    private val sources: AcquisitionReadModelService,
    private val selection: GameEnvironmentSelectionService,
    private val attempts: AvailableAttemptsService,
) {
    fun save(sourceId: AcquisitionSourceId, count: Int): AcquisitionAttemptsUpdateResult {
        require(count >= 0) { "Available attempts must not be negative" }
        return when {
            !sources.sourceExists(sourceId) -> AcquisitionAttemptsUpdateResult.UNKNOWN_SOURCE
            selection.getSelected() == null -> AcquisitionAttemptsUpdateResult.NO_ENVIRONMENT_SELECTED
            else -> {
                // Acquisition's transactional method rolls back before this expected rejection is handled.
                try {
                    attempts.setAvailableAttemptsForSelectedEnvironment(sourceId, count)
                    AcquisitionAttemptsUpdateResult.UPDATED
                } catch (_: UnrecognizedSelectedGameEnvironmentException) {
                    AcquisitionAttemptsUpdateResult.UNRECOGNIZED_ENVIRONMENT
                }
            }
        }
    }

    fun remove(sourceId: AcquisitionSourceId): AcquisitionAttemptsUpdateResult = when {
        !sources.sourceExists(sourceId) -> AcquisitionAttemptsUpdateResult.UNKNOWN_SOURCE
        selection.getSelected() == null -> AcquisitionAttemptsUpdateResult.NO_ENVIRONMENT_SELECTED
        else -> {
            attempts.clearAvailableAttemptsForSelectedEnvironment(sourceId)
            AcquisitionAttemptsUpdateResult.UPDATED
        }
    }
}

internal enum class AcquisitionAttemptsUpdateResult {
    UPDATED,
    UNKNOWN_SOURCE,
    NO_ENVIRONMENT_SELECTED,
    UNRECOGNIZED_ENVIRONMENT,
}
