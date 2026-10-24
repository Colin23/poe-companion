package com.colinmoerbe.poecompanion.browser.acquisitions

import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
import com.colinmoerbe.poecompanion.SecurityTestActors
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GameEnvironmentChoice
import com.colinmoerbe.poecompanion.league.GameEnvironmentOptionService
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
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
import java.util.UUID

/** Browser save/remove behavior against C2C context creation and real PostgreSQL persistence. */
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
internal class AcquisitionSavedAttemptsBrowserIntegrationTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val fixture: DevelopmentFixtureInitializer,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val attempts: AvailableAttemptsService,
    @Autowired private val accounts: AccountEvaluationService,
    @Autowired private val selections: GameEnvironmentSelectionService,
    @Autowired private val options: GameEnvironmentOptionService,
    @Autowired private val leagues: DevelopmentLeagueAccess,
) {
    private val ids = DevelopmentFixtureIds
    private val sourceUri get() = "/acquisitions/${ids.source.value}"

    @BeforeEach
    fun setup() {
        clear()
        fixture.initialize()
    }

    @AfterEach
    fun cleanup() = clear()

    @Test
    fun `saving personal attempts is distinct from previewing hypothetical attempts`() {
        mvc.perform(
            post("$sourceUri/attempts").with(user("test-user")).with(csrf()).param("count", "5"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, sourceUri))

        val saved = page()
        assertThat(saved.savedAttempts).isEqualTo(5)
        assertThat(saved.effectiveAttempts).isEqualTo(5)
        assertThat(saved.relationships.single().probabilityWithinAttempts).isEqualTo("41.0%")

        val preview = mvc.perform(get(sourceUri).param("attempts", "30").with(user("test-user")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Previewing")))
            .andReturn().modelAndView!!.model["page"] as AcquisitionBrowserDetailPage
        assertThat(preview.previewAttempts).isEqualTo(30)
        assertThat(preview.savedAttempts).isEqualTo(5)
        assertThat(preview.relationships.single().probabilityWithinAttempts).isEqualTo("95.8%")
        assertThat(page().savedAttempts).isEqualTo(5)
    }

    @Test
    fun `saved zero remains recorded until Remove saved count deletes the fact`() {
        mvc.perform(post("$sourceUri/attempts").with(user("test-user")).with(csrf()).param("count", "0"))
            .andExpect(status().is3xxRedirection)

        val recorded = page()
        assertThat(recorded.savedAttempts).isZero()
        assertThat(recorded.effectiveAttempts).isZero()
        val html = mvc.perform(get(sourceUri).with(user("test-user")))
            .andExpect(content().string(containsString("Remove saved count")))
            .andExpect(content().string(containsString("0 means none available")))
            .andReturn().response.contentAsString
        assertThat(html).contains("Save available attempts")

        mvc.perform(post("$sourceUri/attempts/remove").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(header().string(HttpHeaders.LOCATION, sourceUri))
        assertThat(page().savedAttempts).isNull()
        mvc.perform(get(sourceUri).with(user("test-user")))
            .andExpect(content().string(containsString("Not recorded")))
            .andExpect(content().string(not(containsString("Remove saved count"))))
    }

    @Test
    fun `first saved zero in Standard creates one personal context but preview and remove do not`() {
        selectStandard()
        val before = contextCount()
        assertThat(page().accountSelected).isFalse()
        assertThat(page().attemptsEditable).isTrue()
        mvc.perform(get(sourceUri).param("attempts", "12").with(user("test-user")))
            .andExpect(status().isOk)
        mvc.perform(post("$sourceUri/attempts/remove").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertThat(contextCount()).isEqualTo(before)

        mvc.perform(post("$sourceUri/attempts").with(user("test-user")).with(csrf()).param("count", "0"))
            .andExpect(status().is3xxRedirection)
        val id = SecurityTestActors.asUser { accounts.getSelected()!!.accountContextId }
        assertThat(contextCount()).isEqualTo(before + 1)
        assertThat(SecurityTestActors.asUser { attempts.getAvailableAttempts(id, ids.source)?.count }).isZero()

        mvc.perform(post("$sourceUri/attempts/remove").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertThat(SecurityTestActors.asUser { attempts.getAvailableAttempts(id, ids.source) }).isNull()
        assertThat(contextCount()).isEqualTo(before + 1)
    }

    @Test
    fun `Standard compatibility versions reuse one saved personal count`() {
        selectStandard()
        mvc.perform(post("$sourceUri/attempts").with(user("test-user")).with(csrf()).param("count", "7"))
            .andExpect(status().is3xxRedirection)
        val id = SecurityTestActors.asUser { accounts.getSelected()!!.accountContextId }
        val before = contextCount()

        leagues.recognizeVersion(ids.standardLeague, CompatibilityVersion(3, 29))
        selectStandard(CompatibilityVersion(3, 29))
        assertThat(SecurityTestActors.asUser { accounts.getSelected()!!.accountContextId }).isEqualTo(id)
        assertThat(page().savedAttempts).isEqualTo(7)
        assertThat(contextCount()).isEqualTo(before)

        selectStandard()
        assertThat(page().savedAttempts).isEqualTo(7)
    }

    @Test
    fun `USER and ADMIN saved attempts stay separate for the same source and league`() {
        selectStandard()
        SecurityTestActors.asAdmin {
            options.select(
                GameEnvironmentChoice(
                    CompatibilityVersion(3, 30),
                    LeagueType.STANDARD,
                    LeagueMortality.SOFTCORE,
                    LeagueParticipation.SSF,
                ),
            )
        }
        mvc.perform(post("$sourceUri/attempts").with(user("test-user")).with(csrf()).param("count", "7"))
            .andExpect(status().is3xxRedirection)
        mvc.perform(
            post("$sourceUri/attempts").with(user("test-admin").roles("ADMIN")).with(csrf()).param("count", "2"),
        ).andExpect(status().is3xxRedirection)

        assertThat(page().savedAttempts).isEqualTo(7)
        assertThat(page("test-admin", "ADMIN").savedAttempts).isEqualTo(2)
        val userContext = SecurityTestActors.asUser { accounts.getSelected()!!.accountContextId }
        val adminContext = SecurityTestActors.asAdmin { accounts.getSelected()!!.accountContextId }
        assertThat(userContext).isNotEqualTo(adminContext)

        mvc.perform(post("$sourceUri/attempts/remove").with(user("test-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertThat(page("test-admin", "ADMIN").savedAttempts).isNull()
        assertThat(page().savedAttempts).isEqualTo(7)
    }

    @Test
    fun `unknown sources and invalid input do not create a personal context`() {
        selectStandard()
        val before = contextCount()
        listOf("", "-1", "2.5", "nope", "2147483648").forEach { invalid ->
            mvc.perform(
                post("$sourceUri/attempts").with(user("test-user")).with(csrf()).param("count", invalid),
            ).andExpect(status().isBadRequest)
        }
        mvc.perform(post("$sourceUri/attempts").with(user("test-user")).with(csrf()))
            .andExpect(status().isBadRequest)
        val unknown = "/acquisitions/${UUID.randomUUID()}"
        mvc.perform(post("$unknown/attempts").with(user("test-user")).with(csrf()).param("count", "1"))
            .andExpect(status().isNotFound)
        mvc.perform(post("$unknown/attempts/remove").with(user("test-user")).with(csrf()))
            .andExpect(status().isNotFound)
        assertThat(contextCount()).isEqualTo(before)
        assertThat(SecurityTestActors.asUser { accounts.getSelected() }).isNull()
    }

    @Test
    fun `missing selection and CSRF reject personal writes`() {
        val before = contextCount()
        mvc.perform(post("$sourceUri/attempts").with(user("test-user")).param("count", "1"))
            .andExpect(status().isForbidden)
        mvc.perform(post("$sourceUri/attempts/remove").with(user("test-user")))
            .andExpect(status().isForbidden)
        SecurityTestActors.asUser { selections.getSelected() }
        jdbc.update(
            "DELETE FROM poe_companion.account_evaluation_selection WHERE application_user_id = ?",
            com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers.USER.value,
        )
        mvc.perform(post("$sourceUri/attempts").with(user("test-user")).with(csrf()).param("count", "1"))
            .andExpect(status().isConflict)
        mvc.perform(post("$sourceUri/attempts/remove").with(user("test-user")).with(csrf()))
            .andExpect(status().isConflict)
        mvc.perform(get(sourceUri).with(user("test-user")))
            .andExpect(content().string(not(containsString("Save available attempts"))))
        assertThat(contextCount()).isEqualTo(before)
    }

    @Test
    fun `historical selections cannot create contexts but may edit already owned contexts`() {
        selectStandard()
        val before = contextCount()
        SecurityTestActors.asUser {
            selections.select(
                EvaluationContext(
                    CompatibilityVersion(3, 28),
                    Ruleset.NORMAL,
                    leagueDefinitionId = ids.standardLeague,
                ),
            )
        }
        assertThat(page().attemptsEditable).isFalse()
        mvc.perform(post("$sourceUri/attempts").with(user("test-user")).with(csrf()).param("count", "4"))
            .andExpect(status().isConflict)
        assertThat(contextCount()).isEqualTo(before)

        SecurityTestActors.asUser {
            selections.select(
                EvaluationContext(
                    CompatibilityVersion(3, 28),
                    Ruleset.NORMAL,
                    leagueDefinitionId = ids.league,
                ),
            )
        }
        assertThat(page().attemptsEditable).isTrue()
        mvc.perform(post("$sourceUri/attempts").with(user("test-user")).with(csrf()).param("count", "4"))
            .andExpect(status().is3xxRedirection)
        assertThat(page().savedAttempts).isEqualTo(4)
        assertThat(contextCount()).isEqualTo(before)
    }

    private fun page(username: String = "test-user", role: String = "USER"): AcquisitionBrowserDetailPage {
        val response = mvc.perform(get(sourceUri).with(user(username).roles(role)))
            .andExpect(status().isOk)
            .andExpect(view().name("acquisitions/detail"))
            .andReturn()
        return response.modelAndView!!.model["page"] as AcquisitionBrowserDetailPage
    }

    private fun selectStandard(version: CompatibilityVersion = CompatibilityVersion(3, 30)) {
        SecurityTestActors.asUser {
            options.select(
                GameEnvironmentChoice(
                    version,
                    LeagueType.STANDARD,
                    LeagueMortality.SOFTCORE,
                    LeagueParticipation.SSF,
                ),
            )
        }
    }

    private fun contextCount(): Long =
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
