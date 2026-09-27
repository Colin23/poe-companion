package com.colinmoerbe.poecompanion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForObject
import javax.sql.DataSource

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
class DatabaseMigrationIntegrationTests(
    @Autowired dataSource: DataSource,
    @Autowired private val environment: Environment,
) {
    private val jdbcTemplate = JdbcTemplate(dataSource)

    @Test
    fun usesPostgresql() {
        val databaseProductName =
            jdbcTemplate.dataSource
                ?.connection
                ?.use { it.metaData.databaseProductName }

        assertThat(databaseProductName).isEqualTo("PostgreSQL")
    }

    @Test
    fun createsApplicationSchema() {
        val schemaExists =
            jdbcTemplate.queryForObject<Boolean>(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.schemata
                    WHERE schema_name = 'poe_companion'
                )
                """.trimIndent(),
            )

        assertThat(schemaExists).isTrue()
    }

    @Test
    fun appliesInitialMigrationSuccessfully() {
        val successfulMigrationCount =
            jdbcTemplate.queryForObject<Long>(
                """
                SELECT COUNT(*)
                FROM poe_companion.flyway_schema_history
                WHERE version = '001'
                  AND success
                """.trimIndent(),
            )

        assertThat(successfulMigrationCount).isEqualTo(1)
    }

    @Test
    fun configuresHibernateForSchemaValidationOnly() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto"))
            .isEqualTo("validate")
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.default_schema"))
            .isEqualTo("poe_companion")
        assertThat(environment.getProperty("spring.jpa.open-in-view"))
            .isEqualTo("false")
    }
}
