package com.colinmoerbe.poecompanion.browser.acquisitions

import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
import com.colinmoerbe.poecompanion.SecurityTestActors
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.DevelopmentAcquisitionAccess
import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
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
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
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
        "poe-companion.security.user.username=test-user",
        "poe-companion.security.user.password=test-password",
        "poe-companion.security.admin.username=test-admin",
        "poe-companion.security.admin.password=test-admin-password",
    ],
)
@Suppress("LongParameterList")
internal class AcquisitionBrowserControllerIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val attempts: AvailableAttemptsService,
    @Autowired private val fixtureActor: DevelopmentFixtureActor,
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
        assertThat(list.sources).hasSize(4)
        val bossY = list.sources.single { it.id == ids.source.value }
        assertThat(bossY.displayName).isEqualTo("Boss Y")
        assertThat(bossY.availability).isEqualTo("Available")
        assertThat(bossY.relationshipCount).isEqualTo(1)

        val bossP = list.sources.single { it.id == ids.sourceP.value }
        assertThat(bossP.displayName).isEqualTo("Boss P")
        assertThat(bossP.availability).isEqualTo("Available")
        assertThat(bossP.relationshipCount).isEqualTo(2)

        val expandedPage = detailPage(id = ids.sourceP)
        assertThat(expandedPage.displayName).isEqualTo("Boss P")
        assertThat(expandedPage.relationships).hasSize(2)
        assertThat(expandedPage.relationships.map { it.relationshipStatus })
            .containsExactlyInAnyOrder("Can drop", "Cannot drop")

        val page = detailPage()
        assertThat(page.displayName).isEqualTo("Boss Y")
        assertThat(page.savedAttempts).isEqualTo(12)
        assertThat(page.previewAttempts).isNull()
        assertThat(page.effectiveAttempts).isEqualTo(12)
        val unique = page.relationships.single()
        assertThat(unique.displayName).isEqualTo("Unique C")
        assertThat(unique.syntheticLabel).isTrue()
        assertThat(unique.relationshipStatus).isEqualTo("Can drop")
        assertThat(unique.probabilityStatus).isEqualTo("Accepted fixed estimate")
        assertThat(unique.chancePerAttempt).isEqualTo("10%")
        assertThat(unique.estimateProvenance).isEqualTo("Measured")
        assertThat(unique.evidenceReference).contains("synthetic-development-fixture")
        assertThat(unique.actionable).isTrue()
        assertThat(unique.probabilityWithinAttempts).isEqualTo("71.8%")
        assertThat(unique.expectedAttempts).isEqualTo("10")
        assertThat(unique.thresholds.single { it.percentage == 95 }.attempts).isEqualTo(29)

        mockMvc.perform(get("/acquisitions/${ids.source.value}").with(user("test-user")))
            .andExpect(content().string(containsString("71.8%")))
            .andExpect(content().string(containsString("Some Unique names are development placeholders.")))
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
        assertThat(
            fixtureActor.asLegacyUser { attempts.getAvailableAttempts(ids.account, ids.source) }?.count,
        ).isEqualTo(12)

        val zero = detailPage("0")
        assertThat(zero.previewAttempts).isZero()
        assertThat(zero.relationships.single().probabilityWithinAttempts).isEqualTo("0.0%")
        assertThat(detailPage().relationships.single().probabilityWithinAttempts).isEqualTo("71.8%")
    }

    @Test
    fun `missing saved attempts are not treated as zero`() {
        initializer.initialize()
        fixtureActor.asLegacyUser { attempts.clearAvailableAttempts(ids.account, ids.source) }

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
    fun `sources remain browsable without selected game environment or relationships`() {
        initializer.initialize()
        val orphan = AcquisitionSourceId.generate()
        sourceAccess.saveSource(AcquisitionSource(orphan))
        selection.deleteAllInBatch()

        val list = listPage()
        assertThat(list.accountSelected).isFalse()
        assertThat(list.environmentSelected).isFalse()
        assertThat(list.sources).hasSize(5)
        assertThat(list.sources.single { it.id == orphan.value }.relationshipCount).isZero()
        assertThat(list.sources).allMatch { it.availability == "Not evaluated" && !it.nameResolved }
        assertThat(list.sources.map { it.displayName }).allMatch { it.startsWith("Acquisition source ") }

        val known = detailPage()
        assertThat(known.accountSelected).isFalse()
        assertThat(known.relationships.single().actionable).isFalse()
        assertThat(known.relationships.single().relationshipStatus).contains("no game environment selected")

        val noRelationships = detailPage(id = orphan)
        assertThat(noRelationships.relationships).isEmpty()
        assertThat(noRelationships.displayName).isEqualTo("Acquisition source")
        assertThat(noRelationships.nameExplanation).contains("Name not evaluated")
    }

    @Test
    fun `no selected environment hides personal counts and previews and rejects explicit preview requests`() {
        initializer.initialize()
        val contextCountBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM poe_companion.account_context",
            Long::class.java,
        )
        selection.deleteAllInBatch()

        mockMvc.perform(get("/acquisitions").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Select a game environment")))

        val page = detailPage()
        assertThat(page.environmentSelected).isFalse()
        assertThat(page.attemptsEditable).isFalse()
        assertThat(page.savedAttempts).isNull()
        assertThat(page.relationships.single().actionable).isFalse()
        assertThat(page.relationships.single().probabilityStatus)
            .isEqualTo("Unknown — no game environment selected")

        mockMvc.perform(get("/acquisitions/${ids.source.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Select a game environment")))
            .andExpect(content().string(not(containsString("Recorded available attempts"))))
            .andExpect(content().string(not(containsString("Save available attempts"))))
            .andExpect(content().string(not(containsString("Remove saved count"))))
            .andExpect(content().string(not(containsString("Preview probability"))))

        mockMvc.perform(
            get("/acquisitions/${ids.source.value}").param("attempts", "12").with(user("test-user")),
        )
            .andExpect(status().isConflict)
            .andExpect(view().name("acquisitions/detail"))
            .andExpect(content().string(containsString("Select a game environment before previewing probability.")))
            .andExpect(content().string(not(containsString("Preview probability"))))

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM poe_companion.account_context", Long::class.java))
            .isEqualTo(contextCountBefore)
    }

    @Test
    fun `ambiguous source scope blocks actionable probability even if values agree`() {
        initializer.initialize()
        val scope = AcquisitionKnowledgeScope(
            CompatibilityVersion(3, 30),
            Ruleset.NORMAL,
            ids.league,
        )
        SecurityTestActors.asAdmin {
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
        }

        val page = detailPage()
        assertThat(page.availability).isEqualTo("Ambiguous")
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
        SecurityTestActors.asAdmin {
            encounters.recordSnapshot(
                source,
                AcquisitionKnowledgeScope(CompatibilityVersion(3, 30), Ruleset.NORMAL),
                BossEncounterKnowledgeSnapshot(
                    null,
                    "Alternative Boss",
                    AcquisitionSourceAvailability.AVAILABLE,
                    BossParticipation.ConfigurationDependent,
                ),
                null,
            )
        }
        SecurityTestActors.asAdmin {
            drops.recordSnapshot(
                relationship,
                AcquisitionKnowledgeScope(CompatibilityVersion(3, 30), Ruleset.NORMAL),
                DropRelationshipSnapshot(null, DropRelationshipState.CANNOT_DROP),
                null,
            )
        }

        val page = detailPage(id = source)
        assertThat(page.availability).isEqualTo("Available")
        assertThat(page.relationships.single().relationshipStatus).isEqualTo("Cannot drop")
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

    @Test
    fun `fallback source labels only count unnamed entries in a mixed source list`() {
        initializer.initialize()
        val alphabeticallyEarlySource = AcquisitionSourceId.generate()
        val unnamedSource = AcquisitionSourceId.generate()
        sourceAccess.saveSource(AcquisitionSource(alphabeticallyEarlySource))
        sourceAccess.saveSource(AcquisitionSource(unnamedSource))
        SecurityTestActors.asAdmin {
            encounters.recordSnapshot(
                alphabeticallyEarlySource,
                AcquisitionKnowledgeScope(CompatibilityVersion(3, 30), Ruleset.NORMAL),
                BossEncounterKnowledgeSnapshot(
                    null,
                    "Aardvark encounter",
                    AcquisitionSourceAvailability.AVAILABLE,
                    BossParticipation.ConfigurationDependent,
                ),
                null,
            )
        }

        val entries = listPage().sources
        assertThat(entries.first().displayName).isEqualTo("Aardvark encounter")
        assertThat(entries.single { it.id == unnamedSource.value }.displayName).isEqualTo("Acquisition source 1")
    }

    @Test
    fun `source list and detail explain why encounter names cannot be evaluated`() {
        initializer.initialize()
        selection.deleteAllInBatch()
        val listHtml = mockMvc.perform(get("/acquisitions").with(user("test-user")))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(listHtml)
            .contains(
                "No game environment selected",
                "contextual names",
                "Name not evaluated",
                "Acquisition source 1",
            ).doesNotContain("Acquisition source ID", "Source UUID")

        val detailHtml = mockMvc.perform(get("/acquisitions/${ids.source.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(detailHtml).contains("its contextual name", "Select a game environment")
            .doesNotContain("Acquisition source ID")
    }

    @Test
    fun `unavailable Boss R keeps recorded estimate but cannot show a farming outlook`() {
        initializer.initialize()
        val page = detailPage(id = ids.sourceR)
        assertThat(page.availability).isEqualTo("Unavailable")
        val unique = page.relationships.single()
        assertThat(unique.chancePerAttempt).isEqualTo("5%")
        assertThat(unique.actionable).isFalse()
        assertThat(unique.probabilityWithinAttempts).isNull()

        val html = mockMvc.perform(get("/acquisitions/${ids.sourceR.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(html).contains("Recorded fixed estimate:", "Not currently actionable.")
            .doesNotContain("Applicable fixed chance per completed encounter")
    }

    @Test
    fun `probability details remain available in accessible native disclosures`() {
        initializer.initialize()
        val html = mockMvc.perform(get("/acquisitions/${ids.source.value}").with(user("test-user")))
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        assertThat(html).contains(
            "<details",
            "<summary>All probability thresholds</summary>",
            "<summary>Estimate evidence and review details</summary>",
            "71.8%",
            "29",
            "rounded to one decimal place",
            "synthetic-development-fixture",
        )
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
