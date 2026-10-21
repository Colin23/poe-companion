package com.colinmoerbe.poecompanion.browser

import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.account.CurrentOwnershipService
import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
import com.colinmoerbe.poecompanion.browser.acquisitions.AcquisitionBrowserDetailPage
import com.colinmoerbe.poecompanion.browser.builds.BuildBrowserDetailPage
import com.colinmoerbe.poecompanion.browser.builds.BuildBrowserState
import com.colinmoerbe.poecompanion.browser.uniques.UniqueDetailPage
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Same canonical A/B/C knowledge, independent USER and ADMIN contexts, and real Browser/ownership composition.
 */
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
internal class TwoUserBrowserIsolationIntegrationTests(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val fixture: DevelopmentFixtureInitializer,
    @Autowired private val league: DevelopmentLeagueAccess,
    @Autowired private val selection: AccountEvaluationService,
    @Autowired private val ownership: ManualOwnershipService,
    @Autowired private val currentOwnership: CurrentOwnershipService,
    @Autowired private val attempts: AvailableAttemptsService,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val ids = DevelopmentFixtureIds
    private val adminContextId = AccountContextId.generate()

    @BeforeEach
    fun prepareContexts() {
        clearFixture()
        fixture.initialize()
        league.saveAccount(AccountContext(adminContextId, ids.league, Ruleset.NORMAL, ConfiguredApplicationUsers.ADMIN))
        asActor("test-admin", "ADMIN") {
            selection.select(adminContextId, CompatibilityVersion(3, 30))
            listOf(ids.uniqueA, ids.uniqueB, ids.uniqueC).forEach { ownership.setOwnedQuantity(adminContextId, it, 1) }
            attempts.setAvailableAttempts(adminContextId, ids.source, 0)
        }
    }

    @AfterEach
    fun cleanContexts() = clearFixture()

    @Test
    fun `the same shared Build and source show independent personal readiness and attempts`() {
        assertThat(buildPage("test-user", "USER").state).isEqualTo(BuildBrowserState.PARTIAL)
        assertThat(buildPage("test-admin", "ADMIN").state).isEqualTo(BuildBrowserState.UNIQUE_READY)
        assertThat(uniquePage("test-user", "USER").item.ownedQuantity).isZero()
        assertThat(uniquePage("test-admin", "ADMIN").item.ownedQuantity).isEqualTo(1)
        assertThat(acquisitionPage("test-user", "USER").savedAttempts).isEqualTo(12)
        assertThat(acquisitionPage("test-admin", "ADMIN").savedAttempts).isZero()

        asActor("test-user", "USER") { assertThat(selection.getSelected()?.accountContextId).isEqualTo(ids.account) }
        asActor("test-admin", "ADMIN") { assertThat(selection.getSelected()?.accountContextId).isEqualTo(adminContextId) }
    }

    @Test
    fun `neither role may read write clear or select another user's context`() {
        asActor("test-admin", "ADMIN") {
            assertForeignContextRejected(ids.account)
        }
        asActor("test-user", "USER") {
            assertForeignContextRejected(adminContextId)
        }

        asActor("test-user", "USER") {
            assertThat(currentOwnership.getCurrentOwnership(ids.account).quantityOf(ids.uniqueA)).isEqualTo(1)
            assertThat(attempts.getAvailableAttempts(ids.account, ids.source)?.count).isEqualTo(12)
        }
        asActor("test-admin", "ADMIN") {
            assertThat(currentOwnership.getCurrentOwnership(adminContextId).quantityOf(ids.uniqueA)).isEqualTo(1)
            assertThat(attempts.getAvailableAttempts(adminContextId, ids.source)?.count).isZero()
        }
    }

    @Test
    fun `admin ownership form only changes admin data and CSRF is required`() {
        val url = "/uniques/${ids.uniqueC.value}/ownership"
        mockMvc.perform(post(url).param("quantity", "9").with(user("test-admin").roles("ADMIN")))
            .andExpect(status().isForbidden)
        assertThat(uniquePage("test-admin", "ADMIN").item.ownedQuantity).isEqualTo(1)

        mockMvc.perform(post(url).param("quantity", "2").with(user("test-admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().is3xxRedirection)

        assertThat(uniquePage("test-admin", "ADMIN").item.ownedQuantity).isEqualTo(2)
        assertThat(uniquePage("test-user", "USER").item.ownedQuantity).isZero()
        assertThat(buildPage("test-user", "USER").state).isEqualTo(BuildBrowserState.PARTIAL)
    }

    private fun assertForeignContextRejected(id: AccountContextId) {
        assertThatThrownBy { selection.select(id, CompatibilityVersion(3, 30)) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessage("Account context unavailable")
        assertThatThrownBy { currentOwnership.getCurrentOwnership(id) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ownership.setOwnedQuantity(id, ids.uniqueC, 4) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { attempts.getAvailableAttempts(id, ids.source) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { attempts.setAvailableAttempts(id, ids.source, 8) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { attempts.clearAvailableAttempts(id, ids.source) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun buildPage(username: String, role: String): BuildBrowserDetailPage {
        val result = mockMvc.perform(
            get("/builds/${ids.variant.value}").with(user(username).roles(role)),
        ).andExpect(status().isOk).andReturn()
        return result.modelAndView!!.model["page"] as BuildBrowserDetailPage
    }

    private fun uniquePage(username: String, role: String): UniqueDetailPage {
        val result = mockMvc.perform(
            get("/uniques/${ids.uniqueC.value}").with(user(username).roles(role)),
        ).andExpect(status().isOk).andReturn()
        return result.modelAndView!!.model["page"] as UniqueDetailPage
    }

    private fun acquisitionPage(username: String, role: String): AcquisitionBrowserDetailPage {
        val result = mockMvc.perform(
            get("/acquisitions/${ids.source.value}").with(user(username).roles(role)),
        ).andExpect(status().isOk).andReturn()
        return result.modelAndView!!.model["page"] as AcquisitionBrowserDetailPage
    }

    private fun <T> asActor(username: String, role: String, action: () -> T): T {
        val previous = SecurityContextHolder.getContext()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken(
            username,
            null,
            listOf(SimpleGrantedAuthority("ROLE_$role")),
        )
        SecurityContextHolder.setContext(context)
        return try {
            action()
        } finally {
            SecurityContextHolder.setContext(previous)
        }
    }

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
