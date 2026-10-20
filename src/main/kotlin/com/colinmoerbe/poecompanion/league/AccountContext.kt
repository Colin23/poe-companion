package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.security.identity.ApplicationUserId

/**
 * Stable definition of one owned personalized PoE environment.
 *
 * Owner, league and ruleset define immutable identity semantics; compatibility version/patch
 * belong to the separately persisted per-user evaluation selection.
 */
class AccountContext(
    val id: AccountContextId,
    val leagueDefinitionId: LeagueDefinitionId,
    val ruleset: Ruleset,
    val ownerApplicationUserId: ApplicationUserId,
)
