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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * Verifies that persisted manual ownership is projected into normalized current ownership.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class CurrentOwnershipServiceIntegrationTests(
    @Autowired private val currentOwnershipService: CurrentOwnershipService,
    @Autowired private val manualOwnershipPersistenceAdapter: ManualOwnershipPersistenceAdapter,
    @Autowired private val manualOwnershipRepository: SpringDataManualOwnershipRepository,
    @Autowired private val uniqueDefinitionPersistenceAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val leagueDefinitionPersistenceAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val accountContextPersistenceAdapter: AccountContextPersistenceAdapter,
) {

    @BeforeEach
    fun clearManualOwnershipBeforeTest() {
        manualOwnershipRepository.deleteAllInBatch()
    }

    @AfterEach
    fun clearManualOwnershipAfterTest() {
        manualOwnershipRepository.deleteAllInBatch()
    }

    @Test
    fun `current ownership should derive a sparse view from persisted manual quantities`() {
        val accountContext = createPersistedAccountContext()
        val ownedUnique = createPersistedUniqueDefinition()
        val zeroUnique = createPersistedUniqueDefinition()

        manualOwnershipPersistenceAdapter.save(
            ManualOwnership(
                accountContextId = accountContext.id,
                uniqueDefinitionId = ownedUnique.id,
                quantity = 2,
            ),
        )
        manualOwnershipPersistenceAdapter.save(
            ManualOwnership(
                accountContextId = accountContext.id,
                uniqueDefinitionId = zeroUnique.id,
                quantity = 0,
            ),
        )

        val currentOwnership = currentOwnershipService.getCurrentOwnership(accountContext.id)

        assertThat(currentOwnership.accountContextId).isEqualTo(accountContext.id)
        assertThat(currentOwnership.quantities).containsExactlyEntriesOf(mapOf(ownedUnique.id to 2))
        assertThat(currentOwnership.quantityOf(zeroUnique.id)).isZero()
    }

    @Test
    fun `current ownership should remain isolated by account context`() {
        val leagueDefinition = createPersistedLeagueDefinition()
        val contextA =
            AccountContext(
                id = AccountContextId.generate(),
                leagueDefinitionId = leagueDefinition.id,
                ruleset = Ruleset.NORMAL,
            ).also(accountContextPersistenceAdapter::save)
        val contextB =
            AccountContext(
                id = AccountContextId.generate(),
                leagueDefinitionId = leagueDefinition.id,
                ruleset = Ruleset.NORMAL,
            ).also(accountContextPersistenceAdapter::save)
        val sharedUnique = createPersistedUniqueDefinition()
        val contextBOnlyUnique = createPersistedUniqueDefinition()

        manualOwnershipPersistenceAdapter.save(
            ManualOwnership(
                accountContextId = contextA.id,
                uniqueDefinitionId = sharedUnique.id,
                quantity = 2,
            ),
        )
        manualOwnershipPersistenceAdapter.save(
            ManualOwnership(
                accountContextId = contextB.id,
                uniqueDefinitionId = sharedUnique.id,
                quantity = 1,
            ),
        )
        manualOwnershipPersistenceAdapter.save(
            ManualOwnership(
                accountContextId = contextB.id,
                uniqueDefinitionId = contextBOnlyUnique.id,
                quantity = 1,
            ),
        )

        val ownershipA = currentOwnershipService.getCurrentOwnership(contextA.id)
        val ownershipB = currentOwnershipService.getCurrentOwnership(contextB.id)

        assertThat(ownershipA.accountContextId).isEqualTo(contextA.id)
        assertThat(ownershipA.quantityOf(sharedUnique.id)).isEqualTo(2)
        assertThat(ownershipA.quantityOf(contextBOnlyUnique.id)).isZero()

        assertThat(ownershipB.accountContextId).isEqualTo(contextB.id)
        assertThat(ownershipB.quantityOf(sharedUnique.id)).isEqualTo(1)
        assertThat(ownershipB.quantityOf(contextBOnlyUnique.id)).isEqualTo(1)
    }

    private fun createPersistedUniqueDefinition(): UniqueDefinition =
        UniqueDefinition(UniqueDefinitionId.generate()).also(uniqueDefinitionPersistenceAdapter::save)

    private fun createPersistedLeagueDefinition(): LeagueDefinition =
        LeagueDefinition(
            id = LeagueDefinitionId.generate(),
            name = "Allflame",
            type = LeagueType.CHALLENGE,
            participation = LeagueParticipation.SSF,
            mortality = LeagueMortality.SOFTCORE,
            realm = GameRealm.PC,
        ).also(leagueDefinitionPersistenceAdapter::save)

    private fun createPersistedAccountContext(): AccountContext {
        val leagueDefinition = createPersistedLeagueDefinition()
        val accountContext =
            AccountContext(
                id = AccountContextId.generate(),
                leagueDefinitionId = leagueDefinition.id,
                ruleset = Ruleset.NORMAL,
            )

        accountContextPersistenceAdapter.save(accountContext)

        return accountContext
    }
}
