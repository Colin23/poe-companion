package com.colinmoerbe.poecompanion.browser.acquisitions

import com.colinmoerbe.poecompanion.acquisition.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceAvailability
import com.colinmoerbe.poecompanion.acquisition.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.BossEncounterKnowledgeSnapshot
import com.colinmoerbe.poecompanion.acquisition.BossParticipation
import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.acquisition.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipKnowledgeService
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipSnapshot
import com.colinmoerbe.poecompanion.acquisition.DropRelationshipState
import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
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

/** HTTP rendering of source discovery, independent knowledge and read-only probability previews. */
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
internal class AcquisitionBrowserControllerIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val attempts: AvailableAttemptsService,
    @Autowired private val sourceAccess: DevelopmentAcquisitionAccess,
    @Autowired private val encounters: BossEncounterKnowledgeService,
    @Autowired private val drops: DropRelationshipKnowledgeService,
    @Autowired private val selection: SpringDataAccountEvaluationSelectionRepository,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val ids = DevelopmentFixtureIds

    @BeforeEach
    fun beforeEach() = clearDatabase()

    @AfterEach
    fun afterEach() = clearDatabase()

    @Test
    fun `Boss Y list and detail show canonical fixed model and provenance`() {
        initializer.initialize()
        val list = listPage()
        assertThat(list.accountSelected).isTrue()
        assertThat(list.sources).hasSize(1)
        assertThat(list.sources.single().displayName).isEqualTo("Boss Y")
        assertThat(list.sources.single().availability).isEqualTo("AVAILABLE")
        assertThat(list.sources.single().relationshipCount).isEqualTo(1)

        val page = detailPage()
        assertThat(page.displayName).isEqualTo("Boss Y")
        assertThat(page.savedAttempts).isEqualTo(12)
        assertThat(page.previewAttempts).isNull()
        assertThat(page.effectiveAttempts).isEqualTo(12)
        val unique = page.relationships.single()
        assertThat(unique.displayName).isEqualTo("Unique C")
        assertThat(unique.syntheticLabel).isTrue()
        assertThat(unique.relationshipStatus).isEqualTo("CAN_DROP")
        assertThat(unique.probabilityStatus).isEqualTo("Accepted fixed estimate")
        assertThat(unique.chancePerAttempt).isEqualTo("10.000%")
        assertThat(unique.estimateProvenance).isEqualTo("MEASURED")
        assertThat(unique.evidenceReference).contains("synthetic-development-fixture")
        assertThat(unique.actionable).isTrue()
        assertThat(unique.probabilityWithinAttempts).isEqualTo("71.8%")
        assertThat(unique.expectedAttempts).isEqualTo("10")
        assertThat(unique.thresholds.single { it.percentage == 95 }.attempts).isEqualTo(29)

        mockMvc.perform(get("/acquisitions/${ids.source.value}").with(user("test-user")))
            .andExpect(content().string(containsString("71.8%")))
            .andExpect(content().string(containsString("Synthetic development item")))
            .andExpect(content().string(containsString("/uniques/${ids.uniqueC.value}")))
            .andExpect(content().string(containsString("synthetic-development-fixture")))
    }

    @Test
    fun `GET preview changes calculated attempts without changing saved attempts`() {
        initializer.initialize()
        val preview = detailPage("20")
        assertThat(preview.savedAttempts).isEqualTo(12)
        assertThat(preview.previewAttempts).isEqualTo(20)
        assertThat(preview.effectiveAttempts).isEqualTo(20)
        assertThat(preview.relationships.single().probabilityWithinAttempts).isEqualTo("87.8%")
        assertThat(attempts.getAvailableAttempts(ids.account, ids.source)?.count).isEqualTo(12)

        val zero = detailPage("0")
        assertThat(zero.previewAttempts).isZero()
        assertThat(zero.relationships.single().probabilityWithinAttempts).isEqualTo("0.0%")
        assertThat(detailPage().relationships.single().probabilityWithinAttempts).isEqualTo("71.8%")
    }

    @Test
    fun `missing saved attempts are not treated as zero`() {
        initializer.initialize()
        attempts.clearAvailableAttempts(ids.account, ids.source)

        val page = detailPage()
        assertThat(page.savedAttempts).isNull()
        assertThat(page.effectiveAttempts).isNull()
        assertThat(page.relationships.single().actionable).isTrue()
        assertThat(page.relationships.single().probabilityWithinAttempts).isNull()
        assertThat(page.relationships.single().thresholds.single { it.percentage == 95 }.attempts).isEqualTo(29)

        val html = mockMvc.perform(get("/acquisitions/${ids.source.value}").with(user("test-user")))
            .andExpect(content().string(containsString("Not recorded")))
            .andReturn().response.contentAsString
        assertThat(html).containsPattern("""<input\b[^>]*\bid="preview-attempts"[^>]*\brequired\b""")
    }

    @Test
    fun `sources remain browsable without selected account or relationships`() {
        initializer.initialize()
        val orphan = AcquisitionSourceId.generate()
        sourceAccess.saveSource(AcquisitionSource(orphan))
        selection.deleteAllInBatch()

        val list = listPage()
        assertThat(list.accountSelected).isFalse()
        assertThat(list.sources).hasSize(2)
        assertThat(list.sources.single { it.id == orphan.value }.relationshipCount).isZero()
        assertThat(list.sources).allMatch { it.availability.contains("no account selected") }

        val known = detailPage()
        assertThat(known.accountSelected).isFalse()
        assertThat(known.relationships.single().actionable).isFalse()
        assertThat(known.relationships.single().relationshipStatus).contains("no account selected")

        val noRelationships = detailPage(id = orphan)
        assertThat(noRelationships.relationships).isEmpty()
        assertThat(noRelationships.displayName).contains("name unavailable")
    }

    @Test
    fun `ambiguous source scope blocks actionable probability even if values agree`() {
        initializer.initialize()
        val scope = AcquisitionKnowledgeScope(
            com.colinmoerbe.poecompanion.league.CompatibilityVersion(3, 30),
            Ruleset.NORMAL,
            ids.league,
        )
        encounters.recordSnapshot(
            ids.source,
            scope,
            BossEncounterKnowledgeSnapshot(
                null,
                "Boss Y",
                AcquisitionSourceAvailability.AVAILABLE,
                BossParticipation.Fixed(setOf(ids.boss)),
            ),
            null,
        )

        val page = detailPage()
        assertThat(page.availability).isEqualTo("Ambiguous encounter scope")
        assertThat(page.relationships.single().actionable).isFalse()
        assertThat(page.relationships.single().probabilityWithinAttempts).isNull()
    }

    @Test
    fun `CANNOT_DROP relationship keeps independent source availability but no actionable calculation`() {
        initializer.initialize()
        val source = AcquisitionSourceId.generate()
        val relationship = DropRelationshipId.generate()
        sourceAccess.saveSource(AcquisitionSource(source))
        sourceAccess.saveRelationship(DropRelationship(relationship, source, ids.uniqueC))
        encounters.recordSnapshot(
            source,
            AcquisitionKnowledgeScope(com.colinmoerbe.poecompanion.league.CompatibilityVersion(3, 30), Ruleset.NORMAL),
            BossEncounterKnowledgeSnapshot(
                null,
                "Alternative Boss",
                AcquisitionSourceAvailability.AVAILABLE,
                BossParticipation.ConfigurationDependent,
            ),
            null,
        )
        drops.recordSnapshot(
            relationship,
            AcquisitionKnowledgeScope(com.colinmoerbe.poecompanion.league.CompatibilityVersion(3, 30), Ruleset.NORMAL),
            DropRelationshipSnapshot(null, DropRelationshipState.CANNOT_DROP),
            null,
        )

        val page = detailPage(id = source)
        assertThat(page.availability).isEqualTo("AVAILABLE")
        assertThat(page.relationships.single().relationshipStatus).isEqualTo("CANNOT_DROP")
        assertThat(page.relationships.single().actionable).isFalse()
    }

    @Test
    fun `invalid attempt inputs and source IDs return proper HTTP responses`() {
        initializer.initialize()
        listOf("", "-1", "1.5", "text", "2147483648").forEach { raw ->
            mockMvc.perform(get("/acquisitions/${ids.source.value}").param("attempts", raw).with(user("test-user")))
                .andExpect(status().isBadRequest)
        }
        mockMvc.perform(get("/acquisitions/${AcquisitionSourceId.generate().value}").with(user("test-user")))
            .andExpect(status().isNotFound)
        mockMvc.perform(get("/acquisitions/not-a-uuid").with(user("test-user")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `empty source catalog and anonymous navigation are safe`() {
        assertThat(listPage().sources).isEmpty()
        mockMvc.perform(get("/acquisitions").with(user("test-user")))
            .andExpect(content().string(containsString("No acquisition sources are known yet")))

        initializer.initialize()
        mockMvc.perform(get("/acquisitions").with(user("test-user")))
            .andExpect(content().string(containsString(">Acquisition</a>")))
            .andExpect(content().string(containsString("action=\"/logout\"")))
        mockMvc.perform(get("/acquisitions"))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, containsString("/login")))
        mockMvc.perform(get("/acquisitions/${ids.source.value}"))
            .andExpect(status().is3xxRedirection)
    }

    private fun listPage(): AcquisitionBrowserListPage {
        val response = mockMvc.perform(get("/acquisitions").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("acquisitions/list"))
            .andReturn()
        return response.modelAndView!!.model["page"] as AcquisitionBrowserListPage
    }

    private fun detailPage(
        attemptPreview: String? = null,
        id: AcquisitionSourceId = ids.source,
    ): AcquisitionBrowserDetailPage {
        val request = get("/acquisitions/${id.value}").with(user("test-user"))
        if (attemptPreview != null) request.param("attempts", attemptPreview)
        val response = mockMvc.perform(request)
            .andExpect(status().isOk)
            .andExpect(view().name("acquisitions/detail"))
            .andReturn()
        return response.modelAndView!!.model["page"] as AcquisitionBrowserDetailPage
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
