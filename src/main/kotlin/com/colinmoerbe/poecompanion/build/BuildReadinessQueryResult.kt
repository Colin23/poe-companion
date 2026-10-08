package com.colinmoerbe.poecompanion.build

import com.colinmoerbe.poecompanion.league.AccountEvaluation

/**
 * Application-level outcome of evaluating one known build variant against the current account selection.
 */
sealed interface BuildReadinessQueryResult {

    /** No account/evaluation context is currently selected. */
    data object NoAccountEvaluationSelected : BuildReadinessQueryResult

    /** The selected evaluation has no active revision for this build variant's compatibility version. */
    data class NoCompatibleActiveRevision(val accountEvaluation: AccountEvaluation) : BuildReadinessQueryResult

    /** Successful readiness evaluation using the selected account and one compatible active revision. */
    data class Evaluated(
        val accountEvaluation: AccountEvaluation,
        val revision: BuildVariantRevision,
        val readiness: UniqueReadinessResult,
    ) : BuildReadinessQueryResult
}
