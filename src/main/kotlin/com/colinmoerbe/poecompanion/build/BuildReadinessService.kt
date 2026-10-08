package com.colinmoerbe.poecompanion.build

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

    @Suppress("ReturnCount")
    @Transactional(readOnly = true)
    fun evaluate(buildVariantId: BuildVariantId): BuildReadinessQueryResult {
        val accountEvaluation =
            accountEvaluationService.getSelected()
                ?: return BuildReadinessQueryResult.NoAccountEvaluationSelected

        val revision =
            revisionPersistencePort.findActiveBy(
                buildVariantId = buildVariantId,
                compatibilityVersion = accountEvaluation.evaluationContext.compatibilityVersion,
            )
                ?: return BuildReadinessQueryResult.NoCompatibleActiveRevision(accountEvaluation)

        val ownership = currentOwnershipService.getCurrentOwnership(accountEvaluation.accountContextId)

        return BuildReadinessQueryResult.Evaluated(
            accountEvaluation = accountEvaluation,
            revision = revision,
            readiness = readinessEvaluator.evaluate(revision, ownership),
        )
    }
}
