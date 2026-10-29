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
import com.colinmoerbe.poecompanion.league.AccountContextAccessService
import com.colinmoerbe.poecompanion.league.AccountContextId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GameEnvironmentSelectionService
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
import com.colinmoerbe.poecompanion.league.persistence.AccountContextPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.LeagueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.persistence.SelectableLeagueVersionPersistenceAdapter
import com.colinmoerbe.poecompanion.security.identity.ApplicationUserId
import com.colinmoerbe.poecompanion.security.identity.ConfiguredApplicationUsers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService
import org.springframework.security.test.context.support.WithMockUser
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * PostgreSQL-backed personal override service contract: scope, history, CAS, ownership and first writes.
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
@Suppress("LargeClass")
@WithMockUser(username = "test-user")
internal class DropRateOverrideServiceIntegrationTests(
    @Autowired private val service: DropRateOverrideService,
    @Autowired private val jdbc: JdbcTemplate,
    @Autowired private val repository: SpringDataDropRateOverrideRevisionRepository,
    @Autowired private val selection: GameEnvironmentSelectionService,
    @Autowired private val accountAccess: AccountContextAccessService,
    @Autowired private val accountAdapter: AccountContextPersistenceAdapter,
    @Autowired private val leagueAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val selectableVersions: SelectableLeagueVersionPersistenceAdapter,
    @Autowired private val sourceAdapter: AcquisitionSourcePersistenceAdapter,
    @Autowired private val uniqueAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val relationshipAdapter: DropRelationshipPersistenceAdapter,
) {
    private val version = CompatibilityVersion(3, 30)

    @BeforeEach
    fun cleanBeforeTest() {
        repository.deleteAllInBatch()
    }

    @AfterEach
    fun cleanAfterTest() {
        repository.deleteAllInBatch()
    }

    @Test
    fun `a current confirmation should round trip the exact revision and probability`() {
        val league = league()
        val relationship = relationship()
        account(league)
        select(league)

        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isNull()
        val saved = service.confirmForSelectedEnvironment(relationship.id, 0.13)

        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(saved)
        assertThat(saved.model.probabilityPerAttempt).isEqualTo(0.13)
        assertThat(repository.findById(saved.revisionId.value).orElseThrow().status.name).isEqualTo("ACTIVE")
    }

    @Test
    fun `different exact patches and patch unspecified must retain independent confirmations`() {
        val league = league()
        val relationship = relationship()
        account(league)

        select(league, patch = null)
        val unspecified = service.confirmForSelectedEnvironment(relationship.id, 0.14)
        select(league, patch = GamePatch(version, 1))
        val one = service.confirmForSelectedEnvironment(relationship.id, 0.13)
        select(league, patch = GamePatch(version, 2))
        val two = service.confirmForSelectedEnvironment(relationship.id, 0.11)

        assertThat(unspecified.revisionId).isNotEqualTo(one.revisionId)
        assertThat(one.revisionId).isNotEqualTo(two.revisionId)
        assertThat(repository.count()).isEqualTo(3L)
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(two)
        select(league, patch = null)
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(unspecified)
        select(league, patch = GamePatch(version, 1))
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(one)
    }

    @Test
    fun `switching league or compatibility version does not transfer confirmed values`() {
        val standard = league()
        val challenge = league(type = LeagueType.CHALLENGE)
        val relationship = relationship()
        account(standard)
        account(challenge)
        select(standard)
        val original = service.confirmForSelectedEnvironment(relationship.id, 0.13)

        select(challenge)
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isNull()
        select(
            standard,
            patch = GamePatch(CompatibilityVersion(3, 31), 1),
            compatibilityVersion = CompatibilityVersion(3, 31),
        )
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isNull()
        select(standard)
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(original)
    }

    @Test
    fun `replacement must preserve old immutable value and mark its revision superseded`() {
        val league = league()
        val relationship = relationship()
        account(league)
        select(league)
        val original = service.confirmForSelectedEnvironment(relationship.id, 0.13)

        val revised = service.confirmForSelectedEnvironment(relationship.id, 0.11, original.revisionId)

        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(revised)
        assertThat(revised.revisionId).isNotEqualTo(original.revisionId)
        val history = repository.findById(original.revisionId.value).orElseThrow()
        assertThat(history.status.name).isEqualTo("SUPERSEDED")
        assertThat(history.probabilityPerAttempt).isEqualTo(0.13)
        assertThat(history.endedAt).isNotNull()
        assertThat(repository.count()).isEqualTo(2L)
    }

    @Test
    fun `stale replacement must fail without changing the current confirmation`() {
        val league = league()
        val relationship = relationship()
        account(league)
        select(league)
        val original = service.confirmForSelectedEnvironment(relationship.id, 0.13)
        val revised = service.confirmForSelectedEnvironment(relationship.id, 0.11, original.revisionId)

        assertThatThrownBy {
            service.confirmForSelectedEnvironment(relationship.id, 0.15, original.revisionId)
        }.isInstanceOf(DropRateOverrideConflictException::class.java)
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(revised)
        assertThat(repository.count()).isEqualTo(2L)
    }

    @Test
    fun `ordinary removal keeps its revision and allows a new first confirmation`() {
        val league = league()
        val relationship = relationship()
        account(league)
        select(league)
        val original = service.confirmForSelectedEnvironment(relationship.id, 0.13)
        val revised = service.confirmForSelectedEnvironment(relationship.id, 0.11, original.revisionId)

        service.removeForSelectedEnvironment(relationship.id, revised.revisionId)

        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isNull()
        val retired = repository.findById(revised.revisionId.value).orElseThrow()
        assertThat(retired.status.name).isEqualTo("REMOVED")
        assertThat(retired.endedAt).isNotNull()
        assertThat(repository.findById(original.revisionId.value).orElseThrow().status.name)
            .isEqualTo("SUPERSEDED")
        val again = service.confirmForSelectedEnvironment(relationship.id, 0.09)
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(again)
        assertThat(repository.count()).isEqualTo(3L)
    }

    @Test
    fun `stale removal cannot remove a newer confirmation or an unrelated patch`() {
        val league = league()
        val relationship = relationship()
        account(league)
        select(league)
        val original = service.confirmForSelectedEnvironment(relationship.id, 0.13)
        val revised = service.confirmForSelectedEnvironment(relationship.id, 0.11, original.revisionId)

        assertThatThrownBy {
            service.removeForSelectedEnvironment(relationship.id, original.revisionId)
        }.isInstanceOf(DropRateOverrideConflictException::class.java)

        select(league, patch = GamePatch(version, 2))
        assertThatThrownBy {
            service.removeForSelectedEnvironment(relationship.id, revised.revisionId)
        }.isInstanceOf(DropRateOverrideConflictException::class.java)
        select(league)
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(revised)
    }

    @Test
    fun `first valid confirmation creates its personal context only after validation`() {
        val league = league()
        selectableVersions.saveIfAbsent(league.id, version)
        val relationship = relationship()
        select(league)

        assertThat(accountAccess.findOwned(league.id, Ruleset.NORMAL)).isNull()
        val confirmed = service.confirmForSelectedEnvironment(relationship.id, 0.13)

        assertThat(accountAccess.findOwned(league.id, Ruleset.NORMAL)?.id)
            .isEqualTo(confirmed.key.accountContextId)
    }

    @Test
    fun `invalid rate or unknown relationship must not create an account context`() {
        val league = league()
        selectableVersions.saveIfAbsent(league.id, version)
        select(league)

        assertThatThrownBy { service.confirmForSelectedEnvironment(DropRelationshipId.generate(), 0.13) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { service.confirmForSelectedEnvironment(DropRelationshipId.generate(), 0.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThat(accountAccess.findOwned(league.id, Ruleset.NORMAL)).isNull()
    }

    @Test
    fun `failed first-write replacement rolls back creation of account context`() {
        val league = league()
        selectableVersions.saveIfAbsent(league.id, version)
        val relationship = relationship()
        select(league)

        assertThatThrownBy {
            service.confirmForSelectedEnvironment(relationship.id, 0.13, DropRateOverrideRevisionId.generate())
        }.isInstanceOf(DropRateOverrideConflictException::class.java)
        assertThat(accountAccess.findOwned(league.id, Ruleset.NORMAL)).isNull()
        assertThat(repository.count()).isZero()
    }

    @Test
    fun `unrecognized selected league version cannot create a first account context`() {
        val league = league()
        val relationship = relationship()
        select(league)

        assertThatThrownBy {
            service.confirmForSelectedEnvironment(relationship.id, 0.13)
        }.isInstanceOf(com.colinmoerbe.poecompanion.league.UnrecognizedSelectedGameEnvironmentException::class.java)
        assertThat(accountAccess.findOwned(league.id, Ruleset.NORMAL)).isNull()
    }

    @Test
    fun `unknown canonical probability does not prevent saving personal input`() {
        val league = league()
        val relationship = relationship()
        account(league)
        select(league)

        assertThat(service.confirmForSelectedEnvironment(relationship.id, 0.13).model.probabilityPerAttempt)
            .isEqualTo(0.13)
    }

    @Test
    @WithMockUser(username = "test-admin", roles = ["ADMIN"])
    fun `admin cannot read or replace another player's personal override`() {
        val league = league()
        val relationship = relationship()
        val userAccount = account(league, owner = ConfiguredApplicationUsers.USER)
        select(league)
        val existing = DropRateOverride(
            DropRateOverrideRevisionId.generate(),
            DropRateOverrideKey(userAccount.id, relationship.id, selectedContext(league)),
            com.colinmoerbe.poecompanion.acquisition.probability.FixedBernoulliModel(0.13),
        )
        // Directly populated fixture: the admin's service must never resolve this other owner's context.
        insertOwnedFixture(existing)

        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isNull()
        assertThatThrownBy {
            service.removeForSelectedEnvironment(relationship.id, existing.revisionId)
        }.isInstanceOf(DropRateOverrideConflictException::class.java)

        selectableVersions.saveIfAbsent(league.id, version)
        val adminConfirmation = service.confirmForSelectedEnvironment(relationship.id, 0.11)
        assertThat(adminConfirmation.key.accountContextId).isNotEqualTo(userAccount.id)
        assertThat(service.findConfirmedForSelectedEnvironment(relationship.id)).isEqualTo(adminConfirmation)
        assertThat(repository.findById(existing.revisionId.value).orElseThrow().status.name)
            .isEqualTo("ACTIVE")
    }

    @Test
    fun `concurrent first confirmations allow exactly one active revision`() {
        val league = league()
        val relationship = relationship()
        account(league)
        select(league)
        val results = race(
            { service.confirmForSelectedEnvironment(relationship.id, 0.13) },
            { service.confirmForSelectedEnvironment(relationship.id, 0.11) },
        )
        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is DropRateOverrideConflictException })
            .isEqualTo(1)
        assertThat(repository.count()).isEqualTo(1L)
    }

    @Test
    fun `concurrent replacements must not lose either confirmed player decision silently`() {
        val league = league()
        val relationship = relationship()
        account(league)
        select(league)
        val original = service.confirmForSelectedEnvironment(relationship.id, 0.13)

        val results = race(
            { service.confirmForSelectedEnvironment(relationship.id, 0.11, original.revisionId) },
            { service.confirmForSelectedEnvironment(relationship.id, 0.15, original.revisionId) },
        )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is DropRateOverrideConflictException })
            .isEqualTo(1)
        assertThat(repository.count()).isEqualTo(2L)
        assertThat(repository.findById(original.revisionId.value).orElseThrow().status.name)
            .isEqualTo("SUPERSEDED")
    }

    private fun race(
        first: () -> Unit,
        second: () -> Unit,
    ): List<Result<Unit>> {
        val executor = DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2))
        val ready = CountDownLatch(2)
        val begin = CountDownLatch(1)
        return try {
            val actions = listOf(first, second).map { action ->
                executor.submit(
                    Callable {
                        ready.countDown()
                        check(begin.await(10, TimeUnit.SECONDS)) { "Concurrent test did not start" }
                        runCatching { action() }
                    },
                )
            }
            check(ready.await(10, TimeUnit.SECONDS)) { "Concurrent calls did not become ready" }
            begin.countDown()
            actions.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            begin.countDown()
            executor.shutdownNow()
        }
    }

    private fun selectedContext(league: LeagueDefinition): EvaluationContext =
        EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1), league.id)

    private fun select(
        league: LeagueDefinition,
        patch: GamePatch? = GamePatch(version, 1),
        compatibilityVersion: CompatibilityVersion = version,
    ) {
        selection.select(EvaluationContext(compatibilityVersion, Ruleset.NORMAL, patch, league.id))
    }

    private fun league(type: LeagueType = LeagueType.STANDARD): LeagueDefinition =
        LeagueDefinition(
            id = LeagueDefinitionId.generate(),
            name = "Override test ${UUID.randomUUID()}",
            type = type,
            participation = LeagueParticipation.SSF,
            mortality = LeagueMortality.HARDCORE,
            realm = GameRealm.PC,
        ).also(leagueAdapter::save)

    private fun account(
        league: LeagueDefinition,
        owner: ApplicationUserId = ConfiguredApplicationUsers.USER,
    ): AccountContext = AccountContext(
        id = AccountContextId.generate(),
        leagueDefinitionId = league.id,
        ruleset = Ruleset.NORMAL,
        ownerApplicationUserId = owner,
    ).also(accountAdapter::save)

    private fun relationship(): DropRelationship {
        val source = AcquisitionSource(AcquisitionSourceId.generate()).also(sourceAdapter::save)
        val unique = UniqueDefinition(UniqueDefinitionId.generate()).also(uniqueAdapter::save)
        return DropRelationship(DropRelationshipId.generate(), source.id, unique.id)
            .also(relationshipAdapter::save)
    }

    private fun insertOwnedFixture(override: DropRateOverride) {
        val key = override.key
        // Fixture bypasses service authorization to seed a different owner's stored revision.
        jdbc.update(
            """
            INSERT INTO poe_companion.drop_rate_override_revision (
                id, account_context_id, drop_relationship_id,
                compatibility_version_major, compatibility_version_minor,
                game_patch_number, game_patch_suffix, model_type,
                probability_per_attempt, status, confirmed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'FIXED_BERNOULLI', ?, 'ACTIVE', CURRENT_TIMESTAMP)
            """.trimIndent(),
            override.revisionId.value,
            key.accountContextId.value,
            key.dropRelationshipId.value,
            key.evaluationContext.compatibilityVersion.major,
            key.evaluationContext.compatibilityVersion.minor,
            key.evaluationContext.gamePatch?.patch,
            key.evaluationContext.gamePatch?.suffix,
            override.model.probabilityPerAttempt,
        )
    }
}
