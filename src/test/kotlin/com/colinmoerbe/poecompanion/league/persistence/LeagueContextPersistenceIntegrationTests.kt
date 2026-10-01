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
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Verifies PostgreSQL persistence for league definitions and isolated account contexts.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
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
            )

        leagueDefinitionPersistenceAdapter.save(leagueDefinition)
        accountContextPersistenceAdapter.save(original)
        val persisted = accountContextPersistenceAdapter.findById(original.id)

        assertThat(persisted?.id).isEqualTo(original.id)
        assertThat(persisted?.leagueDefinitionId).isEqualTo(leagueDefinition.id)
        assertThat(persisted?.ruleset).isEqualTo(Ruleset.NORMAL)
    }

    @Test
    fun `database should reject account context referencing an unknown league`() {
        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO poe_companion.account_context (id, league_definition_id, ruleset)
                VALUES (?, ?, ?)
                """.trimIndent(),
                AccountContextId.generate().value,
                LeagueDefinitionId.generate().value,
                Ruleset.NORMAL.name,
            )
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }
}
