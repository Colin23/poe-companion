package com.colinmoerbe.poecompanion.league

/**
 * Reference knowledge describing one PoE league known to the Companion.
 *
 * Display names and structured league dimensions are metadata; the application-owned [id] remains the league identity.
 */
class LeagueDefinition(
    val id: LeagueDefinitionId,
    val name: String,
    val type: LeagueType,
    val participation: LeagueParticipation,
    val mortality: LeagueMortality,
    val realm: GameRealm,
) {
    init {
        require(name.isNotBlank()) { "League name must not be blank" }
    }
}
