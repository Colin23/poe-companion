package com.colinmoerbe.poecompanion.build.readmodel

import com.colinmoerbe.poecompanion.build.readiness.UniqueReadinessResult
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevision
import com.colinmoerbe.poecompanion.league.AccountEvaluation
import com.colinmoerbe.poecompanion.league.EvaluationContext

/** Canonical build applicability and personal Unique readiness are independent results. */
sealed interface BuildReadinessQueryResult {
    /** There is no selected game environment from which to resolve a compatible revision. */
    data object NoAccountEvaluationSelected : BuildReadinessQueryResult

    /** A game environment exists, but no active revision is compatible with its version. */
    data class NoCompatibleActiveRevision(val evaluationContext: EvaluationContext) : BuildReadinessQueryResult

    /** Requirements are known, but no owned personal context is available for readiness. */
    data class PersonalOwnershipUnavailable(
        val evaluationContext: EvaluationContext,
        val revision: BuildVariantRevision,
    ) : BuildReadinessQueryResult

    /** The compatible revision was evaluated using real personal ownership. */
    data class Evaluated(
        val accountEvaluation: AccountEvaluation,
        val revision: BuildVariantRevision,
        val readiness: UniqueReadinessResult,
    ) : BuildReadinessQueryResult
}
