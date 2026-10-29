package com.colinmoerbe.poecompanion.acquisition.overrides

import com.colinmoerbe.poecompanion.acquisition.overrides.internal.DropRateOverridePersistencePort
import com.colinmoerbe.poecompanion.acquisition.probability.FixedBernoulliModel
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.league.AccountContextAccessService
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import com.colinmoerbe.poecompanion.league.UnrecognizedSelectedGameEnvironmentException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

/**
 * Owner-scoped personal fixed-rate confirmations for the selected exact game environment.
 *
 * Does not resolve canonical drop eligibility, cross-context suggestions or calculator preferences.
 */
@Service
class DropRateOverrideService internal constructor(
    private val persistencePort: DropRateOverridePersistencePort,
    private val accountAccess: AccountContextAccessService,
    private val selection: GameEnvironmentSelectionService,
    private val relationships: DropRelationshipPersistenceAdapter,
) {
    @Transactional(readOnly = true)
    fun findConfirmedForSelectedEnvironment(dropRelationshipId: DropRelationshipId): DropRateOverride? {
        val context = requireSelected()
        val account = accountAccess.findOwned(requireNotNull(context.leagueDefinitionId), context.ruleset)
            ?: return null
        return persistencePort.findCurrent(DropRateOverrideKey(account.id, dropRelationshipId, context))
    }

    /**
     * Creates a current confirmation when expectedRevisionId is absent, or replaces that exact active revision.
     * A valid first write may create the matching AccountContext in the same transaction.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun confirmForSelectedEnvironment(
        dropRelationshipId: DropRelationshipId,
        probabilityPerAttempt: Double,
        expectedRevisionId: DropRateOverrideRevisionId? = null,
    ): DropRateOverride {
        val context = requireSelected()
        val model = FixedBernoulliModel(probabilityPerAttempt)
        requireNotNull(relationships.findById(dropRelationshipId)) {
            "Unknown drop relationship: $dropRelationshipId"
        }
        val account = accountAccess.getOrCreateForSelectedEnvironment()
            ?: throw UnrecognizedSelectedGameEnvironmentException()
        check(account.leagueDefinitionId == context.leagueDefinitionId && account.ruleset == context.ruleset) {
            "Selected game environment changed during personal override confirmation"
        }
        val confirmation = DropRateOverride(
            revisionId = DropRateOverrideRevisionId.generate(),
            key = DropRateOverrideKey(account.id, dropRelationshipId, context),
            model = model,
        )
        persistencePort.confirm(confirmation, expectedRevisionId)
        return confirmation
    }

    /** Removes the currently confirmed value without deleting its historical revision. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun removeForSelectedEnvironment(
        dropRelationshipId: DropRelationshipId,
        expectedRevisionId: DropRateOverrideRevisionId,
    ) {
        val context = requireSelected()
        val account = accountAccess.findOwned(requireNotNull(context.leagueDefinitionId), context.ruleset)
            ?: throw DropRateOverrideConflictException("No current personal override exists for this context")
        persistencePort.remove(
            DropRateOverrideKey(account.id, dropRelationshipId, context),
            expectedRevisionId,
        )
    }

    private fun requireSelected(): EvaluationContext =
        requireNotNull(selection.getSelected()) { "No game environment selected" }.also {
            requireNotNull(it.leagueDefinitionId) { "Selected game environment has no league" }
        }
}
