package com.colinmoerbe.poecompanion.browser.environment

import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
import com.colinmoerbe.poecompanion.SecurityTestActors
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
import com.colinmoerbe.poecompanion.browser.builds.BuildBrowserDetailPage
import com.colinmoerbe.poecompanion.browser.builds.BuildBrowserState
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess
import com.colinmoerbe.poecompanion.league.GameEnvironmentChoice
import com.colinmoerbe.poecompanion.league.GameEnvironmentOptionService
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import java.util.UUID

/** Real MVC/DB verification of the selected environment and the optional personal-data join. */
@Import(TestcontainersConfiguration::class)
@AutoConfigureMockMvc
@WithMockUser(username = "test-user", roles = ["USER"])
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
    @Autowired private val accountEvaluation: AccountEvaluationService,
    @Autowired private val selections: GameEnvironmentSelectionService,
    @Autowired private val options: GameEnvironmentOptionService,
    @Autowired private val leagues: DevelopmentLeagueAccess,
    @Autowired private val attempts: AvailableAttemptsService,
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
            .andExpect(content().string(containsString("id=\"environment-version\"")))
            .andExpect(content().string(containsString("id=\"environment-mortality\"")))
            .andExpect(content().string(containsString("id=\"environment-participation\"")))
            .andExpect(content().string(containsString("game-environment-selector.js")))
            .andExpect(content().string(containsString("data-environment-option")))
        mvc.perform(get("/environment").with(anonymous()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    fun `selector offers one guided form backed by actual curated choices`() {
        val result = mvc.perform(get("/environment").with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andReturn()
        val html = result.response.contentAsString
        assertThat(html).contains(
            "Current environment",
            "Choose an environment",
            "data-environment-option",
            "data-league-name=\"Development SSF league\"",
            "data-league-name=\"Development Standard SSF\"",
            "id=\"environment-preview-name\"",
        ).doesNotContain("Recognized choices", "onchange=", "onclick=")
        assertThat(html.split("data-environment-option").size - 1).isEqualTo(2)
        assertThat(result.modelAndView!!.model["draftPreviewReady"]).isEqualTo(true)
        assertThat(result.modelAndView!!.model["draftPreviewName"]).isEqualTo("Development SSF league")
    }

    @Test
    fun `invalid submitted settings retain the draft without replacing the saved environment`() {
        val before = SecurityTestActors.asUser { selections.getSelected() }
        val result = mvc.perform(
            post("/environment").with(user("test-user").roles("USER")).with(csrf())
                .param("version", "3.29")
                .param("type", "STANDARD")
                .param("mortality", "SOFTCORE")
                .param("participation", "SSF")
                .param("ruleset", "NORMAL"),
        )
            .andExpect(status().isBadRequest)
            .andReturn()
        val model = result.modelAndView!!.model
        assertThat(model["selectedVersion"]).isEqualTo("3.29")
        assertThat(model["draftPreviewReady"]).isEqualTo(false)
        assertThat(result.response.contentAsString).contains(
            "Current environment",
            "Development SSF league",
            "value=\"3.29\"",
            "not among the curated game environments",
        )
        assertThat(SecurityTestActors.asUser { selections.getSelected() }).isEqualTo(before)
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

        mvc.perform(
            get("/acquisitions/${ids.source.value}").param("attempts", "12")
                .with(user("test-user").roles("USER")),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("71.8%")))
            .andExpect(content().string(containsString("Not recorded")))
            .andExpect(content().string(containsString("Save available attempts")))

        mvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user").roles("USER")))
            .andExpect(content().string(containsString("Ownership unknown")))
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
            .andExpect(content().string(containsString("1 copy owned")))
    }

    @Test
    fun `invalid and csrf-free requests cannot alter an existing selection`() {
        mvc.perform(
            post("/environment").with(user("test-user").roles("USER"))
                .param("version", "3.30").param("type", "STANDARD")
                .param("mortality", "SOFTCORE").param("participation", "SSF").param("ruleset", "NORMAL"),
        )
            .andExpect(status().isForbidden)

        mvc.perform(
            post("/environment").with(user("test-user").roles("USER")).with(csrf())
                .param("version", "3.29").param("type", "STANDARD")
                .param("mortality", "SOFTCORE").param("participation", "SSF").param("ruleset", "NORMAL"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(view().name("environment/index"))
            .andExpect(content().string(containsString("not among the curated game environments")))
            .andExpect(content().string(containsString("League type")))
            .andExpect(content().string(containsString("Softcore")))
            .andExpect(content().string(containsString("SSF")))

        mvc.perform(
            post("/environment").with(user("test-user").roles("USER")).with(csrf())
                .param("version", "3.30").param("type", "STANDARD")
                .param("mortality", "SOFTCORE").param("participation", "SSF").param("ruleset", "RUTHLESS"),
        )
            .andExpect(status().isBadRequest)

        mvc.perform(get("/environment").with(user("test-user").roles("USER")))
            .andExpect(content().string(containsString("Current environment")))
            .andExpect(content().string(containsString("Development SSF league")))
    }

    @Test
    fun `precise saved patch remains recognized and survives selecting the same environment`() {
        val version = CompatibilityVersion(3, 30)
        val patch = GamePatch(version, 2, "b")
        accountEvaluation.select(ids.account, version, patch)

        mvc.perform(get("/environment").with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("3.30.2b")))
            .andExpect(content().string(containsString("Game environment")))
        val page = mvc.perform(get("/environment").with(user("test-user").roles("USER")))
            .andReturn().modelAndView!!.model
        assertThat(page["currentRecognized"]).isEqualTo(true)
        assertThat(page["selectedVersion"]).isEqualTo("3.30")

        select("CHALLENGE")
        assertThat(SecurityTestActors.asUser { selections.getSelected()?.gamePatch }).isEqualTo(patch)

        // Historical persisted selections are not rewritten, but unsupported rulesets are not selectable.
        jdbc.update(
            """
            UPDATE poe_companion.account_evaluation_selection SET ruleset = 'RUTHLESS'
            WHERE application_user_id = ?
            """.trimIndent(),
            ConfiguredApplicationUsers.USER.value,
        )
        val historical = mvc.perform(get("/environment").with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andReturn().modelAndView!!.model
        assertThat(historical["currentRecognized"]).isEqualTo(false)
    }

    @Test
    fun `a malformed numeric version returns a form validation error instead of an exception`() {
        mvc.perform(
            post("/environment").with(user("test-user").roles("USER")).with(csrf())
                .param("version", "999999999999999999999.30")
                .param("type", "CHALLENGE")
                .param("mortality", "SOFTCORE")
                .param("participation", "SSF")
                .param("ruleset", "NORMAL"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(view().name("environment/index"))
            .andExpect(content().string(containsString("Enter a valid game environment")))
        assertThat(
            SecurityTestActors.asUser { selections.getSelected()?.leagueDefinitionId },
        ).isEqualTo(ids.league)
    }

    @Test
    fun `unregistered selection survives reopening and reinitializing fixtures`() {
        val unknownVersion = CompatibilityVersion(3, 28)
        accountEvaluation.select(ids.account, unknownVersion)

        mvc.perform(get("/environment").with(user("test-user").roles("USER")))
            .andExpect(content().string(containsString("no longer among currently curated choices")))
        fixture.initialize()
        assertThat(
            SecurityTestActors.asUser {
                selections.getSelected()?.compatibilityVersion
            },
        ).isEqualTo(unknownVersion)
        assertThat(optionCount()).isEqualTo(2L)
    }

    @Test
    fun `Standard across curated versions keeps the same admin personal context`() {
        leagues.recognizeVersion(ids.standardLeague, CompatibilityVersion(3, 29))
        val originalCount = countContexts()
        SecurityTestActors.asAdmin {
            val standard29 = GameEnvironmentChoice(
                CompatibilityVersion(3, 29),
                LeagueType.STANDARD,
                LeagueMortality.SOFTCORE,
                LeagueParticipation.SSF,
            )
            options.select(standard29)
            assertThat(accountEvaluation.getSelected()?.accountContextId).isEqualTo(ids.adminStandardAccount)
            options.select(standard29.copy(compatibilityVersion = CompatibilityVersion(3, 30)))
            assertThat(accountEvaluation.getSelected()?.accountContextId).isEqualTo(ids.adminStandardAccount)
        }
        assertThat(SecurityTestActors.asUser { selections.getSelected()?.leagueDefinitionId }).isEqualTo(ids.league)
        assertThat(countContexts()).isEqualTo(originalCount)
        fixture.initialize()
        assertThat(optionCount()).isEqualTo(3L)
    }

    @Test
    fun `ambiguous challenge combinations need an explicit curated league identity`() {
        val second = LeagueDefinition(
            LeagueDefinitionId(UUID.randomUUID()),
            "Another curated challenge",
            LeagueType.CHALLENGE,
            LeagueParticipation.SSF,
            LeagueMortality.SOFTCORE,
            GameRealm.PC,
        )
        SecurityTestActors.asAdmin { leagues.saveLeague(second) }
        leagues.recognizeVersion(second.id, CompatibilityVersion(3, 30))
        val initial = SecurityTestActors.asUser { selections.getSelected() }

        mvc.perform(
            post("/environment").with(user("test-user").roles("USER")).with(csrf())
                .param("version", "3.30")
                .param("type", "CHALLENGE")
                .param("mortality", "SOFTCORE")
                .param("participation", "SSF")
                .param("ruleset", "NORMAL"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(view().name("environment/index"))
            .andExpect(content().string(containsString("Specific league")))
            .andExpect(content().string(containsString("Another curated challenge")))
        assertThat(SecurityTestActors.asUser { selections.getSelected() }).isEqualTo(initial)

        mvc.perform(
            post("/environment").with(user("test-user").roles("USER")).with(csrf())
                .param("version", "3.30")
                .param("type", "CHALLENGE")
                .param("mortality", "SOFTCORE")
                .param("participation", "SSF")
                .param("ruleset", "NORMAL")
                .param("leagueId", second.id.value.toString()),
        )
            .andExpect(status().is3xxRedirection)
        assertThat(SecurityTestActors.asUser { selections.getSelected()?.leagueDefinitionId }).isEqualTo(second.id)
        assertThat(countContexts()).isEqualTo(3L)
    }

    @Test
    fun `preview zero is distinct from missing personal attempts and recorded zero`() {
        val originalCount = countContexts()
        select("STANDARD")

        mvc.perform(get("/acquisitions/${ids.source.value}").with(user("test-user").roles("USER")))
            .andExpect(content().string(containsString("Not recorded")))
        mvc.perform(
            get("/acquisitions/${ids.source.value}").param("attempts", "0")
                .with(user("test-user").roles("USER")),
        )
            .andExpect(content().string(containsString("0.0%")))
        assertThat(countContexts()).isEqualTo(originalCount)

        SecurityTestActors.asAdmin {
            options.select(
                GameEnvironmentChoice(
                    CompatibilityVersion(3, 30),
                    LeagueType.STANDARD,
                    LeagueMortality.SOFTCORE,
                    LeagueParticipation.SSF,
                ),
            )
            attempts.setAvailableAttempts(ids.adminStandardAccount, ids.source, 0)
        }
        mvc.perform(get("/acquisitions/${ids.source.value}").with(user("test-admin").roles("ADMIN")))
            .andExpect(content().string(containsString("0.0%")))
    }

    @Test
    fun `zero-required revision remains canonical without personal ownership`() {
        select("STANDARD")
        val page = mvc.perform(get("/builds/${ids.variantR.value}").with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("No required Uniques")))
            .andReturn().modelAndView!!.model["page"] as BuildBrowserDetailPage
        assertThat(page.state).isEqualTo(BuildBrowserState.NO_PERSONAL)
        assertThat(page.hasRequiredUniqueGroups).isFalse()
    }

    @Test
    fun `version registry rejects unknown foreign keys and restarts do not duplicate entries`() {
        assertThat(optionCount()).isEqualTo(2L)
        assertThatThrownBy {
            jdbc.update(
                """
                INSERT INTO poe_companion.selectable_league_version
                    (league_definition_id, compatibility_version_major, compatibility_version_minor)
                VALUES (?, 3, 30)
                """.trimIndent(),
                UUID.randomUUID(),
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
        fixture.initialize()
        assertThat(optionCount()).isEqualTo(2L)
        mvc.perform(get("/builds").with(user("test-user").roles("USER")))
            .andExpect(content().string(containsString("3.30 SC SSF")))
    }

    private fun select(type: String) {
        mvc.perform(
            post("/environment").with(user("test-user").roles("USER")).with(csrf())
                .param("version", "3.30")
                .param("type", type)
                .param("mortality", "SOFTCORE")
                .param("participation", "SSF")
                .param("ruleset", "NORMAL"),
        )
            .andExpect(status().is3xxRedirection)
    }

    private fun optionCount(): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM poe_companion.selectable_league_version", Long::class.java)!!

    private fun countContexts(): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM poe_companion.account_context", Long::class.java)!!

    private fun clear() {
        jdbc.execute(
            """
            TRUNCATE TABLE poe_companion.boss_definition, poe_companion.unique_definition,
                poe_companion.league_definition, poe_companion.build_archetype,
                poe_companion.acquisition_source CASCADE
            """.trimIndent(),
        )
    }
}
