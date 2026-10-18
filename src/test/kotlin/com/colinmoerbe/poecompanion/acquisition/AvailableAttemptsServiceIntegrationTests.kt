package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.attempts.persistence.SpringDataAvailableAttemptsRepository
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence.AcquisitionSourcePersistenceAdapter
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
 * Verifies application commands and PostgreSQL constraints for manually recorded available attempts.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class AvailableAttemptsServiceIntegrationTests(
    @Autowired private val service: AvailableAttemptsService,
    @Autowired private val repository: SpringDataAvailableAttemptsRepository,
    @Autowired private val sourcePersistenceAdapter: AcquisitionSourcePersistenceAdapter,
    @Autowired private val leagueDefinitionPersistenceAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val accountContextPersistenceAdapter: AccountContextPersistenceAdapter,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearAttemptsBeforeTest() {
        repository.deleteAllInBatch()
    }

    @AfterEach
    fun clearAttemptsAfterTest() {
        repository.deleteAllInBatch()
    }

    @Test
    fun `unrecorded attempts should be absent rather than interpreted as zero`() {
        val accountContextId = createPersistedAccountContext().id
        val sourceId = createPersistedSource().id

        assertThat(service.getAvailableAttempts(accountContextId, sourceId)).isNull()
    }

    @Test
    fun `setting available attempts should create and update one recorded count`() {
        val accountContextId = createPersistedAccountContext().id
        val sourceId = createPersistedSource().id

        service.setAvailableAttempts(accountContextId, sourceId, 12)
        assertThat(service.getAvailableAttempts(accountContextId, sourceId))
            .isEqualTo(AvailableAttempts(accountContextId, sourceId, 12))

        service.setAvailableAttempts(accountContextId, sourceId, 5)
        assertThat(service.getAvailableAttempts(accountContextId, sourceId))
            .isEqualTo(AvailableAttempts(accountContextId, sourceId, 5))
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `setting zero should preserve an explicit recorded count`() {
        val accountContextId = createPersistedAccountContext().id
        val sourceId = createPersistedSource().id

        service.setAvailableAttempts(accountContextId, sourceId, 12)
        service.setAvailableAttempts(accountContextId, sourceId, 0)

        assertThat(service.getAvailableAttempts(accountContextId, sourceId))
            .isEqualTo(AvailableAttempts(accountContextId, sourceId, 0))
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `clearing should remove only one account and source pair`() {
        val firstAccountId = createPersistedAccountContext().id
        val secondAccountId = createPersistedAccountContext().id
        val firstSourceId = createPersistedSource().id
        val secondSourceId = createPersistedSource().id

        service.setAvailableAttempts(firstAccountId, firstSourceId, 0)
        service.setAvailableAttempts(firstAccountId, secondSourceId, 3)
        service.setAvailableAttempts(secondAccountId, firstSourceId, 4)

        service.clearAvailableAttempts(firstAccountId, firstSourceId)

        assertThat(service.getAvailableAttempts(firstAccountId, firstSourceId)).isNull()
        assertThat(service.getAvailableAttempts(firstAccountId, secondSourceId)?.count).isEqualTo(3)
        assertThat(service.getAvailableAttempts(secondAccountId, firstSourceId)?.count).isEqualTo(4)
        assertThat(repository.count()).isEqualTo(2L)
    }

    @Test
    fun `clearing an unrecorded count should not create a fact`() {
        val accountContextId = createPersistedAccountContext().id
        val sourceId = createPersistedSource().id

        service.clearAvailableAttempts(accountContextId, sourceId)

        assertThat(service.getAvailableAttempts(accountContextId, sourceId)).isNull()
        assertThat(repository.count()).isZero()
    }

    @Test
    fun `negative attempts should be rejected without changing an existing count`() {
        val accountContextId = createPersistedAccountContext().id
        val sourceId = createPersistedSource().id
        service.setAvailableAttempts(accountContextId, sourceId, 2)

        assertThatThrownBy {
            service.setAvailableAttempts(accountContextId, sourceId, -1)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThat(service.getAvailableAttempts(accountContextId, sourceId)?.count).isEqualTo(2)
    }

    @Test
    fun `database should accept an explicitly recorded zero`() {
        val accountContextId = createPersistedAccountContext().id
        val sourceId = createPersistedSource().id

        insertAttempt(accountContextId, sourceId, 0)

        assertThat(service.getAvailableAttempts(accountContextId, sourceId)?.count).isZero()
    }

    @Test
    fun `database should reject negative attempts`() {
        val accountContextId = createPersistedAccountContext().id
        val sourceId = createPersistedSource().id

        assertThatThrownBy { insertAttempt(accountContextId, sourceId, -1) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject duplicate account and source pairs`() {
        val accountContextId = createPersistedAccountContext().id
        val sourceId = createPersistedSource().id
        insertAttempt(accountContextId, sourceId, 1)

        assertThatThrownBy { insertAttempt(accountContextId, sourceId, 2) }
            .isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `database should reject an unknown account context`() {
        val sourceId = createPersistedSource().id

        assertThatThrownBy { insertAttempt(AccountContextId.generate(), sourceId, 1) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject an unknown acquisition source`() {
        val accountContextId = createPersistedAccountContext().id

        assertThatThrownBy { insertAttempt(accountContextId, AcquisitionSourceId.generate(), 1) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun insertAttempt(
        accountContextId: AccountContextId,
        acquisitionSourceId: AcquisitionSourceId,
        count: Int,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.available_attempts (account_context_id, acquisition_source_id, attempt_count)
            VALUES (?, ?, ?)
            """.trimIndent(),
            accountContextId.value,
            acquisitionSourceId.value,
            count,
        )
    }

    private fun createPersistedSource(): AcquisitionSource =
        AcquisitionSource(AcquisitionSourceId.generate()).also(sourcePersistenceAdapter::save)

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
