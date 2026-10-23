package com.colinmoerbe.poecompanion.browser.environment

import com.colinmoerbe.poecompanion.DevelopmentFixtureIds
import com.colinmoerbe.poecompanion.DevelopmentFixtureInitializer
import com.colinmoerbe.poecompanion.SecurityTestActors
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.account.CurrentOwnershipService
import com.colinmoerbe.poecompanion.account.ManualOwnershipService
import com.colinmoerbe.poecompanion.acquisition.attempts.AvailableAttemptsService
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.AccountEvaluationService
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.DevelopmentLeagueAccess
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GameEnvironmentChoice
import com.colinmoerbe.poecompanion.league.GameEnvironmentOptionService
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** C2C real-PostgreSQL acceptance tests for first personal writes and account-context reuse. */
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
internal class FirstPersonalWriteIntegrationTests(
    @Autowired private val mvc: MockMvc,
    @Autowired private val fixture: DevelopmentFixtureInitializer,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val options: GameEnvironmentOptionService,
    @Autowired private val selections: GameEnvironmentSelectionService,
    @Autowired private val accounts: AccountEvaluationService,
    @Autowired private val ownership: ManualOwnershipService,
    @Autowired private val currentOwnership: CurrentOwnershipService,
    @Autowired private val attempts: AvailableAttemptsService,
    @Autowired private val leagues: DevelopmentLeagueAccess,
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
    fun `first positive ownership save initializes personal data through the Unique Browser`() {
        selectStandard()
        val before = countContexts()
        val uri = "/uniques/${ids.uniqueC.value}"

        mvc.perform(get(uri).with(user("test-user").roles("USER")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Save ownership")))
            .andExpect(content().string(containsString("Unknown — no personal collection")))

        mvc.perform(post("$uri/ownership").param("quantity", "1").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)

        val id = SecurityTestActors.asUser { accounts.getSelected()!!.accountContextId }
        assertThat(countContexts()).isEqualTo(before + 1)
        assertThat(SecurityTestActors.asUser { currentOwnership.getCurrentOwnership(id).quantityOf(ids.uniqueC) })
            .isEqualTo(1)
        mvc.perform(get(uri).with(user("test-user")))
            .andExpect(content().string(containsString("Owned ·")))

        // Editing zero afterwards preserves the established context and sparse ownership.
        mvc.perform(post("$uri/ownership").param("quantity", "0").with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        assertThat(countContexts()).isEqualTo(before + 1)
        assertThat(SecurityTestActors.asUser { currentOwnership.getCurrentOwnership(id).quantityOf(ids.uniqueC) })
            .isZero()
    }

    @Test
    fun `no-op zero preview and invalid Unique do not initialize a personal context`() {
        selectStandard()
        val before = countContexts()
        mvc.perform(post("/uniques/${ids.uniqueC.value}/ownership").param("quantity", "0")
            .with(user("test-user")).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mvc.perform(get("/acquisitions/${ids.source.value}").param("attempts", "12")
            .with(user("test-user")))
            .andExpect(status().isOk)
        mvc.perform(post("/uniques/${ids.uniqueC.value}/ownership").param("quantity", "-1")
            .with(user("test-user")).with(csrf()))
            .andExpect(status().isBadRequest)
        mvc.perform(post("/uniques/${UUID.randomUUID()}/ownership").param("quantity", "1")
            .with(user("test-user")).with(csrf()))
            .andExpect(status().isNotFound)
        assertThat(countContexts()).isEqualTo(before)
        assertThat(SecurityTestActors.asUser { accounts.getSelected() }).isNull()
    }

    @Test
    fun `saving zero attempts creates a fact but clearing absent attempts does not`() {
        selectStandard()
        val before = countContexts()
        SecurityTestActors.asUser { attempts.clearAvailableAttemptsForSelectedEnvironment(ids.source) }
        assertThat(countContexts()).isEqualTo(before)
        SecurityTestActors.asUser { attempts.setAvailableAttemptsForSelectedEnvironment(ids.source, 0) }
        val id = SecurityTestActors.asUser { accounts.getSelected()!!.accountContextId }
        assertThat(countContexts()).isEqualTo(before + 1)
        assertThat(SecurityTestActors.asUser { attempts.getAvailableAttempts(id, ids.source)?.count }).isZero()
        SecurityTestActors.asUser { attempts.clearAvailableAttemptsForSelectedEnvironment(ids.source) }
        assertThat(SecurityTestActors.asUser { attempts.getAvailableAttempts(id, ids.source) }).isNull()
        assertThat(countContexts()).isEqualTo(before + 1)
    }

    @Test
    fun `invalid source write rolls back the newly created context`() {
        selectStandard()
        val before = countContexts()
        assertThatThrownBy {
            SecurityTestActors.asUser {
                attempts.setAvailableAttemptsForSelectedEnvironment(AcquisitionSourceId.generate(), 1)
            }
        }.isInstanceOf(RuntimeException::class.java)
        assertThat(countContexts()).isEqualTo(before)
        assertThat(SecurityTestActors.asUser { accounts.getSelected() }).isNull()
    }

    @Test
    fun `historical unrecognized selections reuse existing contexts but cannot create new ones`() {
        selectStandard()
        val before = countContexts()
        SecurityTestActors.asUser {
            selections.select(EvaluationContext(CompatibilityVersion(3, 28), Ruleset.NORMAL,
                leagueDefinitionId = ids.standardLeague))
        }
        mvc.perform(get("/uniques/${ids.uniqueC.value}").with(user("test-user")))
            .andExpect(content().string(not(containsString("Save ownership"))))
        mvc.perform(post("/uniques/${ids.uniqueC.value}/ownership").param("quantity", "1")
            .with(user("test-user")).with(csrf()))
            .andExpect(status().isConflict)
            .andExpect(content().string(containsString("not recognized for new personal data")))
        assertThat(countContexts()).isEqualTo(before)

        // A prior personal context remains usable even if its selected version is not curated.
        SecurityTestActors.asUser {
            selections.select(EvaluationContext(CompatibilityVersion(3, 28), Ruleset.NORMAL,
                leagueDefinitionId = ids.league))
            ownership.setOwnedQuantityForSelectedEnvironment(ids.uniqueC, 1)
        }
        assertThat(countContexts()).isEqualTo(before)
        assertThat(SecurityTestActors.asUser {
            currentOwnership.getCurrentOwnership(ids.account).quantityOf(ids.uniqueC)
        }).isEqualTo(1)
    }

    @Test
    fun `switching Standard versions preserves one personal context`() {
        selectStandard()
        SecurityTestActors.asUser { ownership.setOwnedQuantityForSelectedEnvironment(ids.uniqueA, 1) }
        val id = SecurityTestActors.asUser { accounts.getSelected()!!.accountContextId }
        val before = countContexts()
        leagues.recognizeVersion(ids.standardLeague, CompatibilityVersion(3, 29))
        selectStandard(CompatibilityVersion(3, 29))
        SecurityTestActors.asUser { ownership.setOwnedQuantityForSelectedEnvironment(ids.uniqueB, 1) }
        assertThat(SecurityTestActors.asUser { accounts.getSelected()!!.accountContextId }).isEqualTo(id)
        assertThat(countContexts()).isEqualTo(before)
        assertThat(SecurityTestActors.asUser { currentOwnership.getCurrentOwnership(id) }
            .quantityOf(ids.uniqueA)).isEqualTo(1)
    }

    @Test
    fun `USER and ADMIN initialize independent contexts for the same curated league`() {
        val league = LeagueDefinition(
            LeagueDefinitionId.generate(), "New shared challenge", LeagueType.CHALLENGE,
            LeagueParticipation.SSF, LeagueMortality.SOFTCORE, GameRealm.PC,
        )
        SecurityTestActors.asAdmin { leagues.saveLeague(league) }
        leagues.recognizeVersion(league.id, CompatibilityVersion(3, 30))
        val before = countContexts()
        val chosen = EvaluationContext(CompatibilityVersion(3, 30), Ruleset.NORMAL,
            leagueDefinitionId = league.id)
        val userId = SecurityTestActors.asUser {
            selections.select(chosen)
            ownership.setOwnedQuantityForSelectedEnvironment(ids.uniqueA, 1)
            accounts.getSelected()!!.accountContextId
        }
        val adminId = SecurityTestActors.asAdmin {
            selections.select(chosen)
            ownership.setOwnedQuantityForSelectedEnvironment(ids.uniqueB, 2)
            accounts.getSelected()!!.accountContextId
        }
        assertThat(userId).isNotEqualTo(adminId)
        assertThat(countContexts()).isEqualTo(before + 2)
        assertThat(SecurityTestActors.asUser { currentOwnership.getCurrentOwnership(userId).quantityOf(ids.uniqueA) })
            .isEqualTo(1)
        assertThat(SecurityTestActors.asAdmin { currentOwnership.getCurrentOwnership(adminId).quantityOf(ids.uniqueB) })
            .isEqualTo(2)
        assertThatThrownBy { SecurityTestActors.asUser { currentOwnership.getCurrentOwnership(adminId) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `simultaneous first writes converge on one personal context`() {
        selectStandard()
        val before = countContexts()
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val futures = listOf(ids.uniqueA, ids.uniqueB).map { unique ->
                executor.submit(Callable {
                    SecurityTestActors.asUser {
                        ready.countDown()
                        check(start.await(10, TimeUnit.SECONDS))
                        ownership.setOwnedQuantityForSelectedEnvironment(unique, 1)
                    }
                })
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
            start.countDown()
            futures.forEach { it.get(20, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
        val id = SecurityTestActors.asUser { accounts.getSelected()!!.accountContextId }
        assertThat(countContexts()).isEqualTo(before + 1)
        val snapshot = SecurityTestActors.asUser { currentOwnership.getCurrentOwnership(id) }
        assertThat(snapshot.quantityOf(ids.uniqueA)).isEqualTo(1)
        assertThat(snapshot.quantityOf(ids.uniqueB)).isEqualTo(1)
    }

    private fun selectStandard(version: CompatibilityVersion = CompatibilityVersion(3, 30)) {
        SecurityTestActors.asUser {
            options.select(GameEnvironmentChoice(version, LeagueType.STANDARD,
                LeagueMortality.SOFTCORE, LeagueParticipation.SSF))
        }
    }

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
