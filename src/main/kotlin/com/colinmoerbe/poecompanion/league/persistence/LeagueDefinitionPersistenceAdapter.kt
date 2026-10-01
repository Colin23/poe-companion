package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import org.springframework.stereotype.Repository

/**
 * Persists league reference knowledge without exposing JPA representations to the domain.
 */
@Repository
internal class LeagueDefinitionPersistenceAdapter(
    private val springDataRepository: SpringDataLeagueDefinitionRepository,
) {
    fun save(leagueDefinition: LeagueDefinition) {
        springDataRepository.save(
            LeagueDefinitionEntity(
                id = leagueDefinition.id.value,
                name = leagueDefinition.name,
                category = leagueDefinition.category,
                participation = leagueDefinition.participation,
                mortality = leagueDefinition.mortality,
                realm = leagueDefinition.realm,
            ),
        )
    }

    fun findById(id: LeagueDefinitionId): LeagueDefinition? = springDataRepository
        .findById(id.value)
        .map { entity ->
            LeagueDefinition(
                id = LeagueDefinitionId(entity.id),
                name = entity.name,
                category = entity.category,
                participation = entity.participation,
                mortality = entity.mortality,
                realm = entity.realm,
            )
        }.orElse(null)
}
