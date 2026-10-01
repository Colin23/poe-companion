package com.colinmoerbe.poecompanion.league

/**
 * Stable definition of one isolated personalized PoE environment.
 *
 * [leagueDefinitionId] and [ruleset] define the meaning of this identity and must not change after it is persisted.
 * Personalized state belongs to this identity. Game-version evaluation remains separate in [EvaluationContext], so a
 * long-lived context such as Standard is not permanently bound to one compatibility version or patch.
 */
class AccountContext(val id: AccountContextId, val leagueDefinitionId: LeagueDefinitionId, val ruleset: Ruleset)
