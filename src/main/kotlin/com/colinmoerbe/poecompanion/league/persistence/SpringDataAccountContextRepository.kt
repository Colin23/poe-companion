package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.Ruleset
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

internal interface SpringDataAccountContextRepository : JpaRepository<AccountContextEntity, UUID> {

    fun findByOwnerApplicationUserIdAndLeagueDefinitionIdAndRuleset(
        ownerApplicationUserId: UUID,
        leagueDefinitionId: UUID,
        ruleset: Ruleset,
    ): AccountContextEntity?

    @Modifying
    @Query(
        value =
        """
            INSERT INTO poe_companion.account_context
                (id, league_definition_id, ruleset, owner_application_user_id)
            VALUES (:id, :leagueDefinitionId, :ruleset, :ownerApplicationUserId)
            ON CONFLICT (id) DO NOTHING
            """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("leagueDefinitionId") leagueDefinitionId: UUID,
        @Param("ruleset") ruleset: String,
        @Param("ownerApplicationUserId") ownerApplicationUserId: UUID,
    ): Int
}
