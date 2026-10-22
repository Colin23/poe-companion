package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository

/** Maps curated league/version associations between Spring Data JPA and League domain identities. */
@Repository
internal class SelectableLeagueVersionPersistenceAdapter(
    private val springDataRepository: SpringDataSelectableLeagueVersionRepository,
) {
    fun findAll(): List<Pair<LeagueDefinitionId, CompatibilityVersion>> = springDataRepository.findAll(
        Sort.by(
            Sort.Order.desc("compatibilityVersionMajor"),
            Sort.Order.desc("compatibilityVersionMinor"),
            Sort.Order.asc("leagueDefinitionId"),
        ),
    ).map { entity ->
        LeagueDefinitionId(entity.leagueDefinitionId) to
            CompatibilityVersion(entity.compatibilityVersionMajor, entity.compatibilityVersionMinor)
    }

    fun saveIfAbsent(league: LeagueDefinitionId, version: CompatibilityVersion) {
        springDataRepository.insertIfAbsent(league.value, version.major, version.minor)
    }
}
