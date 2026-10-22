package com.colinmoerbe.poecompanion.league.persistence

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.league.AccountContext
import com.colinmoerbe.poecompanion.league.AccountContextId
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Verifies PostgreSQL persistence for league definitions and isolated account contexts.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.user.username=test-user",
        "poe-companion.security.user.password=test-password",
        "poe-companion.security.admin.username=test-admin",
        "poe-companion.security.admin.password=test-admin-password",
    ],
)
internal class LeagueContextPersistenceIntegrationTests(
    @Autowired private val leagueDefinitionPersistenceAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val accountContextPersistenceAdapter: AccountContextPersistenceAdapter,
    @Autowired private val leagueDefinitionRepository: SpringDataLeagueDefinitionRepository,
    @Autowired private val accountContextRepository: SpringDataAccountContextRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {

    @BeforeEach
    fun clearLeagueContextData() {
        accountContextRepository.deleteAllInBatch()
        leagueDefinitionRepository.deleteAllInBatch()
    }

    @Test
    fun `persisted league definition should retain its structured dimensions`() {
        val original =
            LeagueDefinition(
                id = LeagueDefinitionId.generate(),
                name = "Allflame",
                type = LeagueType.CHALLENGE,
                participation = LeagueParticipation.SSF,
                mortality = LeagueMortality.SOFTCORE,
                realm = GameRealm.PC,
            )

        leagueDefinitionPersistenceAdapter.save(original)
        val persisted = leagueDefinitionPersistenceAdapter.findById(original.id)

        assertThat(persisted?.id).isEqualTo(original.id)
        assertThat(persisted?.name).isEqualTo("Allflame")
        assertThat(persisted?.type).isEqualTo(LeagueType.CHALLENGE)
        assertThat(persisted?.participation).isEqualTo(LeagueParticipation.SSF)
        assertThat(persisted?.mortality).isEqualTo(LeagueMortality.SOFTCORE)
        assertThat(persisted?.realm).isEqualTo(GameRealm.PC)
    }

    @Test
    fun `persisted account context should retain league identity and ruleset`() {
        val leagueDefinition =
            LeagueDefinition(
                id = LeagueDefinitionId.generate(),
                name = "Allflame",
                type = LeagueType.CHALLENGE,
                participation = LeagueParticipation.SSF,
                mortality = LeagueMortality.SOFTCORE,
                realm = GameRealm.PC,
            )
        val original =
            AccountContext(
                id = AccountContextId.generate(),
                leagueDefinitionId = leagueDefinition.id,
                ruleset = Ruleset.NORMAL,
                ownerApplicationUserId = ConfiguredApplicationUsers.USER,
            )

        leagueDefinitionPersistenceAdapter.save(leagueDefinition)
        accountContextPersistenceAdapter.save(original)
        val persisted = accountContextPersistenceAdapter.findById(original.id)

        assertThat(persisted?.id).isEqualTo(original.id)
        assertThat(persisted?.leagueDefinitionId).isEqualTo(leagueDefinition.id)
        assertThat(persisted?.ruleset).isEqualTo(Ruleset.NORMAL)
        assertThat(persisted?.ownerApplicationUserId).isEqualTo(ConfiguredApplicationUsers.USER)
    }

    @Test
    fun `saving the same account context identity and configuration should be idempotent`() {
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
                ownerApplicationUserId = ConfiguredApplicationUsers.USER,
            )

        leagueDefinitionPersistenceAdapter.save(leagueDefinition)
        accountContextPersistenceAdapter.save(accountContext)
        accountContextPersistenceAdapter.save(accountContext)

        assertThat(accountContextPersistenceAdapter.findById(accountContext.id)?.ruleset).isEqualTo(Ruleset.NORMAL)
    }

    @Test
    fun `existing account context identity should reject ruleset redefinition`() {
        val leagueDefinition =
            LeagueDefinition(
                id = LeagueDefinitionId.generate(),
                name = "Allflame",
                type = LeagueType.CHALLENGE,
                participation = LeagueParticipation.SSF,
                mortality = LeagueMortality.SOFTCORE,
                realm = GameRealm.PC,
            )
        val id = AccountContextId.generate()

        leagueDefinitionPersistenceAdapter.save(leagueDefinition)
        accountContextPersistenceAdapter.save(
            AccountContext(
                id = id,
                leagueDefinitionId = leagueDefinition.id,
                ruleset = Ruleset.NORMAL,
                ownerApplicationUserId = ConfiguredApplicationUsers.USER,
            ),
        )

        assertThatThrownBy {
            accountContextPersistenceAdapter.save(
                AccountContext(
                    id = id,
                    leagueDefinitionId = leagueDefinition.id,
                    ruleset = Ruleset.RUTHLESS,
                    ownerApplicationUserId = ConfiguredApplicationUsers.USER,
                ),
            )
        }.isInstanceOf(InvalidDataAccessApiUsageException::class.java)
            .hasCauseInstanceOf(IllegalArgumentException::class.java)

        assertThat(accountContextPersistenceAdapter.findById(id)?.ruleset).isEqualTo(Ruleset.NORMAL)
    }

    @Test
    fun `persisted AccountContext ownership cannot be reassigned`() {
        val league = LeagueDefinition(
            id = LeagueDefinitionId.generate(),
            name = "Identity test",
            type = LeagueType.CHALLENGE,
            participation = LeagueParticipation.SSF,
            mortality = LeagueMortality.SOFTCORE,
            realm = GameRealm.PC,
        )
        leagueDefinitionPersistenceAdapter.save(league)
        val id = AccountContextId.generate()
        accountContextPersistenceAdapter.save(
            AccountContext(id, league.id, Ruleset.NORMAL, ConfiguredApplicationUsers.USER),
        )

        assertThatThrownBy {
            accountContextPersistenceAdapter.save(
                AccountContext(id, league.id, Ruleset.NORMAL, ConfiguredApplicationUsers.ADMIN),
            )
        }.isInstanceOf(InvalidDataAccessApiUsageException::class.java)
            .hasCauseInstanceOf(IllegalArgumentException::class.java)

        assertThat(accountContextPersistenceAdapter.findById(id)?.ownerApplicationUserId)
            .isEqualTo(ConfiguredApplicationUsers.USER)
    }

    @Test
    fun `database rejects game selection referencing unknown canonical league`() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.account_evaluation_selection
                    (application_user_id, league_definition_id, ruleset,
                     compatibility_version_major, compatibility_version_minor)
                VALUES (?, ?, 'NORMAL', 3, 30)
                """.trimIndent(),
                ConfiguredApplicationUsers.USER.value,
                LeagueDefinitionId.generate().value,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database enforces one owner league ruleset context but allows different users`() {
        val league = LeagueDefinition(
            LeagueDefinitionId.generate(),
            "Shared League",
            LeagueType.CHALLENGE,
            LeagueParticipation.SSF,
            LeagueMortality.SOFTCORE,
            GameRealm.PC,
        )
        leagueDefinitionPersistenceAdapter.save(league)
        accountContextPersistenceAdapter.save(
            AccountContext(AccountContextId.generate(), league.id, Ruleset.NORMAL, ConfiguredApplicationUsers.USER),
        )
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.account_context
                    (id, league_definition_id, ruleset, owner_application_user_id)
                VALUES (?, ?, 'NORMAL', ?)
                """.trimIndent(),
                AccountContextId.generate().value,
                league.id.value,
                ConfiguredApplicationUsers.USER.value,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
        accountContextPersistenceAdapter.save(
            AccountContext(AccountContextId.generate(), league.id, Ruleset.NORMAL, ConfiguredApplicationUsers.ADMIN),
        )
        assertThat(accountContextRepository.count()).isEqualTo(2L)
    }

    @Test
    fun `database should reject account context referencing an unknown league`() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.account_context
                    (id, league_definition_id, ruleset, owner_application_user_id)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                AccountContextId.generate().value,
                LeagueDefinitionId.generate().value,
                Ruleset.NORMAL.name,
                ConfiguredApplicationUsers.USER.value,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
