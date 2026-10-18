package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.account.CurrentOwnership
import com.colinmoerbe.poecompanion.account.CurrentOwnershipService
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Composes the current account selection, ownership, active build revision, and pure readiness evaluator.
 */
@Service
class BuildReadinessService internal constructor(
    private val accountEvaluationService: AccountEvaluationService,
    private val currentOwnershipService: CurrentOwnershipService,
    private val revisionPersistencePort: BuildVariantRevisionPersistencePort,
) {
    private val readinessEvaluator = UniqueReadinessEvaluator()

    /** Single-build entry point retained for existing callers. */
    @Transactional(readOnly = true)
    fun evaluate(buildVariantId: BuildVariantId): BuildReadinessQueryResult =
        evaluateAll(listOf(buildVariantId)).getValue(buildVariantId)

    /**
     * Evaluates multiple variants against one selected context and one ownership snapshot.
     * The caller owns the wider read transaction when composing a browser page.
     */
    @Transactional(readOnly = true)
    fun evaluateAll(buildVariantIds: Collection<BuildVariantId>): Map<BuildVariantId, BuildReadinessQueryResult> {
        val accountEvaluation = accountEvaluationService.getSelected()
            ?: return buildVariantIds.associateWith { BuildReadinessQueryResult.NoAccountEvaluationSelected }

        val ownership = currentOwnershipService.getCurrentOwnership(accountEvaluation.accountContextId)
        return buildVariantIds.associateWith { variantId ->
            evaluateFor(variantId, accountEvaluation, ownership)
        }
    }

    private fun evaluateFor(
        variantId: BuildVariantId,
        selected: com.colinmoerbe.poecompanion.league.AccountEvaluation,
        ownership: CurrentOwnership,
    ): BuildReadinessQueryResult {
        val revision = revisionPersistencePort.findActiveBy(
            buildVariantId = variantId,
            compatibilityVersion = selected.evaluationContext.compatibilityVersion,
        ) ?: return BuildReadinessQueryResult.NoCompatibleActiveRevision(selected)

        return BuildReadinessQueryResult.Evaluated(
            accountEvaluation = selected,
            revision = revision,
            readiness = readinessEvaluator.evaluate(revision, ownership),
        )
    }
}
