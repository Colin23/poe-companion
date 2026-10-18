package com.colinmoerbe.poecompanion.browser

import com.colinmoerbe.poecompanion.acquisition.AvailableAttemptsService
import com.colinmoerbe.poecompanion.browser.acquisitions.AcquisitionBrowserDetailPage
import com.colinmoerbe.poecompanion.browser.builds.BuildBrowserDetailPage
import com.colinmoerbe.poecompanion.browser.builds.BuildBrowserState
import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
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

/** Proves the canonical A/B/C journey across authenticated browser routes and real PostgreSQL persistence. */
@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(
    properties = [
        "poe-companion.dev-fixture.enabled=true",
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class VerticalSlice1BrowserJourneyIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val initializer: DevelopmentFixtureInitializer,
    @Autowired private val attempts: AvailableAttemptsService,
    @Autowired dataSource: DataSource,
) {
    private val jdbc = JdbcTemplate(dataSource)
    private val ids = DevelopmentFixtureIds

    @BeforeEach
    fun beforeEach() = clearDatabase()

    @AfterEach
    fun afterEach() = clearDatabase()

    @Test
    fun `Build X to Boss Y to owning C makes Build X unique ready`() {
        initializer.initialize()
        val buildUrl = "/builds/${ids.variant.value}"
        val uniqueUrl = "/uniques/${ids.uniqueC.value}"
        val sourceUrl = "/acquisitions/${ids.source.value}"

        val initialBuild = buildDetail(buildUrl)
        assertThat(initialBuild.state).isEqualTo(BuildBrowserState.PARTIAL)
        assertThat(initialBuild.groups.map { it.importance }).containsExactly("ENABLING", "CORE")
        assertThat(initialBuild.groups.map { it.satisfied }).containsExactly(true, false)
        assertThat(initialBuild.groups[0].requirements.single().ownedQuantity).isEqualTo(1)
        assertThat(initialBuild.groups[1].requirements.map { it.ownedQuantity }).containsExactly(1, 0)

        val uniqueHtml = mockMvc.perform(get(uniqueUrl).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("uniques/detail"))
            .andExpect(content().string(containsString("Missing · 0 copies")))
            .andExpect(content().string(containsString("Save ownership")))
            .andReturn().response.contentAsString
        assertThat(uniqueHtml).contains("Boss Y", "$sourceUrl#unique-${ids.uniqueC.value}")

        val boss = acquisitionDetail(sourceUrl)
        assertThat(boss.displayName).isEqualTo("Boss Y")
        assertThat(boss.availability).isEqualTo("AVAILABLE")
        assertThat(boss.savedAttempts).isEqualTo(12)
        val drop = boss.relationships.single()
        assertThat(drop.uniqueId).isEqualTo(ids.uniqueC.value)
        assertThat(drop.relationshipStatus).isEqualTo("CAN_DROP")
        assertThat(drop.actionable).isTrue()
        assertThat(drop.chancePerAttempt).isEqualTo("10.000%")
        assertThat(drop.probabilityWithinAttempts).isEqualTo("71.8%")
        assertThat(drop.thresholds.single { it.percentage == 95 }.attempts).isEqualTo(29)

        mockMvc.perform(post("$uniqueUrl/ownership").param("quantity", "1").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, uniqueUrl))
        mockMvc.perform(get(uniqueUrl).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Owned · <span>1</span> copies")))

        val updatedBuild = buildDetail(buildUrl)
        assertThat(updatedBuild.state).isEqualTo(BuildBrowserState.UNIQUE_READY)
        assertThat(updatedBuild.groups.map { it.satisfied }).containsExactly(true, true)
        assertThat(updatedBuild.groups[1].requirements.map { it.ownedQuantity }).containsExactly(1, 1)

        val bossAfterOwnership = acquisitionDetail(sourceUrl)
        assertThat(bossAfterOwnership.savedAttempts).isEqualTo(12)
        val dropAfterOwnership = bossAfterOwnership.relationships.single()
        assertThat(dropAfterOwnership.chancePerAttempt).isEqualTo("10.000%")
        assertThat(dropAfterOwnership.probabilityWithinAttempts).isEqualTo("71.8%")
        assertThat(attempts.getAvailableAttempts(ids.account, ids.source)?.count).isEqualTo(12)
    }

    private fun buildDetail(url: String): BuildBrowserDetailPage {
        val response = mockMvc.perform(get(url).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("builds/detail"))
            .andExpect(content().string(containsString("/uniques/${ids.uniqueC.value}")))
            .andReturn()
        return response.modelAndView!!.model["page"] as BuildBrowserDetailPage
    }

    private fun acquisitionDetail(url: String): AcquisitionBrowserDetailPage {
        val response = mockMvc.perform(get(url).with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(view().name("acquisitions/detail"))
            .andExpect(content().string(containsString("71.8%")))
            .andExpect(content().string(containsString("/uniques/${ids.uniqueC.value}")))
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
