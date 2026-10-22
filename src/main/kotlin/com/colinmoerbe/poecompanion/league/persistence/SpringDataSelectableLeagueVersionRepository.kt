package com.colinmoerbe.poecompanion.league.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** JPA by default; native SQL only for PostgreSQL's atomic insert-if-absent semantics. */
internal interface SpringDataSelectableLeagueVersionRepository :
    JpaRepository<SelectableLeagueVersionEntity, SelectableLeagueVersionKey> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO poe_companion.selectable_league_version (
                league_definition_id, compatibility_version_major, compatibility_version_minor
            )
            VALUES (:leagueId, :major, :minor)
            ON CONFLICT (league_definition_id, compatibility_version_major, compatibility_version_minor)
            DO NOTHING
        """,
        nativeQuery = true,
    )
    fun insertIfAbsent(
        @Param("leagueId") leagueId: UUID,
        @Param("major") major: Int,
        @Param("minor") minor: Int,
    ): Int
}
