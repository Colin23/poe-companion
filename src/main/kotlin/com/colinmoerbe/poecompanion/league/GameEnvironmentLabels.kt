package com.colinmoerbe.poecompanion.league

/** Human-readable summary derived from canonical league metadata. */
object GameEnvironmentLabels {
    fun compact(context: EvaluationContext, league: LeagueDefinition): String {
        val mortality = if (league.mortality == LeagueMortality.SOFTCORE) "SC" else "HC"
        val participation = if (league.participation == LeagueParticipation.SSF) " SSF" else ""
        val ruthless = if (context.ruleset == Ruleset.RUTHLESS) " Ruthless" else ""
        val leagueName = if (league.type == LeagueType.STANDARD) "Standard" else league.name
        return "${context.compatibilityVersion} $mortality$participation$ruthless $leagueName"
    }
}
