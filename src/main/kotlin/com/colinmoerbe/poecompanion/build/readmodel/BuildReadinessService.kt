package com.colinmoerbe.poecompanion.build.readmodel

import com.colinmoerbe.poecompanion.account.CurrentOwnership
import com.colinmoerbe.poecompanion.account.CurrentOwnershipService
import com.colinmoerbe.poecompanion.build.identity.BuildVariantId
import com.colinmoerbe.poecompanion.build.readiness.UniqueReadinessEvaluator
import com.colinmoerbe.poecompanion.build.revision.internal.BuildVariantRevisionPersistencePort
import com.colinmoerbe.poecompanion.league.AccountContextAccessService
import com.colinmoerbe.poecompanion.league.AccountEvaluation
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Resolves canonical revisions independently of personal ownership. */
@Service
class BuildReadinessService internal constructor(
    private val gameSelection: GameEnvironmentSelectionService,
    private val access: AccountContextAccessService,
    private val currentOwnershipService: CurrentOwnershipService,
    private val revisions: BuildVariantRevisionPersistencePort,
) {
    private val evaluator = UniqueReadinessEvaluator()

    @Transactional(readOnly = true)
    fun evaluate(buildVariantId: BuildVariantId): BuildReadinessQueryResult =
        evaluateAll(listOf(buildVariantId)).getValue(buildVariantId)

    @Suppress("ReturnCount")
    @Transactional(readOnly = true)
    fun evaluateAll(buildVariantIds: Collection<BuildVariantId>): Map<BuildVariantId, BuildReadinessQueryResult> {
        if (buildVariantIds.isEmpty()) return emptyMap()
        val context = gameSelection.getSelected()
            ?: return buildVariantIds.associateWith { BuildReadinessQueryResult.NoAccountEvaluationSelected }
        val leagueId = requireNotNull(context.leagueDefinitionId)
        val account = access.findOwned(leagueId, context.ruleset)
        val ownership = account?.let { currentOwnershipService.getCurrentOwnership(it.id) }
        val personal = account?.let { AccountEvaluation(it.id, context) }
        return buildVariantIds.associateWith { id -> evaluateFor(id, context, personal, ownership) }
    }

    private fun evaluateFor(
        id: BuildVariantId,
        context: EvaluationContext,
        personal: AccountEvaluation?,
        ownership: CurrentOwnership?,
    ): BuildReadinessQueryResult {
        val revision = revisions.findActiveBy(id, context.compatibilityVersion)
            ?: return BuildReadinessQueryResult.NoCompatibleActiveRevision(context)
        return if (personal == null || ownership == null) {
            BuildReadinessQueryResult.PersonalOwnershipUnavailable(context, revision)
        } else {
            BuildReadinessQueryResult.Evaluated(personal, revision, evaluator.evaluate(revision, ownership))
        }
    }
}
