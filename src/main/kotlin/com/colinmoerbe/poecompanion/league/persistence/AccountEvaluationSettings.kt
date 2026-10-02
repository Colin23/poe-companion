package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.GamePatch

/**
 * Internal persisted configuration used to construct the public AccountEvaluation.
 */
internal data class AccountEvaluationSettings(
    val accountContextId: AccountContextId,
    val compatibilityVersion: CompatibilityVersion,
    val gamePatch: GamePatch?,
)
