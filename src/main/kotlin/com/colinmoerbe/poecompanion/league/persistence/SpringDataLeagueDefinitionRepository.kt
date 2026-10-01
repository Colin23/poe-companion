package com.colinmoerbe.poecompanion.league.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

internal interface SpringDataLeagueDefinitionRepository : JpaRepository<LeagueDefinitionEntity, UUID>
