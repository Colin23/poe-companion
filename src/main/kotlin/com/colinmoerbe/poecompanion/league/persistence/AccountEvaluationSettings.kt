package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.Ruleset

/** Internal per-user game selection projection, independent of any personally owned context. */
internal data class AccountEvaluationSettings(
    val leagueDefinitionId: LeagueDefinitionId,
    val ruleset: Ruleset,
    val compatibilityVersion: CompatibilityVersion,
    val gamePatch: GamePatch?,
)
