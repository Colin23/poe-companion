package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

/** Verifies V018 -> V019 on real PostgreSQL, including populated and invalid legacy shapes. */
@Testcontainers
internal class GameEnvironmentSelectionMigrationIntegrationTests {
    @field:Container
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))

    @Test
    fun `V019 preserves the per-user league version patch and personal context identity`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        migrate(source, "018")
        val jdbc = JdbcTemplate(source)
        val league = UUID.randomUUID()
        val context = UUID.randomUUID()
        seedLeague(jdbc, league)
        seedAccount(jdbc, league, context)
        jdbc.update(
            """
            INSERT INTO poe_companion.account_evaluation_selection
                (application_user_id, account_context_id, compatibility_version_major,
                 compatibility_version_minor, game_patch, game_patch_suffix)
            VALUES (?, ?, 3, 30, 2, 'b')
            """.trimIndent(),
            ConfiguredApplicationUsers.USER.value,
            context,
        )

        migrate(source, "019")

        val row = jdbc.queryForMap(
            "SELECT * FROM poe_companion.account_evaluation_selection WHERE application_user_id = ?",
            ConfiguredApplicationUsers.USER.value,
        )
        assertThat(row["league_definition_id"]).isEqualTo(league)
        assertThat(row["ruleset"]).isEqualTo("NORMAL")
        assertThat(row["compatibility_version_major"]).isEqualTo(3)
        assertThat(row["compatibility_version_minor"]).isEqualTo(30)
        assertThat(row["game_patch"]).isEqualTo(2)
        assertThat(row["game_patch_suffix"]).isEqualTo("b")
        assertThat(row).doesNotContainKey("account_context_id")
        assertThat(jdbc.queryForObject("SELECT id FROM poe_companion.account_context", UUID::class.java)).isEqualTo(context)
        assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM poe_companion.account_evaluation_selection WHERE application_user_id = ?",
                Long::class.java,
                ConfiguredApplicationUsers.ADMIN.value,
            ),
        ).isZero()
    }

    @Test
    fun `V019 explicitly refuses duplicate contexts instead of destroying personal data`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        migrate(source, "018")
        val jdbc = JdbcTemplate(source)
        val league = UUID.randomUUID()
        seedLeague(jdbc, league)
        seedAccount(jdbc, league, UUID.randomUUID())
        seedAccount(jdbc, league, UUID.randomUUID())

        assertThatThrownBy { migrate(source, "019") }
            .hasMessageContaining("Migration")
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM poe_companion.account_context", Long::class.java))
            .isEqualTo(2L)
    }

    private fun migrate(source: DriverManagerDataSource, version: String) {
        Flyway.configure()
            .dataSource(source)
            .schemas("poe_companion")
            .defaultSchema("poe_companion")
            .createSchemas(true)
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion(version))
            .load()
            .migrate()
    }

    private fun seedLeague(jdbc: JdbcTemplate, id: UUID) {
        jdbc.update(
            """
            INSERT INTO poe_companion.league_definition (id, name, league_type, participation, mortality, realm)
            VALUES (?, 'Fixture League', 'CHALLENGE', 'SSF', 'SOFTCORE', 'PC')
            """.trimIndent(),
            id,
        )
    }

    private fun seedAccount(jdbc: JdbcTemplate, league: UUID, id: UUID) {
        jdbc.update(
            """
            INSERT INTO poe_companion.account_context (id, league_definition_id, ruleset, owner_application_user_id)
            VALUES (?, ?, 'NORMAL', ?)
            """.trimIndent(),
            id,
            league,
            ConfiguredApplicationUsers.USER.value,
        )
    }
}
