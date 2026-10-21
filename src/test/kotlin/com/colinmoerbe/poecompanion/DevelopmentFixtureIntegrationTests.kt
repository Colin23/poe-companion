package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.knowledge.ScopedAcquisitionKnowledge
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateResolution
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionQueryResult
import com.colinmoerbe.poecompanion.acquisition.readmodel.AcquisitionReadModelService
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipResolution
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinition
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionCreationConflictException
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionService
import com.colinmoerbe.poecompanion.build.DevelopmentBuildAccess
import com.colinmoerbe.poecompanion.build.readiness.UniqueReadiness
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessQueryResult
import com.colinmoerbe.poecompanion.build.readmodel.BuildReadinessService
import com.colinmoerbe.poecompanion.catalog.DevelopmentCatalogAccess
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import com.colinmoerbe.poecompanion.security.identity.DevelopmentFixtureActor
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
import org.springframework.security.test.context.support.WithMockUser
import javax.sql.DataSource

/**
 * Runs the real fixture explicitly with opt-in property but without the dev profile, against Testcontainers PostgreSQL.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.dev-fixture.enabled=true",
        "poe-companion.security.user.username=test-user",
        "poe-companion.security.user.password=test-password",
        "poe-companion.security.admin.username=test-admin",
        "poe-companion.security.admin.password=test-admin-password",
    ],
)
@WithMockUser(username = "test-user")
internal class DevelopmentFixtureIntegrationTests(
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val buildReadiness: BuildReadinessService,
    @Autowired private val acquisitionReadModel: AcquisitionReadModelService,
    @Autowired private val ownership: ManualOwnershipService,
    @Autowired private val attempts: AvailableAttemptsService,
    @Autowired private val accountEvaluation: AccountEvaluationService,
    @Autowired private val bosses: BossDefinitionService,
    @Autowired private val fixtureActor: DevelopmentFixtureActor,
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
        assertThat(context.getBeansOfType(DevelopmentCatalogAccess::class.java))
            .hasSize(1)
        assertThat(context.getBeansOfType(DevelopmentLeagueAccess::class.java))
            .hasSize(1)
        assertThat(context.getBeansOfType(DevelopmentBuildAccess::class.java))
            .hasSize(1)
        assertThat(context.getBeansOfType(DevelopmentAcquisitionAccess::class.java))
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
        val otherAccount = AccountContext(
            AccountContextId.generate(),
            ids.league,
            Ruleset.NORMAL,
            ConfiguredApplicationUsers.USER,
        )
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
        assertThat(count("unique_definition")).isEqualTo(12)
        assertThat(count("build_archetype")).isEqualTo(4)
        assertThat(count("acquisition_source")).isEqualTo(4)
        assertThat(count("account_context")).isEqualTo(4) // Three fixture contexts plus the test-created USER context.
        assertThat(count("drop_relationship")).isEqualTo(5)
        assertThat(count("fixed_drop_estimate")).isEqualTo(3)
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
        fixtureActor.asAdmin { bosses.create(BossDefinition(BossDefinitionId.generate(), "Boss Y")) }

        assertThatThrownBy { initializer.initialize() }
            .isInstanceOf(BossDefinitionCreationConflictException::class.java)

        assertThat(uniques.findById(ids.uniqueA)).isNull()
        assertThat(count("build_archetype")).isZero()
        assertThat(count("account_context")).isZero()
        assertThat(count("drop_relationship")).isZero()
        assertThat(accountEvaluation.getSelected()).isNull()
    }

    @Test
    fun `expanded fixture shares four Builds but derives independent readiness for each context`() {
        initializer.initialize()

        assertReadiness(ids.variant, UniqueReadiness.PARTIAL)
        assertReadiness(ids.variantP, UniqueReadiness.BLOCKED)
        assertReadiness(ids.variantQ, UniqueReadiness.PARTIAL)
        assertReadiness(ids.variantR, UniqueReadiness.UNIQUE_READY)

        fixtureActor.asAdmin {
            assertThat(accountEvaluation.getSelected()?.accountContextId).isEqualTo(ids.adminChallengeAccount)
            assertReadiness(ids.variant, UniqueReadiness.UNIQUE_READY)
            assertReadiness(ids.variantP, UniqueReadiness.PARTIAL)
            assertReadiness(ids.variantQ, UniqueReadiness.UNIQUE_READY)
            assertReadiness(ids.variantR, UniqueReadiness.UNIQUE_READY)

            assertThat(attempts.getAvailableAttempts(ids.adminChallengeAccount, ids.source)?.count).isZero()
            assertThat(attempts.getAvailableAttempts(ids.adminChallengeAccount, ids.sourceP)?.count).isEqualTo(4)
            assertThat(attempts.getAvailableAttempts(ids.adminStandardAccount, ids.sourceP)?.count).isZero()
            assertThat(attempts.getAvailableAttempts(ids.adminStandardAccount, ids.sourceQ)?.count).isEqualTo(2)
            assertThat(attempts.getAvailableAttempts(ids.adminStandardAccount, ids.source)).isNull()

            accountEvaluation.select(ids.adminStandardAccount, CompatibilityVersion(3, 30))
            assertReadiness(ids.variant, UniqueReadiness.BLOCKED)
            assertReadiness(ids.variantP, UniqueReadiness.UNIQUE_READY)
            assertReadiness(ids.variantQ, UniqueReadiness.PARTIAL)
            assertReadiness(ids.variantR, UniqueReadiness.UNIQUE_READY)
        }
        assertThat(accountEvaluation.getSelected()?.accountContextId).isEqualTo(ids.account)
        assertThat(attempts.getAvailableAttempts(ids.account, ids.source)?.count).isEqualTo(12)

        val sourceP = acquisitionReadModel.findForSource(ids.sourceP)!!
        assertThat(
            sourceP.relationships.single { it.relationship.uniqueDefinitionId == ids.uniqueD }
                .assessment?.calculation,
        ).isNotNull()
        val negative = sourceP.relationships.single { it.relationship.uniqueDefinitionId == ids.uniqueE }
        val relationship = negative.assessment?.relationshipKnowledge as ScopedAcquisitionKnowledge.Selected
        assertThat((relationship.resolution as DropRelationshipResolution.Resolved).snapshot.state)
            .isEqualTo(DropRelationshipState.CANNOT_DROP)
        assertThat(negative.assessment?.calculation).isNull()

        val noEstimate = acquisitionReadModel.findForSource(ids.sourceQ)!!.relationships.single()
        assertThat(noEstimate.assessment?.calculation).isNull()
        val unavailable = acquisitionReadModel.findForSource(ids.sourceR)!!.relationships.single()
        val accepted = unavailable.assessment?.probabilityKnowledge as ScopedAcquisitionKnowledge.Selected
        assertThat(accepted.resolution).isInstanceOf(FixedDropEstimateResolution.Resolved::class.java)
        assertThat(unavailable.assessment?.calculation).isNull()

        assertThat(count("unique_definition")).isEqualTo(12)
        assertThat(count("league_definition")).isEqualTo(2)
        assertThat(count("account_context")).isEqualTo(3)
        assertThat(count("build_archetype")).isEqualTo(4)
        assertThat(count("acquisition_source")).isEqualTo(4)
        assertThat(count("drop_relationship")).isEqualTo(5)
        assertThat(count("fixed_drop_estimate")).isEqualTo(3)
    }

    @Test
    fun `rerunning the expanded fixture never restores cleared attempts or USER selection`() {
        initializer.initialize()
        fixtureActor.asAdmin {
            attempts.clearAvailableAttempts(ids.adminChallengeAccount, ids.source)
            attempts.setAvailableAttempts(ids.adminChallengeAccount, ids.sourceP, 0)
            accountEvaluation.select(ids.adminStandardAccount, CompatibilityVersion(3, 30))
        }
        jdbc.update(
            "DELETE FROM poe_companion.account_evaluation_selection WHERE application_user_id = ?",
            ConfiguredApplicationUsers.USER.value,
        )

        initializer.initialize()
        initializer.initialize()

        assertThat(accountEvaluation.getSelected()).isNull()
        fixtureActor.asAdmin {
            assertThat(accountEvaluation.getSelected()?.accountContextId).isEqualTo(ids.adminStandardAccount)
            assertThat(attempts.getAvailableAttempts(ids.adminChallengeAccount, ids.source)).isNull()
            assertThat(attempts.getAvailableAttempts(ids.adminChallengeAccount, ids.sourceP)?.count).isZero()
        }
    }

    @Test
    fun `partial expanded identities cannot be treated as a clean fixture`() {
        uniques.save(UniqueDefinition(ids.uniqueG))

        assertThatThrownBy { initializer.initialize() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Partial development fixture")

        assertThat(count("unique_definition")).isEqualTo(1)
        assertThat(count("account_context")).isZero()
    }

    private fun assertReadiness(
        variantId: com.colinmoerbe.poecompanion.build.identity.BuildVariantId,
        expected: UniqueReadiness,
    ) {
        val result = buildReadiness.evaluate(variantId) as BuildReadinessQueryResult.Evaluated
        assertThat(result.readiness.readiness).isEqualTo(expected)
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
