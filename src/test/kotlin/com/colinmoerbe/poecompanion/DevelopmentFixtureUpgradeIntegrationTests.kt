package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.account.CurrentOwnershipService
import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.encounter.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.encounter.BossParticipation
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.probability.DropEstimateProvenance
import com.colinmoerbe.poecompanion.acquisition.probability.FixedBernoulliModel
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateService
import com.colinmoerbe.poecompanion.acquisition.probability.FixedDropEstimateSnapshot
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipSnapshot
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinition
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.BossDefinitionService
import com.colinmoerbe.poecompanion.build.DevelopmentBuildAccess
import com.colinmoerbe.poecompanion.build.identity.BuildArchetype
import com.colinmoerbe.poecompanion.build.identity.BuildVariant
import com.colinmoerbe.poecompanion.build.requirements.Requirement
import com.colinmoerbe.poecompanion.build.requirements.RequirementGroup
import com.colinmoerbe.poecompanion.build.requirements.RequirementImportance
import com.colinmoerbe.poecompanion.build.requirements.RequirementLogic
import com.colinmoerbe.poecompanion.build.revision.BuildVariantRevision
import com.colinmoerbe.poecompanion.catalog.DevelopmentCatalogAccess
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import com.colinmoerbe.poecompanion.security.identity.DevelopmentFixtureActor
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.context.support.WithMockUser
import java.time.Instant
import javax.sql.DataSource

