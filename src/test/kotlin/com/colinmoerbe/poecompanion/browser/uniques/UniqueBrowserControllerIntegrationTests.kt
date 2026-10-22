package com.colinmoerbe.poecompanion.browser.uniques

import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
import com.colinmoerbe.poecompanion.SecurityTestActors
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.account.CurrentOwnershipService
import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.account.persistence.ManualOwnershipPersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.acquisition.encounter.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.encounter.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.encounter.BossParticipation
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipSnapshot
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipState
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import com.colinmoerbe.poecompanion.security.identity.DevelopmentFixtureActor
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import javax.sql.DataSource

/** Full HTTP rendering against the real Catalog and Account services and PostgreSQL fixture records. */
@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(
    properties = [
        "poe-companion.dev-fixture.enabled=true",
        "poe-companion.security.user.username=test-user",
        "poe-companion.security.user.password=test-password",
        "poe-companion.security.admin.username=test-admin",
        "poe-companion.security.admin.password=test-admin-password",
    ],
)
@Suppress("LongParameterList")
internal class UniqueBrowserControllerIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val ownership: ManualOwnershipService,
    @Autowired private val currentOwnership: CurrentOwnershipService,
    @Autowired private val fixtureActor: DevelopmentFixtureActor,
    @Autowired private val manualOwnershipAdapter: ManualOwnershipPersistenceAdapter,
    @Autowired private val accountEvaluation: AccountEvaluationService,
    @Autowired private val accountContexts: AccountContextPersistenceAdapter,
    @Autowired private val catalog: UniqueDefinitionPersistenceAdapter,
    @Autowired private val acquisition: DevelopmentAcquisitionAccess,
    @Autowired private val encounters: BossEncounterKnowledgeService,
    @Autowired private val drops: DropRelationshipKnowledgeService,
    @Autowired private val selectionRepository: SpringDataAccountEvaluationSelectionRepository,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val ids = DevelopmentFixtureIds

    @BeforeEach
    fun beforeEach() = clearDatabase()

    @AfterEach
    fun afterEach() = clearDatabase()

    @Test
    fun `collection shows every known Unique with quantities from selected account`() {
        initializer.initialize()

        mockMvc.perform(get("/uniques").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("uniques/list"))
            .andExpect(content().string(containsString(">Unique A</a>")))
            .andExpect(content().string(containsString(">Unique B</a>")))
            .andExpect(content().string(containsString(">Unique C</a>")))
            .andExpect(content().string(containsString("3</strong> owned")))
            .andExpect(content().string(containsString("9</strong> missing")))
            .andExpect(content().string(containsString("Synthetic development item")))
    }

    @Test
    fun `expanded Unique D detail links to Boss P acquisition source`() {
        initializer.initialize()

        val html = mockMvc.perform(get("/uniques/${ids.uniqueD.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("uniques/detail"))
            .andExpect(content().string(containsString("Boss P")))
            .andExpect(content().string(containsString("CAN_DROP")))
            .andExpect(content().string(containsString("AVAILABLE")))
            .andReturn().response.contentAsString
        assertThat(html).contains("/acquisitions/${ids.sourceP.value}#unique-${ids.uniqueD.value}")
    }

    @Test
    fun `owned and missing filters use recorded quantities rather than fixture names`() {
        initializer.initialize()

        mockMvc.perform(get("/uniques").param("filter", "owned").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(">Unique A</a>")))
            .andExpect(content().string(containsString(">Unique B</a>")))
            .andExpect(content().string(not(containsString(">Unique C</a>"))))

        mockMvc.perform(get("/uniques").param("filter", "missing").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(">Unique C</a>")))
            .andExpect(content().string(not(containsString(">Unique A</a>"))))
            .andExpect(content().string(not(containsString(">Unique B</a>"))))
    }

    @Test
    fun `Unique C detail changes from missing to owned after an ownership command`() {
        initializer.initialize()
        val uri = "/uniques/${ids.uniqueC.value}"

        mockMvc.perform(get(uri).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("uniques/detail"))
            .andExpect(content().string(containsString("Missing · 0 copies")))

        fixtureActor.asLegacyUser { ownership.setOwnedQuantity(ids.account, ids.uniqueC, 1) }

        mockMvc.perform(get(uri).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Owned · <span>1</span> copies")))
    }

    @Test
    fun `without a game environment selection ownership is unknown not zero`() {
        initializer.initialize()
        selectionRepository.deleteAllInBatch()

        mockMvc.perform(get("/uniques").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("No environment selected.")))
            .andExpect(content().string(containsString("Ownership unknown")))
            .andExpect(content().string(not(containsString("Missing · 0"))))

        mockMvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Select an environment")))
            .andExpect(content().string(containsString("Unknown — no personal collection")))
            .andExpect(content().string(not(containsString("Change game environment"))))

        mockMvc.perform(get("/uniques").param("filter", "missing").with(user("test-user")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `owner can edit a Unique quantity through authenticated browser POST`() {
        initializer.initialize()
        val uri = "/uniques/${ids.uniqueC.value}"

        mockMvc.perform(get(uri).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Save ownership")))
            .andExpect(content().string(containsString("name=\"_csrf\"")))

        mockMvc.perform(post("$uri/ownership").param("quantity", "1").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, uri))

        mockMvc.perform(get(uri).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Owned · <span>1</span> copies")))
        mockMvc.perform(get("/uniques").param("filter", "owned").with(user("test-user")))
            .andExpect(content().string(containsString(">Unique C</a>")))
        assertThat(
            fixtureActor.asLegacyUser { currentOwnership.getCurrentOwnership(ids.account) }.quantityOf(ids.uniqueC),
        ).isEqualTo(1)
    }

    @Test
    fun `saving zero removes the persisted manual ownership record`() {
        initializer.initialize()
        val uri = "/uniques/${ids.uniqueA.value}/ownership"

        mockMvc.perform(post(uri).param("quantity", "0").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)

        assertThat(manualOwnershipAdapter.findBy(ids.account, ids.uniqueA)).isNull()
        mockMvc.perform(get("/uniques/${ids.uniqueA.value}").with(user("test-user")))
            .andExpect(content().string(containsString("Missing · 0 copies")))
    }

    @Test
    fun `browser ownership edits affect only the selected account context`() {
        initializer.initialize()
        val uri = "/uniques/${ids.uniqueC.value}/ownership"
        mockMvc.perform(post(uri).param("quantity", "2").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)

        val other = AccountContext(
            AccountContextId.generate(),
            ids.standardLeague,
            Ruleset.NORMAL,
            ConfiguredApplicationUsers.USER,
        )
        accountContexts.save(other)
        fixtureActor.asLegacyUser { accountEvaluation.select(other.id, CompatibilityVersion(3, 30)) }
        mockMvc.perform(post(uri).param("quantity", "3").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)

        assertThat(

            fixtureActor.asLegacyUser { currentOwnership.getCurrentOwnership(ids.account) }.quantityOf(ids.uniqueC),

        ).isEqualTo(2)
        assertThat(
            fixtureActor.asLegacyUser { currentOwnership.getCurrentOwnership(other.id) }.quantityOf(ids.uniqueC),
        ).isEqualTo(3)
    }

    @Test
    fun `missing environment selection prevents editing and explains the conflict`() {
        initializer.initialize()
        selectionRepository.deleteAllInBatch()
        val uri = "/uniques/${ids.uniqueC.value}"

        mockMvc.perform(get(uri).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Select an environment")))
            .andExpect(content().string(not(containsString("Change game environment"))))
            .andExpect(content().string(not(containsString("Save ownership"))))

        mockMvc.perform(post("$uri/ownership").param("quantity", "1").with(user("test-user")).with(csrf()))
            .andExpect(status().isConflict)
            .andExpect(view().name("uniques/ownership-error"))
            .andExpect(content().string(containsString("Ownership could not be saved")))
            .andExpect(content().string(containsString("No personal collection is available for this environment")))
            .andExpect(content().string(containsString("No changes were made")))
        assertThat(
            fixtureActor.asLegacyUser { currentOwnership.getCurrentOwnership(ids.account) }.quantityOf(ids.uniqueC),
        ).isZero()
    }

    @Test
    fun `invalid owned quantities and unknown Uniques never modify ownership`() {
        initializer.initialize()
        val uri = "/uniques/${ids.uniqueA.value}/ownership"

        listOf("-1", "not-a-number", "", "2147483648").forEach { submitted ->
            mockMvc.perform(post(uri).param("quantity", submitted).with(user("test-user")).with(csrf()))
                .andExpect(status().isBadRequest)
                .andExpect(view().name("uniques/ownership-error"))
                .andExpect(content().string(containsString("Enter a whole owned quantity of zero or greater")))
        }
        mockMvc.perform(post(uri).with(user("test-user")).with(csrf()))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(containsString("No changes were saved")))

        mockMvc.perform(
            post("/uniques/${UniqueDefinitionId.generate().value}/ownership")
                .param("quantity", "5")
                .with(user("test-user"))
                .with(csrf()),
        )
            .andExpect(status().isNotFound)
            .andExpect(content().string(containsString("This Unique is no longer available")))
        assertThat(
            fixtureActor.asLegacyUser { currentOwnership.getCurrentOwnership(ids.account) }.quantityOf(ids.uniqueA),
        ).isEqualTo(1)
    }

    @Test
    fun `ownership editing requires authentication and CSRF protection`() {
        initializer.initialize()
        val uri = "/uniques/${ids.uniqueC.value}/ownership"

        mockMvc.perform(post(uri).param("quantity", "1").with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("/login")))
        mockMvc.perform(post(uri).param("quantity", "1").with(user("test-user")))
            .andExpect(status().isForbidden)
        assertThat(
            fixtureActor.asLegacyUser { currentOwnership.getCurrentOwnership(ids.account) }.quantityOf(ids.uniqueC),
        ).isZero()
    }

    @Test
    fun `empty catalog is explained without automatically installing development data`() {
        assertThat(catalog.findAll()).isEmpty()

        mockMvc.perform(get("/uniques").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("The catalog is empty")))
    }

    @Test
    fun `non-fixture catalog identities are browsable without fabricated display names`() {
        initializer.initialize()
        val extra = UniqueDefinitionId.generate()
        catalog.save(UniqueDefinition(extra))

        mockMvc.perform(get("/uniques").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Unique (name unavailable)")))
            .andExpect(content().string(containsString(extra.value.toString())))

        mockMvc.perform(get("/uniques/${extra.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Canonical display metadata has not been imported")))
    }

    @Test
    fun `Build X to Unique C to Boss Y is a linked read-only journey`() {
        initializer.initialize()

        mockMvc.perform(get("/builds/${ids.variant.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("/uniques/${ids.uniqueC.value}")))

        val html = mockMvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Known acquisition sources")))
            .andExpect(content().string(containsString("Boss Y")))
            .andExpect(content().string(containsString("CAN_DROP")))
            .andExpect(content().string(containsString("AVAILABLE")))
            .andExpect(content().string(containsString("10.000%")))
            .andReturn().response.contentAsString
        assertThat(html).contains("/acquisitions/${ids.source.value}#unique-${ids.uniqueC.value}")

        mockMvc.perform(get("/acquisitions/${ids.source.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("id=\"unique-${ids.uniqueC.value}\"")))
    }

    @Test
    fun `Uniques A and B have no recorded acquisition sources not known negative sources`() {
        initializer.initialize()

        listOf(ids.uniqueA, ids.uniqueB).forEach { id ->
            mockMvc.perform(get("/uniques/${id.value}").with(user("test-user")))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("No acquisition sources recorded yet")))
                .andExpect(content().string(containsString("does not mean the Unique cannot be acquired")))
                .andExpect(content().string(not(containsString("Boss Y"))))
        }
    }

    @Test
    fun `recorded relationships remain visible without selected account`() {
        initializer.initialize()
        selectionRepository.deleteAllInBatch()
        val expectedLink = "/acquisitions/${ids.source.value}#unique-${ids.uniqueC.value}"

        mockMvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(expectedLink)))
            .andExpect(content().string(containsString("Unknown — no account selected")))
            .andExpect(content().string(containsString("Acquisition source (name unavailable)")))
            .andExpect(content().string(not(containsString("10.000%"))))
    }

    @Test
    fun `ambiguous source scope is not converted into actionable probability`() {
        initializer.initialize()
        val leagueScope = AcquisitionKnowledgeScope(CompatibilityVersion(3, 30), Ruleset.NORMAL, ids.league)
        SecurityTestActors.asAdmin {
            encounters.recordSnapshot(
                ids.source,
                leagueScope,
                BossEncounterKnowledgeSnapshot(
                    null,
                    "Boss Y",
                    AcquisitionSourceAvailability.AVAILABLE,
                    BossParticipation.ConfigurationDependent,
                ),
                null,
            )
        }

        mockMvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Ambiguous encounter scope")))
            .andExpect(content().string(containsString("CAN_DROP")))
            .andExpect(content().string(containsString("No applicable supported fixed-probability calculation")))
            .andExpect(content().string(not(containsString("10.000%"))))
    }

    @Test
    fun `negative unavailable and unknown sources retain independent knowledge states`() {
        initializer.initialize()
        val general = AcquisitionKnowledgeScope(CompatibilityVersion(3, 30), Ruleset.NORMAL)
        fun addSource(name: String?, availability: AcquisitionSourceAvailability?, state: DropRelationshipState?) {
            val source = AcquisitionSourceId.generate()
            val relationship = DropRelationshipId.generate()
            acquisition.saveSource(AcquisitionSource(source))
            acquisition.saveRelationship(DropRelationship(relationship, source, ids.uniqueC))
            if (name != null && availability != null) {
                SecurityTestActors.asAdmin {
                    encounters.recordSnapshot(
                        source,
                        general,
                        BossEncounterKnowledgeSnapshot(
                            null,
                            name,
                            availability,
                            BossParticipation.ConfigurationDependent,
                        ),
                        null,
                    )
                }
            }
            if (state != null) {
                SecurityTestActors.asAdmin {
                    drops.recordSnapshot(
                        relationship,
                        general,
                        DropRelationshipSnapshot(null, state),
                        null,
                    )
                }
            }
        }
        addSource("Cannot Drop Encounter", AcquisitionSourceAvailability.AVAILABLE, DropRelationshipState.CANNOT_DROP)
        addSource("Unavailable Encounter", AcquisitionSourceAvailability.UNAVAILABLE, DropRelationshipState.CAN_DROP)
        addSource(null, null, null)

        val html = mockMvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(html).contains("Cannot Drop Encounter", "CANNOT_DROP")
        assertThat(html).contains("Unavailable Encounter", "UNAVAILABLE")
        assertThat(html).contains("Unknown — no accepted encounter knowledge")
        assertThat(html).contains("Unknown — no accepted relationship knowledge")
        assertThat(html).contains("No applicable supported fixed-probability calculation")
    }

    @Test
    fun `bad IDs and unknown filters return proper HTTP errors`() {
        initializer.initialize()

        mockMvc.perform(get("/uniques/${UniqueDefinitionId.generate().value}").with(user("test-user")))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/uniques/invalid-id").with(user("test-user")))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/uniques").param("filter", "invalid").with(user("test-user")))
            .andExpect(status().isBadRequest)
        mockMvc.perform(get("/uniques").param("filter", "").with(user("test-user")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `anonymous visitors are redirected to login`() {
        mockMvc.perform(get("/uniques"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("/login")))

        mockMvc.perform(get("/uniques/${ids.uniqueC.value}"))
            .andExpect(status().is3xxRedirection)
    }

    private fun clearDatabase() {
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
