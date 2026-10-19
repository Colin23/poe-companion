package com.colinmoerbe.poecompanion.acquisition.relationship

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSource
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.catalog.UniqueDefinition
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.catalog.persistence.UniqueDefinitionPersistenceAdapter
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.GameRealm
import com.colinmoerbe.poecompanion.league.LeagueDefinition
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.LeagueMortality
import com.colinmoerbe.poecompanion.league.LeagueParticipation
import com.colinmoerbe.poecompanion.league.LeagueType
import com.colinmoerbe.poecompanion.league.Ruleset
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
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Verifies accepted relationship facts, exact-scope consistency and conditional curator corrections in PostgreSQL.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class DropRelationshipKnowledgeServiceIntegrationTests(
    @Autowired private val service: DropRelationshipKnowledgeService,
    @Autowired private val knowledgeRepository: SpringDataDropRelationshipKnowledgeRepository,
    @Autowired private val relationshipRepository: SpringDataDropRelationshipRepository,
    @Autowired private val relationshipAdapter: DropRelationshipPersistenceAdapter,
    @Autowired private val sourceAdapter: AcquisitionSourcePersistenceAdapter,
    @Autowired private val uniqueAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val leagueAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {
    private val version = CompatibilityVersion(3, 30)
    private val normalScope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL)

    @BeforeEach
    fun cleanBeforeTest() {
        knowledgeRepository.deleteAllInBatch()
        relationshipRepository.deleteAllInBatch()
    }

    @AfterEach
    fun cleanAfterTest() {
        knowledgeRepository.deleteAllInBatch()
        relationshipRepository.deleteAllInBatch()
    }

    @Test
    fun `a relationship identity without authored knowledge should have no accepted timeline`() {
        val id = relationship().id

        assertThat(service.findTimeline(id, normalScope)).isNull()
        assertThat(service.findAcceptedChangePoints(id, normalScope)).isEmpty()
    }

    @Test
    fun `persisted baseline CAN_DROP should reconstruct the existing domain timeline`() {
        val id = relationship().id
        val baseline = snapshot()

        val revision = service.recordSnapshot(id, normalScope, baseline, null)

        assertThat(revision).isZero()
        assertThat(service.findAcceptedChangePoints(id, normalScope))
            .containsExactly(AcceptedDropRelationshipChangePoint(baseline, 0L))
        assertThat(service.findTimeline(id, normalScope)?.resolve(context()))
            .isEqualTo(DropRelationshipResolution.Resolved(baseline))
    }

    @Test
    fun `negative change and later restoration should preserve earlier game-patch truth`() {
        val id = relationship().id
        val baseline = snapshot()
        val removed = snapshot(GamePatch(version, 2), DropRelationshipState.CANNOT_DROP)
        val restored = snapshot(GamePatch(version, 4), DropRelationshipState.CAN_DROP)

        service.recordSnapshot(id, normalScope, removed, null)
        service.recordSnapshot(id, normalScope, restored, null)
        service.recordSnapshot(id, normalScope, baseline, null)

        val timeline = service.findTimeline(id, normalScope)
        assertThat(timeline?.snapshots).containsExactly(baseline, removed, restored)
        assertThat(timeline?.resolve(context(GamePatch(version, 1))))
            .isEqualTo(DropRelationshipResolution.Resolved(baseline))
        assertThat(timeline?.resolve(context(GamePatch(version, 3))))
            .isEqualTo(DropRelationshipResolution.Resolved(removed))
        assertThat(timeline?.resolve(context(GamePatch(version, 5))))
            .isEqualTo(DropRelationshipResolution.Resolved(restored))
        assertThat(timeline?.resolve(context())).isEqualTo(DropRelationshipResolution.Unknown)
    }

    @Test
    fun `an explicit baseline CANNOT_DROP must not become an unknown state`() {
        val id = relationship().id
        val rejected = snapshot(state = DropRelationshipState.CANNOT_DROP)
        service.recordSnapshot(id, normalScope, rejected, null)

        assertThat(service.findTimeline(id, normalScope)?.resolve(context()))
            .isEqualTo(DropRelationshipResolution.Resolved(rejected))
    }

    @Test
    fun `an earlier patch without baseline should resolve to unknown`() {
        val id = relationship().id
        service.recordSnapshot(id, normalScope, snapshot(GamePatch(version, 2)), null)

        assertThat(service.findTimeline(id, normalScope)?.resolve(context(GamePatch(version, 1))))
            .isEqualTo(DropRelationshipResolution.Unknown)
    }

    @Test
    fun `correcting one accepted change point should advance its revision without changing other patches`() {
        val id = relationship().id
        val baseline = snapshot()
        val later = snapshot(GamePatch(version, 2), DropRelationshipState.CANNOT_DROP)
        service.recordSnapshot(id, normalScope, baseline, null)
        service.recordSnapshot(id, normalScope, later, null)

        val newRevision =
            service.recordSnapshot(
                id,
                normalScope,
                snapshot(state = DropRelationshipState.CANNOT_DROP),
                expectedRevision = 0L,
            )

        assertThat(newRevision).isEqualTo(1L)
        assertThat(knowledgeRepository.count()).isEqualTo(2L)
        assertThat(service.findAcceptedChangePoints(id, normalScope))
            .containsExactlyInAnyOrder(
                AcceptedDropRelationshipChangePoint(snapshot(state = DropRelationshipState.CANNOT_DROP), 1L),
                AcceptedDropRelationshipChangePoint(later, 0L),
            )
        assertThat(service.findTimeline(id, normalScope)?.resolve(context(GamePatch(version, 3))))
            .isEqualTo(DropRelationshipResolution.Resolved(later))
    }

    @Test
    fun `stale expected revision should not overwrite a curator correction`() {
        val id = relationship().id
        val original = snapshot()
        service.recordSnapshot(id, normalScope, original, null)
        val corrected = snapshot(state = DropRelationshipState.CANNOT_DROP)
        service.recordSnapshot(id, normalScope, corrected, 0L)

        assertThatThrownBy { service.recordSnapshot(id, normalScope, original, 0L) }
            .isInstanceOf(DropRelationshipKnowledgeConflictException::class.java)

        assertThat(service.findAcceptedChangePoints(id, normalScope))
            .containsExactly(AcceptedDropRelationshipChangePoint(corrected, 1L))
    }

    @Test
    fun `repeated same-state correction should still advance the concurrency token`() {
        val id = relationship().id
        val original = snapshot()
        service.recordSnapshot(id, normalScope, original, null)

        assertThat(service.recordSnapshot(id, normalScope, original, 0L)).isEqualTo(1L)
        assertThat(service.recordSnapshot(id, normalScope, original, 1L)).isEqualTo(2L)
    }

    @Test
    fun `creating an occupied exact change point should conflict instead of overwriting`() {
        val id = relationship().id
        service.recordSnapshot(id, normalScope, snapshot(), null)

        assertThatThrownBy {
            service.recordSnapshot(id, normalScope, snapshot(state = DropRelationshipState.CANNOT_DROP), null)
        }.isInstanceOf(DropRelationshipKnowledgeConflictException::class.java)

        assertThat(service.findAcceptedChangePoints(id, normalScope))
            .containsExactly(AcceptedDropRelationshipChangePoint(snapshot(), 0L))
    }

    @Test
    fun `expecting a revision for a different patch must not alter the accepted fact`() {
        val id = relationship().id
        val baseline = snapshot()
        service.recordSnapshot(id, normalScope, baseline, null)

        assertThatThrownBy {
            service.recordSnapshot(id, normalScope, snapshot(GamePatch(version, 2)), 0L)
        }.isInstanceOf(DropRelationshipKnowledgeConflictException::class.java)

        assertThat(service.findAcceptedChangePoints(id, normalScope))
            .containsExactly(AcceptedDropRelationshipChangePoint(baseline, 0L))
    }

    @Test
    fun `compatibility version and ruleset should keep independent accepted knowledge`() {
        val id = relationship().id
        val ruthless = AcquisitionKnowledgeScope(version, Ruleset.RUTHLESS)
        val nextVersion = AcquisitionKnowledgeScope(CompatibilityVersion(3, 31), Ruleset.NORMAL)
        service.recordSnapshot(id, normalScope, snapshot(), null)
        service.recordSnapshot(id, ruthless, snapshot(state = DropRelationshipState.CANNOT_DROP), null)
        service.recordSnapshot(id, nextVersion, snapshot(), null)

        assertThat(service.findTimeline(id, normalScope)?.resolve(context()))
            .isEqualTo(DropRelationshipResolution.Resolved(snapshot()))
        assertThat(service.findTimeline(id, ruthless)?.resolve(EvaluationContext(version, Ruleset.RUTHLESS)))
            .isEqualTo(DropRelationshipResolution.Resolved(snapshot(state = DropRelationshipState.CANNOT_DROP)))
        assertThat(service.findTimeline(id, nextVersion)?.resolve(context()))
            .isEqualTo(DropRelationshipResolution.DoesNotApply)
    }

    @Test
    fun `league-independent and league-specific accepted knowledge should be separately stored`() {
        val id = relationship().id
        val league = league().id
        val specificScope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, league)
        service.recordSnapshot(id, normalScope, snapshot(), null)
        service.recordSnapshot(id, specificScope, snapshot(state = DropRelationshipState.CANNOT_DROP), null)

        assertThat(service.findTimeline(id, specificScope)?.resolve(context()))
            .isEqualTo(DropRelationshipResolution.Unknown)
        assertThat(
            service.findTimeline(id, specificScope)?.resolve(
                EvaluationContext(version, Ruleset.NORMAL, leagueDefinitionId = league),
            ),
        ).isEqualTo(DropRelationshipResolution.Resolved(snapshot(state = DropRelationshipState.CANNOT_DROP)))
        assertThat(service.findTimeline(id, normalScope)?.snapshots).containsExactly(snapshot())
    }

    @Test
    fun `no knowledge for an exact scope does not imply CANNOT_DROP`() {
        val id = relationship().id
        service.recordSnapshot(id, normalScope, snapshot(), null)

        assertThat(service.findTimeline(id, AcquisitionKnowledgeScope(version, Ruleset.RUTHLESS))).isNull()
    }

    @Test
    fun `invalid relationship and mismatched patch should be rejected before writing`() {
        assertThatThrownBy {
            service.recordSnapshot(DropRelationshipId.generate(), normalScope, snapshot(), null)
        }.isInstanceOf(IllegalArgumentException::class.java)

        val id = relationship().id
        assertThatThrownBy {
            service.recordSnapshot(id, normalScope, snapshot(GamePatch(CompatibilityVersion(3, 31), 2)), null)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            service.recordSnapshot(id, normalScope, snapshot(), -1L)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(knowledgeRepository.count()).isZero()
    }

    @Test
    fun `database should reject duplicate baseline facts and invalid source relationships`() {
        val id = relationship().id
        service.recordSnapshot(id, normalScope, snapshot(), null)

        assertThatThrownBy { insertRaw(id) }.isInstanceOf(DuplicateKeyException::class.java)
        assertThatThrownBy { insertRaw(DropRelationshipId.generate()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject duplicate exact league patch and invalid league references`() {
        val id = relationship().id
        val league = league().id
        val scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, league)
        service.recordSnapshot(id, scope, snapshot(GamePatch(version, 2, "a")), null)

        assertThatThrownBy { insertRaw(id, league, 2, "a") }
            .isInstanceOf(DuplicateKeyException::class.java)
        assertThatThrownBy { insertRaw(id, LeagueDefinitionId.generate()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject invalid authored states and partial patch suffixes`() {
        val id = relationship().id

        assertThatThrownBy { insertRaw(id, state = "UNKNOWN") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(id, suffix = "a") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `competing first writes must create exactly one accepted fact`() {
        val id = relationship().id
        val results =
            raceWrites(
                { service.recordSnapshot(id, normalScope, snapshot(), null) },
                { service.recordSnapshot(id, normalScope, snapshot(state = DropRelationshipState.CANNOT_DROP), null) },
            )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is DropRelationshipKnowledgeConflictException }).isEqualTo(1)
        assertThat(knowledgeRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `competing corrections must reject the stale expected revision`() {
        val id = relationship().id
        service.recordSnapshot(id, normalScope, snapshot(), null)
        val results =
            raceWrites(
                { service.recordSnapshot(id, normalScope, snapshot(), 0L) },
                { service.recordSnapshot(id, normalScope, snapshot(state = DropRelationshipState.CANNOT_DROP), 0L) },
            )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is DropRelationshipKnowledgeConflictException }).isEqualTo(1)
        assertThat(service.findAcceptedChangePoints(id, normalScope).single().revision).isEqualTo(1L)
        assertThat(knowledgeRepository.count()).isEqualTo(1L)
    }

    private fun context(patch: GamePatch? = null): EvaluationContext =
        EvaluationContext(version, Ruleset.NORMAL, gamePatch = patch)

    private fun snapshot(
        patch: GamePatch? = null,
        state: DropRelationshipState = DropRelationshipState.CAN_DROP,
    ): DropRelationshipSnapshot = DropRelationshipSnapshot(patch, state)

    private fun relationship(): DropRelationship {
        val source = AcquisitionSource(AcquisitionSourceId.generate())
        val unique = UniqueDefinition(UniqueDefinitionId.generate())
        sourceAdapter.save(source)
        uniqueAdapter.save(unique)
        return DropRelationship(DropRelationshipId.generate(), source.id, unique.id)
            .also(relationshipAdapter::save)
    }

    private fun league(): LeagueDefinition = LeagueDefinition(
        id = LeagueDefinitionId.generate(),
        name = "Test league",
        type = LeagueType.CHALLENGE,
        participation = LeagueParticipation.SSF,
        mortality = LeagueMortality.SOFTCORE,
        realm = GameRealm.PC,
    ).also(leagueAdapter::save)

    private fun insertRaw(
        relationshipId: DropRelationshipId,
        leagueId: LeagueDefinitionId? = null,
        patch: Int? = null,
        suffix: String? = null,
        state: String = "CAN_DROP",
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.drop_relationship_knowledge (
                id, drop_relationship_id, compatibility_version_major, compatibility_version_minor, ruleset,
                league_definition_id, effective_patch_number, effective_patch_suffix, state, revision
            ) VALUES (?, ?, 3, 30, 'NORMAL', ?, ?, ?, ?, 0)
            """.trimIndent(),
            UUID.randomUUID(),
            relationshipId.value,
            leagueId?.value,
            patch,
            suffix,
            state,
        )
    }

    private fun raceWrites(first: () -> Long, second: () -> Long): List<Result<Long>> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        return try {
            val tasks =
                listOf(first, second).map { operation ->
                    executor.submit(
                        Callable {
                            ready.countDown()
                            check(start.await(10, TimeUnit.SECONDS)) { "Timed out starting competing curators" }
                            runCatching(operation)
                        },
                    )
                }
            check(ready.await(10, TimeUnit.SECONDS)) { "Concurrent writers did not become ready" }
            start.countDown()
            tasks.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}
