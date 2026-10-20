package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

/**
 * Exercises a real upgrade from populated legacy V017 data, not only a clean V018 installation.
 * A dedicated PostgreSQL container avoids modifying the shared Spring Boot integration-test schema.
 */
internal class LegacyAccountOwnershipMigrationIntegrationTests {
    @Test
    fun `V018 assigns all legacy contexts to USER and preserves personal facts and selection`() {
        val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))
        postgres.start()
        try {
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            val jdbc = JdbcTemplate(dataSource)
            fun migrateTo(version: String) {
                Flyway.configure()
                    .dataSource(dataSource)
                    .schemas("poe_companion")
                    .defaultSchema("poe_companion")
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion(version))
                    .load()
                    .migrate()
            }

            migrateTo("017")

            val league = UUID.randomUUID()
            val selectedContext = UUID.randomUUID()
            val otherContext = UUID.randomUUID()
            val unique = UUID.randomUUID()
            val source = UUID.randomUUID()
            jdbc.update(
                """
                INSERT INTO poe_companion.league_definition
                    (id, name, league_type, participation, mortality, realm)
                VALUES (?, 'Legacy SSF', 'CHALLENGE', 'SSF', 'SOFTCORE', 'PC')
                """.trimIndent(),
                league,
            )
            jdbc.update("INSERT INTO poe_companion.unique_definition (id) VALUES (?)", unique)
            jdbc.update("INSERT INTO poe_companion.acquisition_source (id) VALUES (?)", source)
            listOf(selectedContext, otherContext).forEach { accountId ->
                jdbc.update(
                    "INSERT INTO poe_companion.account_context (id, league_definition_id, ruleset) VALUES (?, ?, 'NORMAL')",
                    accountId,
                    league,
                )
            }
            jdbc.update(
                "INSERT INTO poe_companion.manual_ownership (account_context_id, unique_definition_id, quantity) VALUES (?, ?, 5)",
                otherContext,
                unique,
            )
            jdbc.update(
                "INSERT INTO poe_companion.available_attempts (account_context_id, acquisition_source_id, attempt_count) VALUES (?, ?, 0)",
                selectedContext,
                source,
            )
            jdbc.update(
                """
                INSERT INTO poe_companion.account_evaluation_selection
                    (singleton_id, account_context_id, compatibility_version_major,
                     compatibility_version_minor, game_patch, game_patch_suffix)
                VALUES (1, ?, 3, 30, 2, 'b')
                """.trimIndent(),
                selectedContext,
            )

            migrateTo("018")

            val owners = jdbc.queryForList(
                "SELECT owner_application_user_id FROM poe_companion.account_context ORDER BY id",
                UUID::class.java,
            )
            assertThat(owners).containsExactlyInAnyOrder(
                ConfiguredApplicationUsers.USER.value,
                ConfiguredApplicationUsers.USER.value,
            )
            assertThat(jdbc.queryForObject(
                "SELECT quantity FROM poe_companion.manual_ownership WHERE account_context_id = ? AND unique_definition_id = ?",
                Int::class.java,
                otherContext,
                unique,
            )).isEqualTo(5)
            assertThat(jdbc.queryForObject(
                "SELECT attempt_count FROM poe_companion.available_attempts WHERE account_context_id = ? AND acquisition_source_id = ?",
                Int::class.java,
                selectedContext,
                source,
            )).isZero()

            val selection = jdbc.queryForMap(
                "SELECT * FROM poe_companion.account_evaluation_selection WHERE application_user_id = ?",
                ConfiguredApplicationUsers.USER.value,
            )
            assertThat(selection["account_context_id"]).isEqualTo(selectedContext)
            assertThat(selection["compatibility_version_major"]).isEqualTo(3)
            assertThat(selection["compatibility_version_minor"]).isEqualTo(30)
            assertThat(selection["game_patch"]).isEqualTo(2)
            assertThat(selection["game_patch_suffix"]).isEqualTo("b")
            assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM poe_companion.account_evaluation_selection WHERE application_user_id = ?",
                Long::class.java,
                ConfiguredApplicationUsers.ADMIN.value,
            )).isZero()
        } finally {
            postgres.stop()
        }
    }
}
