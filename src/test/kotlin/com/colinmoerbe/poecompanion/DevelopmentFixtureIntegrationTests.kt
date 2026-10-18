package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.acquisition.AcquisitionQueryResult
import com.colinmoerbe.poecompanion.acquisition.AcquisitionReadModelService
import com.colinmoerbe.poecompanion.acquisition.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.BossDefinition
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionCreationConflictException
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionId
import com.colinmoerbe.poecompanion.acquisition.BossDefinitionService
import com.colinmoerbe.poecompanion.acquisition.FixedDropEstimateResolution
import com.colinmoerbe.poecompanion.acquisition.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.build.BuildReadinessQueryResult
import com.colinmoerbe.poecompanion.build.BuildReadinessService
import com.colinmoerbe.poecompanion.build.UniqueReadiness
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * Runs the real fixture explicitly with opt-in property but without the dev profile, against Testcontainers PostgreSQL.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.dev-fixture.enabled=true",
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class DevelopmentFixtureIntegrationTests(
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val buildReadiness: BuildReadinessService,
    @Autowired private val acquisitionReadModel: AcquisitionReadModelService,
    @Autowired private val ownership: ManualOwnershipService,
    @Autowired private val attempts: AvailableAttemptsService,
    @Autowired private val accountEvaluation: AccountEvaluationService,
    @Autowired private val bosses: BossDefinitionService,
    @Autowired private val uniques: UniqueDefinitionPersistenceAdapter,
    @Autowired private val accounts: AccountContextPersistenceAdapter,
    @Autowired private val context: ApplicationContext,
    @Autowired dataSource: DataSource,
) {
    private val ids = DevelopmentFixtureIds
    private val jdbc = JdbcTemplate(dataSource)

    @BeforeEach
    fun cleanBefore() = cleanFixture()

    @AfterEach
    fun cleanAfter() = cleanFixture()

    @Test
    fun `complete persisted scenario should go from partial Build X to unique ready after owning C`() {
        assertThat(context.containsBean("developmentFixtureRunner")).isFalse()
        assertThat(context.getBeansOfType(DevelopmentFixtureInitializer::class.java)).hasSize(1)
        assertThat(context.getBeansOfType(com.colinmoerbe.poecompanion.catalog.DevelopmentCatalogAccess::class.java))
            .hasSize(1)
        assertThat(context.getBeansOfType(com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess::class.java))
            .hasSize(1)
        assertThat(context.getBeansOfType(com.colinmoerbe.poecompanion.build.DevelopmentBuildAccess::class.java))
            .hasSize(1)
        assertThat(context.getBeansOfType(com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess::class.java))
            .hasSize(1)
        assertThat(uniques.findById(ids.uniqueA)).isNull()

        initializer.initialize()

        assertThat(accountEvaluation.getSelected()?.accountContextId).isEqualTo(ids.account)
        val before = buildReadiness.evaluate(ids.variant) as BuildReadinessQueryResult.Evaluated
        assertThat(before.readiness.readiness).isEqualTo(UniqueReadiness.PARTIAL)
        val beforeRequirements = before.readiness.groups.flatMap { it.requirements }
        val missing = beforeRequirements.single { it.requirement.uniqueDefinitionId == ids.uniqueC }
        assertThat(missing.ownedQuantity).isZero()
        assertThat(missing.satisfied).isFalse()

        val assessment = acquisitionReadModel.findForUnique(ids.uniqueC) as AcquisitionQueryResult.Evaluated
        val source = assessment.sources.single()
        val probability = source.probabilityKnowledge as ScopedAcquisitionKnowledge.Selected
        val estimate = probability.resolution as FixedDropEstimateResolution.Resolved
        assertThat(estimate.snapshot.model.probabilityPerAttempt).isEqualTo(0.10)
        assertThat(source.calculation?.probabilityWithinAvailableAttempts)
            .isCloseTo(0.717570463519, offset(1e-10))
        assertThat(source.calculation?.attemptsFor95Percent).isEqualTo(29)
        assertThat(source.availableAttempts?.count).isEqualTo(12)

        ownership.setOwnedQuantity(ids.account, ids.uniqueC, 1)
        val after = buildReadiness.evaluate(ids.variant) as BuildReadinessQueryResult.Evaluated
        assertThat(after.readiness.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
    }

    @Test
    fun `repeated startup should preserve changed ownership and available attempts without inserting duplicates`() {
        initializer.initialize()
        ownership.setOwnedQuantity(ids.account, ids.uniqueC, 1)
        ownership.setOwnedQuantity(ids.account, ids.uniqueA, 2)
        attempts.setAvailableAttempts(ids.account, ids.source, 4)

        // A separately selected account must remain selected after another initializer run.
        val otherAccount = AccountContext(AccountContextId.generate(), ids.league, Ruleset.NORMAL)
        accounts.save(otherAccount)
        accountEvaluation.select(otherAccount.id, CompatibilityVersion(3, 30))
        initializer.initialize()
        initializer.initialize()

        assertThat(accountEvaluation.getSelected()?.accountContextId).isEqualTo(otherAccount.id)
        accountEvaluation.select(ids.account, CompatibilityVersion(3, 30))
        val result = buildReadiness.evaluate(ids.variant) as BuildReadinessQueryResult.Evaluated
        assertThat(result.readiness.readiness).isEqualTo(UniqueReadiness.UNIQUE_READY)
        val requirements = result.readiness.groups.flatMap { it.requirements }
        val a = requirements.single { it.requirement.uniqueDefinitionId == ids.uniqueA }
        assertThat(a.ownedQuantity).isEqualTo(2)
        val source = (acquisitionReadModel.findForUnique(ids.uniqueC) as AcquisitionQueryResult.Evaluated)
            .sources.single()
        assertThat(source.availableAttempts?.count).isEqualTo(4)
        assertThat(source.calculation?.probabilityWithinAvailableAttempts).isCloseTo(0.3439, offset(1e-12))
        assertThat(count("unique_definition")).isEqualTo(3)
        assertThat(count("drop_relationship")).isEqualTo(1)
        assertThat(count("fixed_drop_estimate")).isEqualTo(1)
    }

    @Test
    fun `preexisting partial fixture should fail clearly rather than silently repairing synthetic data`() {
        uniques.save(UniqueDefinition(ids.uniqueC))

        assertThatThrownBy { initializer.initialize() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Partial development fixture")
        assertThat(uniques.findById(ids.uniqueA)).isNull()
        assertThat(count("unique_definition")).isEqualTo(1)
    }

    @Test
    fun `failure after intermediate inserts should roll back the entire initial fixture`() {
        bosses.create(BossDefinition(BossDefinitionId.generate(), "Boss Y"))

        assertThatThrownBy { initializer.initialize() }
            .isInstanceOf(BossDefinitionCreationConflictException::class.java)

        assertThat(uniques.findById(ids.uniqueA)).isNull()
        assertThat(count("build_archetype")).isZero()
        assertThat(count("account_context")).isZero()
        assertThat(count("drop_relationship")).isZero()
        assertThat(accountEvaluation.getSelected()).isNull()
    }

    private fun count(table: String): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM poe_companion.$table", Long::class.java)!!

    private fun cleanFixture() {
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
}
