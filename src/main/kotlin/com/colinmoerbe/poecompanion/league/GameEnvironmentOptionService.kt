package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.SelectableLeagueVersionPersistenceAdapter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** A curator-recognized pairing, not a statement that the league is active today. */
data class GameEnvironmentOption(
    val league: LeagueDefinition,
    val compatibilityVersion: CompatibilityVersion,
) {
    val context: EvaluationContext
        get() = EvaluationContext(compatibilityVersion, Ruleset.NORMAL, leagueDefinitionId = league.id)
}

data class GameEnvironmentChoice(
    val compatibilityVersion: CompatibilityVersion,
    val type: LeagueType,
    val mortality: LeagueMortality,
    val participation: LeagueParticipation,
    val ruleset: Ruleset = Ruleset.NORMAL,
)

/** Resolves structured choices to curated environments without guessing league identities or lifecycles. */
@Service
class GameEnvironmentOptionService internal constructor(
    private val versions: SelectableLeagueVersionPersistenceAdapter,
    private val leagues: LeagueDefinitionPersistenceAdapter,
    private val selections: GameEnvironmentSelectionService,
) {
    @Transactional(readOnly = true)
    fun findAll(): List<GameEnvironmentOption> = versions.findAll().mapNotNull { (id, version) ->
        leagues.findById(id)?.let { GameEnvironmentOption(it, version) }
    }

    @Transactional
    fun select(choice: GameEnvironmentChoice) {
        require(choice.ruleset == Ruleset.NORMAL) { "Ruthless is unsupported in V0.1" }
        val matches = findAll().filter { option ->
            option.compatibilityVersion == choice.compatibilityVersion &&
                option.league.type == choice.type &&
                option.league.mortality == choice.mortality &&
                option.league.participation == choice.participation &&
                option.league.realm == GameRealm.PC
        }
        require(matches.size == 1) { "No unique curated game environment matches this choice" }
        selections.select(matches.single().context)
    }
}
