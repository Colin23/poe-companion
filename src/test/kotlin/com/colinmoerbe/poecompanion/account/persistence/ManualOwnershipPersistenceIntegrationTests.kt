package com.colinmoerbe.poecompanion.account.persistence

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.account.ManualOwnership
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
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Verifies the PostgreSQL persistence contract for manual ownership.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class ManualOwnershipPersistenceIntegrationTests(
    @Autowired private val persistenceAdapter: ManualOwnershipPersistenceAdapter,
    @Autowired private val springDataRepository: SpringDataManualOwnershipRepository,
    @Autowired private val uniqueDefinitionPersistenceAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val leagueDefinitionPersistenceAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val accountContextPersistenceAdapter: AccountContextPersistenceAdapter,
    @Autowired private val jdbcTemplate: JdbcTemplate,
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
    fun `zero manual ownership quantity should persist`() {
        val accountContext = createPersistedAccountContext()
        val uniqueDefinition = createPersistedUniqueDefinition()
        val original =
            ManualOwnership(
                accountContextId = accountContext.id,
                uniqueDefinitionId = uniqueDefinition.id,
                quantity = 0,
            )

        persistenceAdapter.save(original)
        val persisted = persistenceAdapter.findBy(accountContext.id, uniqueDefinition.id)

        assertThat(persisted).isEqualTo(original)
    }

    @Test
    fun `saving the same context and unique should update the current quantity`() {
        val accountContext = createPersistedAccountContext()
        val uniqueDefinition = createPersistedUniqueDefinition()

        persistenceAdapter.save(
            ManualOwnership(
                accountContextId = accountContext.id,
                uniqueDefinitionId = uniqueDefinition.id,
                quantity = 1,
            ),
        )
        persistenceAdapter.save(
            ManualOwnership(
                accountContextId = accountContext.id,
                uniqueDefinitionId = uniqueDefinition.id,
                quantity = 2,
            ),
        )

        val persisted = persistenceAdapter.findBy(accountContext.id, uniqueDefinition.id)

        assertThat(persisted?.quantity).isEqualTo(2)
        assertThat(springDataRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `database should reject negative manual ownership quantities`() {
        val accountContext = createPersistedAccountContext()
        val uniqueDefinition = createPersistedUniqueDefinition()

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.manual_ownership (account_context_id, unique_definition_id, quantity)
                VALUES (?, ?, ?)
                """.trimIndent(),
                accountContext.id.value,
                uniqueDefinition.id.value,
                -1,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject duplicate manual ownership facts`() {
        val accountContext = createPersistedAccountContext()
        val uniqueDefinition = createPersistedUniqueDefinition()

        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.manual_ownership (account_context_id, unique_definition_id, quantity)
            VALUES (?, ?, ?)
            """.trimIndent(),
            accountContext.id.value,
            uniqueDefinition.id.value,
            1,
        )

        // Bypass JPA so the test exercises the PostgreSQL composite primary key directly.
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.manual_ownership (account_context_id, unique_definition_id, quantity)
                VALUES (?, ?, ?)
                """.trimIndent(),
                accountContext.id.value,
                uniqueDefinition.id.value,
                2,
            )
        }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `database should reject manual ownership for an unknown account context`() {
        val uniqueDefinition = createPersistedUniqueDefinition()

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.manual_ownership (account_context_id, unique_definition_id, quantity)
                VALUES (?, ?, ?)
                """.trimIndent(),
                AccountContextId.generate().value,
                uniqueDefinition.id.value,
                1,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject manual ownership for an unknown unique definition`() {
        val accountContext = createPersistedAccountContext()

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.manual_ownership (account_context_id, unique_definition_id, quantity)
                VALUES (?, ?, ?)
                """.trimIndent(),
                accountContext.id.value,
                UniqueDefinitionId.generate().value,
                1,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
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
            )
        val accountContext =
            AccountContext(
                id = AccountContextId.generate(),
                leagueDefinitionId = leagueDefinition.id,
                ruleset = Ruleset.NORMAL,
            )

        leagueDefinitionPersistenceAdapter.save(leagueDefinition)
        accountContextPersistenceAdapter.save(accountContext)

        return accountContext
    }
}