/**
 * Reconstructs PR B-era canonical fixture data without running the new initializer.
 * This deliberately preserves the historical fixture shape for additive-upgrade regression tests.
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
@Suppress("LongParameterList")
internal class DevelopmentFixtureUpgradeIntegrationTests(
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val catalog: DevelopmentCatalogAccess,
    @Autowired private val league: DevelopmentLeagueAccess,
    @Autowired private val builds: DevelopmentBuildAccess,
    @Autowired private val bosses: BossDefinitionService,
    @Autowired private val acquisition: DevelopmentAcquisitionAccess,
    @Autowired private val encounters: BossEncounterKnowledgeService,
    @Autowired private val relationships: DropRelationshipKnowledgeService,
    @Autowired private val estimates: FixedDropEstimateService,
    @Autowired private val ownership: ManualOwnershipService,
    @Autowired private val currentOwnership: CurrentOwnershipService,
    @Autowired private val attempts: AvailableAttemptsService,
    @Autowired private val selection: AccountEvaluationService,
    @Autowired private val actor: DevelopmentFixtureActor,
    @Autowired dataSource: DataSource,
) {
    private val ids = DevelopmentFixtureIds
    private val jdbc = JdbcTemplate(dataSource)
    private val version = CompatibilityVersion(3, 30)
    private val scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL)

    @BeforeEach
    fun before() = clearFixture()

    @AfterEach
    fun after() = clearFixture()

    @Test
    fun `complete legacy fixture upgrades without resetting USER edits`() {
        seedLegacyFixture()
        ownership.setOwnedQuantity(ids.account, ids.uniqueC, 2)
        ownership.setOwnedQuantity(ids.account, ids.uniqueA, 3)
        attempts.setAvailableAttempts(ids.account, ids.source, 0)

        initializer.initialize()
        initializer.initialize()

        assertThat(selection.getSelected()?.accountContextId).isEqualTo(ids.account)
        assertThat(currentOwnership.getCurrentOwnership(ids.account).quantityOf(ids.uniqueA)).isEqualTo(3)
        assertThat(currentOwnership.getCurrentOwnership(ids.account).quantityOf(ids.uniqueC)).isEqualTo(2)
        assertThat(attempts.getAvailableAttempts(ids.account, ids.source)?.count).isZero()

        actor.asAdmin {
            assertThat(selection.getSelected()?.accountContextId).isEqualTo(ids.adminChallengeAccount)
            assertThat(attempts.getAvailableAttempts(ids.adminChallengeAccount, ids.source)?.count).isZero()
        }
        assertThat(count("unique_definition")).isEqualTo(12)
        assertThat(count("account_context")).isEqualTo(3)
        assertThat(count("build_archetype")).isEqualTo(4)
        assertThat(count("acquisition_source")).isEqualTo(4)
    }

    @Test
    fun `partial expansion next to legacy fixture fails without recreating personal data`() {
        seedLegacyFixture()
        actor.asAdmin { catalog.save(UniqueDefinition(ids.uniqueD)) }

        assertThatThrownBy { initializer.initialize() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Partial expanded development fixture")

        assertThat(count("unique_definition")).isEqualTo(4)
        assertThat(count("account_context")).isEqualTo(1)
        assertThat(count("build_archetype")).isEqualTo(1)
        assertThat(count("acquisition_source")).isEqualTo(1)
        assertThat(selection.getSelected()?.accountContextId).isEqualTo(ids.account)
    }

    private fun seedLegacyFixture() {
        seedLegacyCanonicalKnowledge()
        seedLegacyUserState()
    }

    private fun seedLegacyCanonicalKnowledge() {
        actor.asAdmin {
            listOf(ids.uniqueA, ids.uniqueB, ids.uniqueC).forEach { catalog.save(UniqueDefinition(it)) }
            league.saveLeague(
                LeagueDefinition(
                    id = ids.league,
                    name = "Development SSF league",
                    type = LeagueType.CHALLENGE,
                    participation = LeagueParticipation.SSF,
                    mortality = LeagueMortality.SOFTCORE,
                    realm = GameRealm.PC,
                ),
            )
            seedLegacyBuild()
            seedLegacyAcquisition()
        }
    }

    private fun seedLegacyBuild() {
        val revision = BuildVariantRevision(ids.revision, ids.variant, version)
        revision.updateRequirementGroups(
            listOf(
                RequirementGroup(
                    RequirementImportance.ENABLING,
                    RequirementLogic.ALL,
                    listOf(Requirement(ids.uniqueA, 1)),
                ),
                RequirementGroup(
                    RequirementImportance.CORE,
                    RequirementLogic.ALL,
                    listOf(Requirement(ids.uniqueB, 1), Requirement(ids.uniqueC, 1)),
                ),
            ),
        )
        builds.createAndActivate(
            BuildArchetype(ids.archetype, "Build X"),
            BuildVariant(ids.variant, ids.archetype),
            revision,
        )
    }

    private fun seedLegacyAcquisition() {
        bosses.create(BossDefinition(ids.boss, "Boss Y"))
        acquisition.saveSource(AcquisitionSource(ids.source))
        encounters.recordSnapshot(
            ids.source, scope,
            BossEncounterKnowledgeSnapshot(
                null,
                "Boss Y",
                AcquisitionSourceAvailability.AVAILABLE,
                BossParticipation.Fixed(setOf(ids.boss)),
            ),
            null,
        )
        acquisition.saveRelationship(DropRelationship(ids.relationship, ids.source, ids.uniqueC))
        relationships.recordSnapshot(
            ids.relationship,
            scope,
            DropRelationshipSnapshot(null, DropRelationshipState.CAN_DROP),
            null,
        )
        estimates.publish(
            ids.relationship, scope,
            FixedDropEstimateSnapshot(
                id = ids.estimate,
                effectiveFromPatch = null,
                model = FixedBernoulliModel(0.10),
                provenance = DropEstimateProvenance.MEASURED,
                evidenceReference = "synthetic-development-fixture:boss-y-to-unique-c",
                reviewedAt = Instant.parse("2026-10-01T00:00:00Z"),
            ),
            null,
        )
    }

    private fun seedLegacyUserState() {
        actor.asLegacyUser {
            league.saveAccount(AccountContext(ids.account, ids.league, Ruleset.NORMAL, ConfiguredApplicationUsers.USER))
            ownership.setOwnedQuantity(ids.account, ids.uniqueA, 1)
            ownership.setOwnedQuantity(ids.account, ids.uniqueB, 1)
            attempts.setAvailableAttempts(ids.account, ids.source, 12)
            selection.select(ids.account, version)
        }
    }

    private fun count(table: String): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM poe_companion.$table", Long::class.java)!!

    private fun clearFixture() {
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
