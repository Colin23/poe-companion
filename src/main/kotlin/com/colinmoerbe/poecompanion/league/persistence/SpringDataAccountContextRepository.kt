package com.colinmoerbe.poecompanion.league.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataAccountContextRepository : JpaRepository<AccountContextEntity, UUID> {

    @Modifying
    @Query(
        value =
        """
            INSERT INTO poe_companion.account_context (id, league_definition_id, ruleset)
            VALUES (:id, :leagueDefinitionId, :ruleset)
            ON CONFLICT (id) DO NOTHING
            """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("leagueDefinitionId") leagueDefinitionId: UUID,
        @Param("ruleset") ruleset: String,
    ): Int
}
