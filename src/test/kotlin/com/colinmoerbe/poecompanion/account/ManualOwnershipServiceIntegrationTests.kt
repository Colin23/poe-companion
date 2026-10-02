package com.colinmoerbe.poecompanion.account

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.account.persistence.ManualOwnershipPersistenceAdapter
import com.colinmoerbe.poecompanion.account.persistence.SpringDataManualOwnershipRepository
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * Verifies the application command semantics for manually maintained ownership.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class ManualOwnershipServiceIntegrationTests(
    @Autowired private val manualOwnershipService: ManualOwnershipService,
    @Autowired private val persistenceAdapter: ManualOwnershipPersistenceAdapter,
    @Autowired private val springDataRepository: SpringDataManualOwnershipRepository,
    @Autowired private val uniqueDefinitionPersistenceAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val leagueDefinitionPersistenceAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val accountContextPersistenceAdapter: AccountContextPersistenceAdapter,
) {

    @BeforeEach
    fun clearManualOwnershipBeforeTest() {
        springDataRepository.deleteAllInBatch()
    }

    @AfterEach
    fun clearManualOwnershipAfterTest() {
        springDataRepository.deleteAllInBatch()
    }

    @Test
    fun `positive quantity should create manual ownership`() {
        val accountContext = createPersistedAccountContext()
        val uniqueDefinition = createPersistedUniqueDefinition()

        manualOwnershipService.setOwnedQuantity(accountContext.id, uniqueDefinition.id, 2)

        assertThat(persistenceAdapter.findBy(accountContext.id, uniqueDefinition.id)?.quantity).isEqualTo(2)
    }

    @Test
    fun `positive quantity should update existing manual ownership`() {
        val accountContext = createPersistedAccountContext()
        val uniqueDefinition = createPersistedUniqueDefinition()

        manualOwnershipService.setOwnedQuantity(accountContext.id, uniqueDefinition.id, 2)
        manualOwnershipService.setOwnedQuantity(accountContext.id, uniqueDefinition.id, 5)

        assertThat(persistenceAdapter.findBy(accountContext.id, uniqueDefinition.id)?.quantity).isEqualTo(5)
        assertThat(springDataRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `zero quantity should remove only the targeted manual ownership`() {
        val accountContext = createPersistedAccountContext()
        val otherAccountContext = createPersistedAccountContext()
        val uniqueDefinition = createPersistedUniqueDefinition()
        val otherUniqueDefinition = createPersistedUniqueDefinition()

        manualOwnershipService.setOwnedQuantity(accountContext.id, uniqueDefinition.id, 2)
        manualOwnershipService.setOwnedQuantity(accountContext.id, otherUniqueDefinition.id, 3)
        manualOwnershipService.setOwnedQuantity(otherAccountContext.id, uniqueDefinition.id, 4)

        manualOwnershipService.setOwnedQuantity(accountContext.id, uniqueDefinition.id, 0)

        assertThat(persistenceAdapter.findBy(accountContext.id, uniqueDefinition.id)).isNull()
        assertThat(persistenceAdapter.findBy(accountContext.id, otherUniqueDefinition.id)?.quantity).isEqualTo(3)
        assertThat(persistenceAdapter.findBy(otherAccountContext.id, uniqueDefinition.id)?.quantity).isEqualTo(4)
    }

    @Test
    fun `zero quantity should be a no-op when manual ownership is already absent`() {
        val accountContext = createPersistedAccountContext()
        val uniqueDefinition = createPersistedUniqueDefinition()

        manualOwnershipService.setOwnedQuantity(accountContext.id, uniqueDefinition.id, 0)

        assertThat(persistenceAdapter.findBy(accountContext.id, uniqueDefinition.id)).isNull()
        assertThat(springDataRepository.count()).isZero()
    }

    @Test
    fun `negative quantity should be rejected without changing persistence`() {
        val accountContext = createPersistedAccountContext()
        val uniqueDefinition = createPersistedUniqueDefinition()

        assertThatThrownBy {
            manualOwnershipService.setOwnedQuantity(accountContext.id, uniqueDefinition.id, -1)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThat(persistenceAdapter.findBy(accountContext.id, uniqueDefinition.id)).isNull()
    }

    private fun createPersistedUniqueDefinition(): UniqueDefinition =
        UniqueDefinition(UniqueDefinitionId.generate()).also(uniqueDefinitionPersistenceAdapter::save)

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
}
