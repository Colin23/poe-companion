package com.colinmoerbe.poecompanion.acquisition.overrides

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.overrides.persistence.SpringDataDropRateOverrideRevisionRepository
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence.AcquisitionSourcePersistenceAdapter
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
import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/** Verifies V021 database invariants independently of Kotlin domain validation. */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.user.username=test-user",
        "poe-companion.security.user.password=test-password",
        "poe-companion.security.admin.username=test-admin",
        "poe-companion.security.admin.password=test-admin-password",
    ],
)
internal class DropRateOverrideDatabaseIntegrationTests(
    @Autowired private val revisions: SpringDataDropRateOverrideRevisionRepository,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val accountAdapter: AccountContextPersistenceAdapter,
    @Autowired private val leagueAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val relationshipAdapter: DropRelationshipPersistenceAdapter,
    @Autowired private val sourceAdapter: AcquisitionSourcePersistenceAdapter,
    @Autowired private val uniqueAdapter: UniqueDefinitionPersistenceAdapter,
) {
    @BeforeEach
    fun cleanBeforeTest() {
        revisions.deleteAllInBatch()
    }

    @AfterEach
    fun cleanAfterTest() {
        revisions.deleteAllInBatch()
    }

    @Test
    fun `nullable patch and suffix must not allow duplicate active confirmations`() {
        val account = account()
        val relationship = relationship()
        insert(account.id.value, relationship.id.value, patch = null)

        assertThatThrownBy { insert(account.id.value, relationship.id.value, patch = null) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(revisions.count()).isEqualTo(1L)
    }

    @Test
    fun `distinct exact patches and patch unspecified are independent slots`() {
        val account = account()
        val relationship = relationship()
        insert(account.id.value, relationship.id.value, patch = null)
        insert(account.id.value, relationship.id.value, patch = 1)
        insert(account.id.value, relationship.id.value, patch = 2)
        insert(account.id.value, relationship.id.value, patch = 1, suffix = "a")

        assertThat(revisions.count()).isEqualTo(4L)
        assertThatThrownBy { insert(account.id.value, relationship.id.value, patch = 1) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database rejects invalid rates and malformed patch scope`() {
        val account = account()
        val relationship = relationship()

        listOf(0.0, -0.01, 1.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { value ->
            assertThatThrownBy { insert(account.id.value, relationship.id.value, probability = value) }
                .isInstanceOf(DataIntegrityViolationException::class.java)
        }
        assertThatThrownBy { insert(account.id.value, relationship.id.value, patch = null, suffix = "a") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insert(account.id.value, relationship.id.value, patch = -1) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(revisions.count()).isZero()
    }

    @Test
    fun `database rejects unknown account or relationship IDs`() {
        val account = account()
        val relationship = relationship()

        assertThatThrownBy { insert(UUID.randomUUID(), relationship.id.value) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insert(account.id.value, UUID.randomUUID()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database rejects unsupported status and missing historical end timestamp`() {
        val account = account()
        val relationship = relationship()

        assertThatThrownBy {
            insert(account.id.value, relationship.id.value, status = "STALE")
        }.isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy {
            insert(account.id.value, relationship.id.value, status = "REMOVED")
        }.isInstanceOf(DataIntegrityViolationException::class.java)
    }

    private fun insert(
        accountId: UUID,
        relationshipId: UUID,
        patch: Int? = null,
        suffix: String? = null,
        probability: Double = 0.13,
        status: String = "ACTIVE",
    ) {
        jdbc.update(
            """
            INSERT INTO poe_companion.drop_rate_override_revision (
                id, account_context_id, drop_relationship_id,
                compatibility_version_major, compatibility_version_minor,
                game_patch_number, game_patch_suffix, model_type,
                probability_per_attempt, status, confirmed_at
            ) VALUES (?, ?, ?, 3, 30, ?, ?, 'FIXED_BERNOULLI', ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent(),
            UUID.randomUUID(),
            accountId,
            relationshipId,
            patch,
            suffix,
            probability,
            status,
        )
    }

    private fun account(): AccountContext {
        val league = LeagueDefinition(
            id = LeagueDefinitionId.generate(),
            name = "Override constraint test ${UUID.randomUUID()}",
            type = LeagueType.STANDARD,
            participation = LeagueParticipation.SSF,
            mortality = LeagueMortality.HARDCORE,
            realm = GameRealm.PC,
        ).also(leagueAdapter::save)

        return AccountContext(
            AccountContextId.generate(),
            league.id,
            Ruleset.NORMAL,
            ConfiguredApplicationUsers.USER,
        ).also(accountAdapter::save)
    }

    private fun relationship(): DropRelationship {
        val source = AcquisitionSource(AcquisitionSourceId.generate()).also(sourceAdapter::save)
        val unique = UniqueDefinition(UniqueDefinitionId.generate()).also(uniqueAdapter::save)
        return DropRelationship(DropRelationshipId.generate(), source.id, unique.id)
            .also(relationshipAdapter::save)
    }
}
