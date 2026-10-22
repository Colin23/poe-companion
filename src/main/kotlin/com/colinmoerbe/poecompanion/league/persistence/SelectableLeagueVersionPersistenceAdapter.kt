package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/** League-owned storage of curated league/version choices; not a lifecycle calendar. */
@Repository
internal class SelectableLeagueVersionPersistenceAdapter(private val jdbc: JdbcTemplate) {
    fun findAll(): List<Pair<LeagueDefinitionId, CompatibilityVersion>> = jdbc.query(
        """
        SELECT league_definition_id, compatibility_version_major, compatibility_version_minor
        FROM poe_companion.selectable_league_version
        ORDER BY compatibility_version_major DESC, compatibility_version_minor DESC, league_definition_id
        """.trimIndent(),
    ) { row, _ ->
        LeagueDefinitionId(row.getObject("league_definition_id", UUID::class.java)) to
            CompatibilityVersion(row.getInt("compatibility_version_major"), row.getInt("compatibility_version_minor"))
    }

    fun saveIfAbsent(league: LeagueDefinitionId, version: CompatibilityVersion) {
        jdbc.update(
            """
            INSERT INTO poe_companion.selectable_league_version
                (league_definition_id, compatibility_version_major, compatibility_version_minor)
            VALUES (?, ?, ?)
            ON CONFLICT DO NOTHING
            """.trimIndent(),
            league.value,
            version.major,
            version.minor,
        )
    }
}
