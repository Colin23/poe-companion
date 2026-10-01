package com.colinmoerbe.poecompanion.league

/**
 * Stable definition of one isolated personalized PoE environment.
 *
 * Personalized state belongs to this identity. Game-version evaluation remains separate in [EvaluationContext], so a
 * long-lived context such as Standard is not permanently bound to one compatibility version or patch.
 */
class AccountContext(
    val id: AccountContextId,
    val leagueDefinitionId: LeagueDefinitionId,
    val ruleset: Ruleset,
)
