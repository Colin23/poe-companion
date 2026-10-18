package com.colinmoerbe.poecompanion

import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.acquisition.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.BossParticipation
import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.acquisition.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipSnapshot
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipState
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
@Suppress("LongParameterList")
internal class UniqueBrowserControllerIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val ownership: ManualOwnershipService,
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
            .andExpect(content().string(containsString("2</strong> owned")))
            .andExpect(content().string(containsString("1</strong> missing")))
            .andExpect(content().string(containsString("Synthetic development item")))
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

        ownership.setOwnedQuantity(ids.account, ids.uniqueC, 1)

        mockMvc.perform(get(uri).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Owned · <span>1</span> copies")))
    }

    @Test
    fun `without an account selection ownership is unknown not zero`() {
        initializer.initialize()
        selectionRepository.deleteAllInBatch()

        mockMvc.perform(get("/uniques").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("ownership is unknown")))
            .andExpect(content().string(containsString("Ownership unknown")))
            .andExpect(content().string(not(containsString("Missing · 0"))))

        mockMvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Unknown — select an account")))

        mockMvc.perform(get("/uniques").param("filter", "missing").with(user("test-user")))
            .andExpect(status().isBadRequest)
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

        mockMvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("/acquisitions/${ids.source.value}#unique-${ids.uniqueC.value}")))
            .andExpect(content().string(containsString("Unknown — no account selected")))
            .andExpect(content().string(containsString("Acquisition source (name unavailable)")))
            .andExpect(content().string(not(containsString("10.000%"))))
    }

    @Test
    fun `ambiguous source scope is not converted into actionable probability`() {
        initializer.initialize()
        val leagueScope = AcquisitionKnowledgeScope(CompatibilityVersion(3, 30), Ruleset.NORMAL, ids.league)
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
                encounters.recordSnapshot(
                    source,
                    general,
                    BossEncounterKnowledgeSnapshot(null, name, availability, BossParticipation.ConfigurationDependent),
                    null,
                )
            }
            if (state != null) {
                drops.recordSnapshot(relationship, general, DropRelationshipSnapshot(null, state), null)
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
