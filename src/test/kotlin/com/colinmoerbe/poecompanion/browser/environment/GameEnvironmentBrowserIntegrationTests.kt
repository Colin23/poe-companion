package com.colinmoerbe.poecompanion.browser.environment

import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.browser.builds.BuildBrowserDetailPage
import com.colinmoerbe.poecompanion.browser.builds.BuildBrowserState
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

/** Real MVC/DB verification of the selected environment and the optional personal-data join. */
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
internal class GameEnvironmentBrowserIntegrationTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val fixture: DevelopmentFixtureInitializer,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val ids = DevelopmentFixtureIds

    @BeforeEach
    fun setup() {
        clear()
        fixture.initialize()
    }

    @AfterEach
    fun cleanup() = clear()

    @Test
    fun `selector offers only curated versions and known league formats`() {
        mvc.perform(get("/environment").with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andExpect(view().name("environment/index"))
            .andExpect(content().string(containsString("Challenge")))
            .andExpect(content().string(containsString("Standard")))
            .andExpect(content().string(containsString("3.30")))
            .andExpect(content().string(containsString("Development")))
        mvc.perform(get("/environment"))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `environment without personal context shows requirements and real probability knowledge`() {
        val initialCount = countContexts()
        select("STANDARD")

        val build = mvc.perform(get("/builds/${ids.variant.value}").with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andReturn().modelAndView!!.model["page"] as BuildBrowserDetailPage
        assertThat(build.state).isEqualTo(BuildBrowserState.NO_PERSONAL)
        assertThat(build.groups).isNotEmpty
        assertThat(build.groups.flatMap { it.requirements }).allMatch { it.ownedQuantity == null }
        assertThat(build.groups.flatMap { it.requirements }).allMatch { it.satisfied == null }

        mvc.perform(get("/acquisitions/${ids.source.value}").param("attempts", "12")
            .with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("71.8%")))
            .andExpect(content().string(containsString("Personal attempts are unavailable")))

        mvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user").roles("USER")))
            .andExpect(content().string(containsString("Unknown — no personal collection")))
        assertThat(countContexts()).isEqualTo(initialCount)
    }

    @Test
    fun `switching back restores original ownership and selection is user scoped`() {
        select("STANDARD")
        mvc.perform(get("/uniques/${ids.uniqueA.value}").with(user("test-admin").roles("ADMIN")))
            .andExpect(content().string(containsString("Owned")))
        select("CHALLENGE")
        mvc.perform(get("/builds/${ids.variant.value}").with(user("test-user").roles("USER")))
            .andExpect(content().string(containsString("Partial")))
        mvc.perform(get("/uniques/${ids.uniqueA.value}").with(user("test-user").roles("USER")))
            .andExpect(content().string(containsString("Owned ·")))
    }

    @Test
    fun `invalid and csrf-free requests cannot alter an existing selection`() {
        mvc.perform(post("/environment").with(user("test-user").roles("USER"))
            .param("version", "3.30").param("type", "STANDARD")
            .param("mortality", "SOFTCORE").param("participation", "SSF").param("ruleset", "NORMAL"))
            .andExpect(status().isForbidden)

        mvc.perform(post("/environment").with(user("test-user").roles("USER")).with(csrf())
            .param("version", "3.29").param("type", "STANDARD")
            .param("mortality", "SOFTCORE").param("participation", "SSF").param("ruleset", "NORMAL"))
            .andExpect(status().isBadRequest)

        mvc.perform(post("/environment").with(user("test-user").roles("USER")).with(csrf())
            .param("version", "3.30").param("type", "STANDARD")
            .param("mortality", "SOFTCORE").param("participation", "SSF").param("ruleset", "RUTHLESS"))
            .andExpect(status().isBadRequest)

        mvc.perform(get("/environment").with(user("test-user").roles("USER")))
            .andExpect(content().string(containsString("Current selection:")))
            .andExpect(content().string(containsString("Development SSF league")))
    }

    private fun select(type: String) {
        mvc.perform(post("/environment").with(user("test-user").roles("USER")).with(csrf())
            .param("version", "3.30")
            .param("type", type)
            .param("mortality", "SOFTCORE")
            .param("participation", "SSF")
            .param("ruleset", "NORMAL"))
            .andExpect(status().is3xxRedirection)
    }

    private fun countContexts(): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM poe_companion.account_context", Long::class.java)!!

    private fun clear() {
        jdbc.execute(
            """
            TRUNCATE TABLE poe_companion.unique_definition, poe_companion.league_definition,
                poe_companion.build_archetype, poe_companion.acquisition_source CASCADE
            """.trimIndent(),
        )
    }
}
