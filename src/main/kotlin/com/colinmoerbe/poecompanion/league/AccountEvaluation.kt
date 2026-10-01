package com.colinmoerbe.poecompanion.league

/**
 * Selected account context together with the explicit game context used to evaluate it.
 */
data class AccountEvaluation(val accountContextId: AccountContextId, val evaluationContext: EvaluationContext)
