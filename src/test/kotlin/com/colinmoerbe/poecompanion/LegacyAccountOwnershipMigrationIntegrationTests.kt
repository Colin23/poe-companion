package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import org.assertj.core.api.Assertions.assertThat
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

/**
 * Exercises a real upgrade from populated legacy V017 data, not only a clean V018 installation.
 * A dedicated PostgreSQL container avoids modifying the shared Spring Boot integration-test schema.
 */
@Testcontainers
internal class LegacyAccountOwnershipMigrationIntegrationTests {
    @field:Container
    private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18.6"))

    @Test
    fun `V018 preserves legacy account facts and selection`() {
        val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        migrateTo(dataSource, "017")
        val jdbc = JdbcTemplate(dataSource)
        val data = seedLegacyData(jdbc)

        migrateTo(dataSource, "018")
        verifyUpgradedData(jdbc, data)
    }

    private fun migrateTo(dataSource: DriverManagerDataSource, version: String) {
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

    private fun seedLegacyData(jdbc: JdbcTemplate): LegacyData {
        val data = LegacyData(
            league = UUID.randomUUID(),
            selectedContext = UUID.randomUUID(),
            otherContext = UUID.randomUUID(),
            unique = UUID.randomUUID(),
            source = UUID.randomUUID(),
        )
        jdbc.update(
            """
            INSERT INTO poe_companion.league_definition
                (id, name, league_type, participation, mortality, realm)
            VALUES (?, 'Legacy SSF', 'CHALLENGE', 'SSF', 'SOFTCORE', 'PC')
            """.trimIndent(),
            data.league,
        )
        jdbc.update("INSERT INTO poe_companion.unique_definition (id) VALUES (?)", data.unique)
        jdbc.update("INSERT INTO poe_companion.acquisition_source (id) VALUES (?)", data.source)
        listOf(data.selectedContext, data.otherContext).forEach { accountId ->
            jdbc.update(
                """
                INSERT INTO poe_companion.account_context
                    (id, league_definition_id, ruleset)
                VALUES (?, ?, 'NORMAL')
                """.trimIndent(),
                accountId,
                data.league,
            )
        }
        jdbc.update(
            """
            INSERT INTO poe_companion.manual_ownership
                (account_context_id, unique_definition_id, quantity)
            VALUES (?, ?, 5)
            """.trimIndent(),
            data.otherContext,
            data.unique,
        )
        jdbc.update(
            """
            INSERT INTO poe_companion.available_attempts
                (account_context_id, acquisition_source_id, attempt_count)
            VALUES (?, ?, 0)
            """.trimIndent(),
            data.selectedContext,
            data.source,
        )
        jdbc.update(
            """
            INSERT INTO poe_companion.account_evaluation_selection
                (singleton_id, account_context_id, compatibility_version_major,
                 compatibility_version_minor, game_patch, game_patch_suffix)
            VALUES (1, ?, 3, 30, 2, 'b')
            """.trimIndent(),
            data.selectedContext,
        )
        return data
    }

    private fun verifyUpgradedData(jdbc: JdbcTemplate, data: LegacyData) {
        val owners = jdbc.queryForList(
            "SELECT owner_application_user_id FROM poe_companion.account_context ORDER BY id",
            UUID::class.java,
        )
        assertThat(owners).containsExactlyInAnyOrder(
            ConfiguredApplicationUsers.USER.value,
            ConfiguredApplicationUsers.USER.value,
        )
        val quantity = jdbc.queryForObject(
            """
            SELECT quantity FROM poe_companion.manual_ownership
            WHERE account_context_id = ? AND unique_definition_id = ?
            """.trimIndent(),
            Int::class.java,
            data.otherContext,
            data.unique,
        )
        assertThat(quantity).isEqualTo(5)
        val availableAttempts = jdbc.queryForObject(
            """
            SELECT attempt_count FROM poe_companion.available_attempts
            WHERE account_context_id = ? AND acquisition_source_id = ?
            """.trimIndent(),
            Int::class.java,
            data.selectedContext,
            data.source,
        )
        assertThat(availableAttempts).isZero()

        val selection = jdbc.queryForMap(
            """
            SELECT * FROM poe_companion.account_evaluation_selection
            WHERE application_user_id = ?
            """.trimIndent(),
            ConfiguredApplicationUsers.USER.value,
        )
        assertThat(selection["account_context_id"]).isEqualTo(data.selectedContext)
        assertThat(selection["compatibility_version_major"]).isEqualTo(3)
        assertThat(selection["compatibility_version_minor"]).isEqualTo(30)
        assertThat(selection["game_patch"]).isEqualTo(2)
        assertThat(selection["game_patch_suffix"]).isEqualTo("b")
        val adminSelectionCount = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM poe_companion.account_evaluation_selection
            WHERE application_user_id = ?
            """.trimIndent(),
            Long::class.java,
            ConfiguredApplicationUsers.ADMIN.value,
        )
        assertThat(adminSelectionCount).isZero()
    }

    private data class LegacyData(
        val league: UUID,
        val selectedContext: UUID,
        val otherContext: UUID,
        val unique: UUID,
        val source: UUID,
    )
}
