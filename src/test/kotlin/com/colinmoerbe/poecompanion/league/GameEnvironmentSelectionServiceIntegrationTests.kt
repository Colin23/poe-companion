package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.SecurityTestActors
import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountContextRepository
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
import com.colinmoerbe.poecompanion.league.persistence.SpringDataLeagueDefinitionRepository
import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.user.username=test-user",
        "poe-companion.security.user.password=test-password",
        "poe-companion.security.admin.username=test-admin",
        "poe-companion.security.admin.password=test-admin-password",
    ],
)
@WithMockUser(username = "test-user")
internal class GameEnvironmentSelectionServiceIntegrationTests(
    @Autowired private val selection: GameEnvironmentSelectionService,
    @Autowired private val personal: AccountEvaluationService,
    @Autowired private val leagues: LeagueDefinitionPersistenceAdapter,
    @Autowired private val accounts: AccountContextPersistenceAdapter,
    @Autowired private val selectionRepository: SpringDataAccountEvaluationSelectionRepository,
    @Autowired private val accountRepository: SpringDataAccountContextRepository,
    @Autowired private val leagueRepository: SpringDataLeagueDefinitionRepository,
) {
    @BeforeEach
    fun before() = clear()

    @AfterEach
    fun after() = clear()

    @Test
    fun `selection is possible without an owned AccountContext and automatically matches a later one`() {
        val league = league()
        val version = CompatibilityVersion(3, 30)
        val chosen = EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 2, "b"), league.id)
        selection.select(chosen)

        assertThat(selection.getSelected()).isEqualTo(chosen)
        assertThat(personal.getSelected()).isNull()
        assertThat(accountRepository.count()).isZero()

        val account = AccountContext(
            AccountContextId.generate(), league.id, Ruleset.NORMAL, ConfiguredApplicationUsers.USER,
        ).also(accounts::save)
        assertThat(personal.getSelected()?.accountContextId).isEqualTo(account.id)
        assertThat(personal.getSelected()?.evaluationContext).isEqualTo(chosen)
    }

    @Test
    fun `Standard context remains the same across compatibility versions`() {
        val league = league(LeagueType.STANDARD)
        val owned = AccountContext(
            AccountContextId.generate(), league.id, Ruleset.NORMAL, ConfiguredApplicationUsers.USER,
        ).also(accounts::save)
        selection.select(EvaluationContext(CompatibilityVersion(3, 29), Ruleset.NORMAL, leagueDefinitionId = league.id))
        assertThat(personal.getSelected()?.accountContextId).isEqualTo(owned.id)
        selection.select(EvaluationContext(CompatibilityVersion(3, 30), Ruleset.NORMAL, leagueDefinitionId = league.id))
        assertThat(personal.getSelected()?.accountContextId).isEqualTo(owned.id)
        assertThat(accountRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `selections are per user and other users personal context remains inaccessible`() {
        val challenge = league()
        val standard = league(LeagueType.STANDARD)
        val myAccount = AccountContext(
            AccountContextId.generate(), challenge.id, Ruleset.NORMAL, ConfiguredApplicationUsers.USER,
        ).also(accounts::save)
        val adminAccount = AccountContext(
            AccountContextId.generate(), standard.id, Ruleset.NORMAL, ConfiguredApplicationUsers.ADMIN,
        ).also(accounts::save)
        val userChoice = EvaluationContext(CompatibilityVersion(3, 30), Ruleset.NORMAL, leagueDefinitionId = challenge.id)
        val adminChoice = EvaluationContext(CompatibilityVersion(3, 29), Ruleset.NORMAL, leagueDefinitionId = standard.id)
        selection.select(userChoice)
        SecurityTestActors.asAdmin {
            assertThat(selection.getSelected()).isNull()
            selection.select(userChoice)
            assertThat(personal.getSelected()).isNull()
            selection.select(adminChoice)
            assertThat(personal.getSelected()?.accountContextId).isEqualTo(adminAccount.id)
        }
        assertThat(selection.getSelected()).isEqualTo(userChoice)
        assertThat(personal.getSelected()?.accountContextId).isEqualTo(myAccount.id)
        assertThat(selectionRepository.count()).isEqualTo(2L)
    }

    @Test
    fun `unknown league and Ruthless selection are rejected without changing saved choice`() {
        val league = league()
        val chosen = EvaluationContext(CompatibilityVersion(3, 30), Ruleset.NORMAL, leagueDefinitionId = league.id)
        selection.select(chosen)
        assertThatThrownBy {
            selection.select(
                EvaluationContext(CompatibilityVersion(3, 30), Ruleset.NORMAL, leagueDefinitionId = LeagueDefinitionId.generate()),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            selection.select(EvaluationContext(CompatibilityVersion(3, 30), Ruleset.RUTHLESS, leagueDefinitionId = league.id))
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(selection.getSelected()).isEqualTo(chosen)
    }

    private fun league(type: LeagueType = LeagueType.CHALLENGE): LeagueDefinition = LeagueDefinition(
        LeagueDefinitionId.generate(),
        if (type == LeagueType.STANDARD) "Standard" else "Challenge",
        type,
        LeagueParticipation.SSF,
        LeagueMortality.SOFTCORE,
        GameRealm.PC,
    ).also(leagues::save)

    private fun clear() {
        selectionRepository.deleteAllInBatch()
        accountRepository.deleteAllInBatch()
        leagueRepository.deleteAllInBatch()
    }
}
