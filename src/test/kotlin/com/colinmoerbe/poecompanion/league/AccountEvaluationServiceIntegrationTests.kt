package com.colinmoerbe.poecompanion.league

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountContextRepository
import com.colinmoerbe.poecompanion.league.persistence.SpringDataAccountEvaluationSelectionRepository
import com.colinmoerbe.poecompanion.league.persistence.SpringDataLeagueDefinitionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * Verifies persistence and derivation of the application's selected account evaluation.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class AccountEvaluationServiceIntegrationTests(
    @Autowired private val accountEvaluationService: AccountEvaluationService,
    @Autowired private val accountContextPersistenceAdapter: AccountContextPersistenceAdapter,
    @Autowired private val leagueDefinitionPersistenceAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val selectionRepository: SpringDataAccountEvaluationSelectionRepository,
    @Autowired private val accountContextRepository: SpringDataAccountContextRepository,
    @Autowired private val leagueDefinitionRepository: SpringDataLeagueDefinitionRepository,
) {

    @BeforeEach
    fun clearSelectionDataBeforeTest() {
        clearSelectionData()
    }

    @AfterEach
    fun clearSelectionDataAfterTest() {
        clearSelectionData()
    }

    @Test
    fun `unconfigured application should have no selected account evaluation`() {
        assertThat(accountEvaluationService.getSelected()).isNull()
    }

    @Test
    fun `selected account evaluation should derive league and ruleset from account context`() {
        val accountContext = createPersistedAccountContext()
        val compatibilityVersion = CompatibilityVersion(3, 30)
        val gamePatch = GamePatch(compatibilityVersion, patch = 2, suffix = "b")

        accountEvaluationService.select(
            accountContextId = accountContext.id,
            compatibilityVersion = compatibilityVersion,
            gamePatch = gamePatch,
        )

        val selected = accountEvaluationService.getSelected()

        assertThat(selected?.accountContextId).isEqualTo(accountContext.id)
        assertThat(selected?.evaluationContext?.compatibilityVersion).isEqualTo(compatibilityVersion)
        assertThat(selected?.evaluationContext?.gamePatch).isEqualTo(gamePatch)
        assertThat(selected?.evaluationContext?.ruleset).isEqualTo(accountContext.ruleset)
        assertThat(selected?.evaluationContext?.leagueDefinitionId).isEqualTo(accountContext.leagueDefinitionId)
    }

    @Test
    fun `selected account evaluation should support compatibility version without exact patch`() {
        val accountContext = createPersistedAccountContext()
        val compatibilityVersion = CompatibilityVersion(3, 30)

        accountEvaluationService.select(
            accountContextId = accountContext.id,
            compatibilityVersion = compatibilityVersion,
        )

        val selected = accountEvaluationService.getSelected()

        assertThat(selected?.evaluationContext?.compatibilityVersion).isEqualTo(compatibilityVersion)
        assertThat(selected?.evaluationContext?.gamePatch).isNull()
    }

    @Test
    fun `selecting another account context should replace the primary selection`() {
        val firstContext = createPersistedAccountContext()
        val secondContext = createPersistedAccountContext()

        accountEvaluationService.select(
            accountContextId = firstContext.id,
            compatibilityVersion = CompatibilityVersion(3, 30),
        )
        accountEvaluationService.select(
            accountContextId = secondContext.id,
            compatibilityVersion = CompatibilityVersion(3, 31),
        )

        val selected = accountEvaluationService.getSelected()

        assertThat(selectionRepository.count()).isEqualTo(1L)
        assertThat(selected?.accountContextId).isEqualTo(secondContext.id)
        assertThat(selected?.evaluationContext?.compatibilityVersion).isEqualTo(CompatibilityVersion(3, 31))
    }

    @Test
    fun `changing evaluation version should not mutate account context identity`() {
        val accountContext = createPersistedAccountContext()

        accountEvaluationService.select(
            accountContextId = accountContext.id,
            compatibilityVersion = CompatibilityVersion(3, 30),
            gamePatch = GamePatch(CompatibilityVersion(3, 30), 2),
        )
        accountEvaluationService.select(
            accountContextId = accountContext.id,
            compatibilityVersion = CompatibilityVersion(3, 31),
        )

        val persistedAccountContext = accountContextPersistenceAdapter.findById(accountContext.id)
        val selected = accountEvaluationService.getSelected()

        assertThat(persistedAccountContext?.id).isEqualTo(accountContext.id)
        assertThat(persistedAccountContext?.leagueDefinitionId).isEqualTo(accountContext.leagueDefinitionId)
        assertThat(persistedAccountContext?.ruleset).isEqualTo(accountContext.ruleset)
        assertThat(selected?.evaluationContext?.compatibilityVersion).isEqualTo(CompatibilityVersion(3, 31))
        assertThat(selected?.evaluationContext?.gamePatch).isNull()
    }

    @Test
    fun `unknown account context should be rejected`() {
        assertThatThrownBy {
            accountEvaluationService.select(
                accountContextId = AccountContextId.generate(),
                compatibilityVersion = CompatibilityVersion(3, 30),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `game patch from another compatibility version should be rejected`() {
        val accountContext = createPersistedAccountContext()

        assertThatThrownBy {
            accountEvaluationService.select(
                accountContextId = accountContext.id,
                compatibilityVersion = CompatibilityVersion(3, 30),
                gamePatch = GamePatch(CompatibilityVersion(3, 29), 3),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun createPersistedAccountContext(): AccountContext {
        val leagueDefinition =
            LeagueDefinition(
                id = LeagueDefinitionId.generate(),
                name = "Allflame",
                type = LeagueType.CHALLENGE,
                participation = LeagueParticipation.SSF,
                mortality = LeagueMortality.SOFTCORE,
                realm = GameRealm.PC,
            ).also(leagueDefinitionPersistenceAdapter::save)
        return AccountContext(
            id = AccountContextId.generate(),
            leagueDefinitionId = leagueDefinition.id,
            ruleset = Ruleset.NORMAL,
        ).also(accountContextPersistenceAdapter::save)
    }

    private fun clearSelectionData() {
        selectionRepository.deleteAllInBatch()
        accountContextRepository.deleteAllInBatch()
        leagueDefinitionRepository.deleteAllInBatch()
    }
}
