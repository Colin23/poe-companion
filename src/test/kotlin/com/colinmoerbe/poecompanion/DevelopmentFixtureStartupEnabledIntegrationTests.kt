package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.build.readiness.UniqueReadiness
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessQueryResult
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

/** Tests the actual ApplicationRunner path, rather than invoking initialization from a test. */
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.dev-fixture.enabled=true",
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class DevelopmentFixtureStartupEnabledIntegrationTests(
    @Autowired private val context: ApplicationContext,
    @Autowired private val readiness: BuildReadinessService,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)

    @AfterEach
    fun cleanFixture() {
        jdbc.execute(
            """
            TRUNCATE TABLE
                poe_companion.boss_definition,
                poe_companion.unique_definition,
                poe_companion.league_definition,
                poe_companion.build_archetype,
                poe_companion.acquisition_source
            CASCADE
            """.trimIndent(),
        )
    }

    @Test
    fun `dev profile and opt-in property install fixture at startup`() {
        assertThat(context.containsBean("developmentFixtureRunner")).isTrue()
        val evaluated = readiness.evaluate(DevelopmentFixtureIds.variant) as BuildReadinessQueryResult.Evaluated
        assertThat(evaluated.readiness.readiness).isEqualTo(UniqueReadiness.PARTIAL)
    }
}
