package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.persistence.AcquisitionSourcePersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.encounter.CorruptBossEncounterKnowledgeException
import com.colinmoerbe.poecompanion.acquisition.encounter.persistence.BossEncounterKnowledgePersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.persistence.SpringDataBossDefinitionRepository
import com.colinmoerbe.poecompanion.acquisition.encounter.persistence.SpringDataBossEncounterKnowledgeRepository
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
 * Validates accepted complete encounter snapshots and atomic boss-roster correction in PostgreSQL.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "poe-companion.security.username=test-user",
        "poe-companion.security.password=test-password",
    ],
)
internal class BossEncounterKnowledgeServiceIntegrationTests(
    @Autowired private val service: BossEncounterKnowledgeService,
    @Autowired private val adapter: BossEncounterKnowledgePersistenceAdapter,
    @Autowired private val sourceAdapter: AcquisitionSourcePersistenceAdapter,
    @Autowired private val bossService: BossDefinitionService,
    @Autowired private val bossRepository: SpringDataBossDefinitionRepository,
    @Autowired private val knowledgeRepository: SpringDataBossEncounterKnowledgeRepository,
    @Autowired private val leagueAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val jdbc: JdbcTemplate,
) {
    private val version = CompatibilityVersion(3, 30)
    private val normalScope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL)

    @BeforeEach
    fun beforeTest() {
        cleanKnowledge()
        bossRepository.deleteAllInBatch()
    }

    @AfterEach
    fun afterTest() {
        cleanKnowledge()
        bossRepository.deleteAllInBatch()
    }

    @Test
    fun `source identity alone should not imply any accepted encounter knowledge`() {
        val source = source()

        assertThat(service.findTimeline(source, normalScope)).isNull()
        assertThat(service.findAcceptedChangePoints(source, normalScope)).isEmpty()
    }

    @Test
    fun `single boss snapshot should reconstruct into a resolved timeline`() {
        val source = source()
        val boss = boss("The Eater of Worlds")
        val original = snapshot(bossIds = setOf(boss))

        assertThat(service.recordSnapshot(source, normalScope, original, null)).isZero()
        assertThat(service.findAcceptedChangePoints(source, normalScope))
            .containsExactly(AcceptedBossEncounterChangePoint(original, 0L))
        assertThat(service.findTimeline(source, normalScope)?.resolve(context()))
            .isEqualTo(BossEncounterKnowledgeResolution.Resolved(original))
        assertThat(participantCount()).isEqualTo(1)
    }

    @Test
    fun `one source can have multiple fixed participating bosses`() {
        val source = source()
        val elder = boss("The Elder")
        val shaper = boss("The Shaper")
        val original = snapshot(bossIds = setOf(elder, shaper))

        service.recordSnapshot(source, normalScope, original, null)

        assertThat(service.findTimeline(source, normalScope)?.snapshots).containsExactly(original)
        assertThat(participantCount()).isEqualTo(2)
    }

    @Test
    fun `configuration dependent roster should persist without fabricated fixed participants`() {
        val source = source()
        val original = snapshot(participation = BossParticipation.ConfigurationDependent)

        service.recordSnapshot(source, normalScope, original, null)

        assertThat(service.findAcceptedChangePoints(source, normalScope))
            .containsExactly(AcceptedBossEncounterChangePoint(original, 0L))
        assertThat(participantCount()).isZero()
    }

    @Test
    fun `availability change points should preserve historical states and restore availability`() {
        val source = source()
        val boss = boss("The Elder")
        val baseline = snapshot(bossIds = setOf(boss))
        val unavailable = snapshot(GamePatch(version, 2), setOf(boss), AcquisitionSourceAvailability.UNAVAILABLE)
        val restored = snapshot(GamePatch(version, 4), setOf(boss))
        service.recordSnapshot(source, normalScope, restored, null)
        service.recordSnapshot(source, normalScope, unavailable, null)
        service.recordSnapshot(source, normalScope, baseline, null)

        val timeline = service.findTimeline(source, normalScope)
        assertThat(timeline?.snapshots).containsExactly(baseline, unavailable, restored)
        assertThat(timeline?.resolve(context(GamePatch(version, 1))))
            .isEqualTo(BossEncounterKnowledgeResolution.Resolved(baseline))
        assertThat(timeline?.resolve(context(GamePatch(version, 3))))
            .isEqualTo(BossEncounterKnowledgeResolution.Resolved(unavailable))
        assertThat(timeline?.resolve(context(GamePatch(version, 5))))
            .isEqualTo(BossEncounterKnowledgeResolution.Resolved(restored))
        assertThat(timeline?.resolve(context())).isEqualTo(BossEncounterKnowledgeResolution.Unknown)
    }

    @Test
    fun `patchless and earlier context should remain unknown when no baseline exists`() {
        val source = source()
        val boss = boss("The Elder")
        val changed = snapshot(GamePatch(version, 2), setOf(boss))

        service.recordSnapshot(source, normalScope, changed, null)

        assertThat(service.findTimeline(source, normalScope)?.resolve(context()))
            .isEqualTo(BossEncounterKnowledgeResolution.Unknown)
        assertThat(service.findTimeline(source, normalScope)?.resolve(context(GamePatch(version, 1))))
            .isEqualTo(BossEncounterKnowledgeResolution.Unknown)
    }

    @Test
    fun `patch suffixes should be preserved and resolved in domain ordering`() {
        val source = source()
        val boss = boss("The Elder")
        val initial = snapshot(GamePatch(version, 2), setOf(boss), label = "Initial")
        val suffix = snapshot(GamePatch(version, 2, "a"), setOf(boss), label = "Changed")
        service.recordSnapshot(source, normalScope, suffix, null)
        service.recordSnapshot(source, normalScope, initial, null)

        assertThat(service.findTimeline(source, normalScope)?.snapshots).containsExactly(initial, suffix)
        assertThat(service.findTimeline(source, normalScope)?.resolve(context(GamePatch(version, 2))))
            .isEqualTo(BossEncounterKnowledgeResolution.Resolved(initial))
        assertThat(service.findTimeline(source, normalScope)?.resolve(context(GamePatch(version, 2, "b"))))
            .isEqualTo(BossEncounterKnowledgeResolution.Resolved(suffix))
    }

    @Test
    fun `rulesets and compatibility versions should have independent knowledge`() {
        val source = source()
        val boss = boss("The Elder")
        val normal = snapshot(bossIds = setOf(boss))
        val ruthless = snapshot(bossIds = setOf(boss), availability = AcquisitionSourceAvailability.UNAVAILABLE)
        val later = AcquisitionKnowledgeScope(CompatibilityVersion(3, 31), Ruleset.NORMAL)
        val ruthlessScope = AcquisitionKnowledgeScope(version, Ruleset.RUTHLESS)
        service.recordSnapshot(source, normalScope, normal, null)
        service.recordSnapshot(source, ruthlessScope, ruthless, null)
        service.recordSnapshot(source, later, normal, null)

        assertThat(service.findTimeline(source, normalScope)?.resolve(context()))
            .isEqualTo(BossEncounterKnowledgeResolution.Resolved(normal))
        assertThat(
            service.findTimeline(source, ruthlessScope)?.resolve(
                EvaluationContext(version, Ruleset.RUTHLESS),
            ),
        ).isEqualTo(BossEncounterKnowledgeResolution.Resolved(ruthless))
        assertThat(service.findTimeline(source, later)?.resolve(context()))
            .isEqualTo(BossEncounterKnowledgeResolution.DoesNotApply)
        assertThat(service.findAcceptedChangePoints(source, normalScope)).hasSize(1)
    }

    @Test
    fun `league independent and league constrained snapshots should not overwrite each other`() {
        val source = source()
        val boss = boss("The Elder")
        val league = league()
        val specific = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, league)
        val general = snapshot(bossIds = setOf(boss))
        val specificSnapshot = snapshot(
            bossIds = setOf(boss),
            availability = AcquisitionSourceAvailability.UNAVAILABLE,
        )
        service.recordSnapshot(source, normalScope, general, null)
        service.recordSnapshot(source, specific, specificSnapshot, null)

        assertThat(service.findAcceptedChangePoints(source, normalScope))
            .containsExactly(AcceptedBossEncounterChangePoint(general, 0L))
        assertThat(service.findTimeline(source, specific)?.resolve(context()))
            .isEqualTo(BossEncounterKnowledgeResolution.Unknown)
        assertThat(
            service.findTimeline(source, specific)?.resolve(
                EvaluationContext(version, Ruleset.NORMAL, leagueDefinitionId = league),
            ),
        ).isEqualTo(BossEncounterKnowledgeResolution.Resolved(specificSnapshot))
    }

    @Test
    fun `a complete fixed roster correction should replace only its own accepted participants`() {
        val source = source()
        val elder = boss("The Elder")
        val shaper = boss("The Shaper")
        val baseline = snapshot(bossIds = setOf(elder, shaper))
        val later = snapshot(GamePatch(version, 2), setOf(shaper))
        service.recordSnapshot(source, normalScope, baseline, null)
        service.recordSnapshot(source, normalScope, later, null)

        val corrected = snapshot(bossIds = setOf(elder), label = "Corrected Uber Elder")
        assertThat(service.recordSnapshot(source, normalScope, corrected, 0L)).isEqualTo(1L)
        assertThat(knowledgeRepository.count()).isEqualTo(2L)
        assertThat(service.findAcceptedChangePoints(source, normalScope))
            .containsExactlyInAnyOrder(
                AcceptedBossEncounterChangePoint(corrected, 1L),
                AcceptedBossEncounterChangePoint(later, 0L),
            )
        assertThat(participantCount()).isEqualTo(2)
    }

    @Test
    fun `fixed to configuration dependent and back must not retain old participants`() {
        val source = source()
        val elder = boss("The Elder")
        val shaper = boss("The Shaper")
        service.recordSnapshot(source, normalScope, snapshot(bossIds = setOf(elder, shaper)), null)

        val dependent = snapshot(participation = BossParticipation.ConfigurationDependent)
        assertThat(service.recordSnapshot(source, normalScope, dependent, 0L)).isEqualTo(1L)
        assertThat(participantCount()).isZero()
        assertThat(service.findTimeline(source, normalScope)?.snapshots).containsExactly(dependent)

        val fixed = snapshot(bossIds = setOf(shaper), availability = AcquisitionSourceAvailability.UNAVAILABLE)
        assertThat(service.recordSnapshot(source, normalScope, fixed, 1L)).isEqualTo(2L)
        assertThat(service.findAcceptedChangePoints(source, normalScope))
            .containsExactly(AcceptedBossEncounterChangePoint(fixed, 2L))
        assertThat(participantCount()).isEqualTo(1)
    }

    @Test
    fun `stale correction must not change the accepted fact or participant set`() {
        val source = source()
        val elder = boss("The Elder")
        val shaper = boss("The Shaper")
        val initial = snapshot(bossIds = setOf(elder))
        val updated = snapshot(bossIds = setOf(shaper))
        service.recordSnapshot(source, normalScope, initial, null)
        service.recordSnapshot(source, normalScope, updated, 0L)

        assertThatThrownBy { service.recordSnapshot(source, normalScope, initial, 0L) }
            .isInstanceOf(BossEncounterKnowledgeConflictException::class.java)
        assertThat(service.findAcceptedChangePoints(source, normalScope))
            .containsExactly(AcceptedBossEncounterChangePoint(updated, 1L))
        assertThat(participantCount()).isEqualTo(1)
    }

    @Test
    fun `failed participant insertion should roll back parent correction and its revision`() {
        val source = source()
        val elder = boss("The Elder")
        val original = snapshot(bossIds = setOf(elder))
        service.recordSnapshot(source, normalScope, original, null)

        val corruptRequest = snapshot(bossIds = setOf(BossDefinitionId.generate()))
        assertThatThrownBy {
            adapter.recordSnapshot(source, normalScope, corruptRequest, 0L)
        }.isInstanceOf(DataIntegrityViolationException::class.java)

        assertThat(service.findAcceptedChangePoints(source, normalScope))
            .containsExactly(AcceptedBossEncounterChangePoint(original, 0L))
        assertThat(participantCount()).isEqualTo(1)
    }

    @Test
    fun `duplicate change points and stale revisions should yield curator conflicts`() {
        val source = source()
        val boss = boss("The Elder")
        val initial = snapshot(bossIds = setOf(boss))
        service.recordSnapshot(source, normalScope, initial, null)

        assertThatThrownBy { service.recordSnapshot(source, normalScope, initial, null) }
            .isInstanceOf(BossEncounterKnowledgeConflictException::class.java)
        assertThatThrownBy {
            service.recordSnapshot(source, normalScope, snapshot(GamePatch(version, 2), setOf(boss)), 0L)
        }.isInstanceOf(BossEncounterKnowledgeConflictException::class.java)
        assertThat(knowledgeRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `unknown source unknown boss and incompatible patch should be rejected before writing`() {
        val source = source()
        val boss = boss("The Elder")
        assertThatThrownBy {
            service.recordSnapshot(AcquisitionSourceId.generate(), normalScope, snapshot(bossIds = setOf(boss)), null)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            service.recordSnapshot(source, normalScope, snapshot(bossIds = setOf(BossDefinitionId.generate())), null)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            service.recordSnapshot(source, normalScope, snapshot(GamePatch(CompatibilityVersion(3, 31), 2)), null)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            service.recordSnapshot(source, normalScope, snapshot(bossIds = setOf(boss)), -1L)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(knowledgeRepository.count()).isZero()
    }

    @Test
    fun `database must enforce source and league foreign keys and allowed authoring values`() {
        val source = source()
        assertThatThrownBy { insertRaw(AcquisitionSourceId.generate().value) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(source.value, leagueId = LeagueDefinitionId.generate().value) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(source.value, availability = "UNKNOWN") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(source.value, kind = "INVALID") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(source.value, label = "\t\n") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database must enforce unique baseline and participant composite keys`() {
        val source = source()
        val boss = boss("The Elder")
        service.recordSnapshot(source, normalScope, snapshot(bossIds = setOf(boss)), null)

        assertThatThrownBy { insertRaw(source.value) }.isInstanceOf(DuplicateKeyException::class.java)
        val id = knowledgeRepository.findAll().single().id
        assertThatThrownBy { insertParticipant(id, boss.value) }.isInstanceOf(DuplicateKeyException::class.java)
        assertThatThrownBy { insertParticipant(id, BossDefinitionId.generate().value) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `reconstruction must reject inconsistent persisted fixed or configuration dependent rosters`() {
        val source = source()
        val id = UUID.randomUUID()
        insertRaw(source.value, id = id)
        assertThatThrownBy { service.findTimeline(source, normalScope) }
            .isInstanceOf(CorruptBossEncounterKnowledgeException::class.java)

        jdbc.update("DELETE FROM poe_companion.boss_encounter_knowledge WHERE id = ?", id)
        val secondId = UUID.randomUUID()
        insertRaw(source.value, id = secondId, kind = "CONFIGURATION_DEPENDENT")
        insertParticipant(secondId, boss("The Elder").value)
        assertThatThrownBy { service.findTimeline(source, normalScope) }
            .isInstanceOf(CorruptBossEncounterKnowledgeException::class.java)
    }

    @Test
    fun `concurrent first publications must create one accepted snapshot`() {
        val source = source()
        val elder = boss("The Elder")
        val shaper = boss("The Shaper")
        val results = raceWrites(
            { service.recordSnapshot(source, normalScope, snapshot(bossIds = setOf(elder)), null) },
            { service.recordSnapshot(source, normalScope, snapshot(bossIds = setOf(shaper)), null) },
        )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is BossEncounterKnowledgeConflictException }).isEqualTo(1)
        assertThat(knowledgeRepository.count()).isEqualTo(1L)
        assertThat(participantCount()).isEqualTo(1)
    }

    @Test
    fun `competing curator corrections must not interleave boss rosters`() {
        val source = source()
        val elder = boss("The Elder")
        val shaper = boss("The Shaper")
        service.recordSnapshot(source, normalScope, snapshot(bossIds = setOf(elder)), null)
        val results = raceWrites(
            { service.recordSnapshot(source, normalScope, snapshot(bossIds = setOf(elder, shaper)), 0L) },
            { service.recordSnapshot(source, normalScope, snapshot(bossIds = setOf(shaper)), 0L) },
        )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is BossEncounterKnowledgeConflictException }).isEqualTo(1)
        assertThat(service.findAcceptedChangePoints(source, normalScope).single().revision).isEqualTo(1L)
        val acceptedRoster =
            service.findAcceptedChangePoints(source, normalScope).single().snapshot.bossParticipation
        assertThat(acceptedRoster).isIn(
            BossParticipation.Fixed(setOf(elder, shaper)),
            BossParticipation.Fixed(setOf(shaper)),
        )
    }

    private fun cleanKnowledge() {
        jdbc.update("DELETE FROM poe_companion.boss_encounter_participant")
        knowledgeRepository.deleteAllInBatch()
    }

    private fun source(): AcquisitionSourceId = AcquisitionSourceId.generate().also {
        sourceAdapter.save(AcquisitionSource(it))
    }

    private fun boss(name: String): BossDefinitionId = BossDefinitionId.generate().also {
        bossService.create(BossDefinition(it, name))
    }

    private fun league(): LeagueDefinitionId {
        val definition = LeagueDefinition(
            id = LeagueDefinitionId.generate(),
            name = "B2 Test League",
            type = LeagueType.CHALLENGE,
            participation = LeagueParticipation.SSF,
            mortality = LeagueMortality.SOFTCORE,
            realm = GameRealm.PC,
        )
        leagueAdapter.save(definition)
        return definition.id
    }

    private fun context(patch: GamePatch? = null): EvaluationContext =
        EvaluationContext(version, Ruleset.NORMAL, gamePatch = patch)

    private fun snapshot(
        patch: GamePatch? = null,
        bossIds: Set<BossDefinitionId> = emptySet(),
        availability: AcquisitionSourceAvailability = AcquisitionSourceAvailability.AVAILABLE,
        label: String = "Uber Elder",
        participation: BossParticipation = defaultParticipation(bossIds),
    ): BossEncounterKnowledgeSnapshot = BossEncounterKnowledgeSnapshot(patch, label, availability, participation)

    private fun defaultParticipation(ids: Set<BossDefinitionId>): BossParticipation =
        if (ids.isEmpty()) BossParticipation.ConfigurationDependent else BossParticipation.Fixed(ids)

    private fun participantCount(): Int = jdbc.queryForObject(
        "SELECT count(*) FROM poe_companion.boss_encounter_participant",
        Int::class.java,
    )!!

    private fun insertParticipant(knowledgeId: UUID, bossId: UUID) {
        jdbc.update(
            "INSERT INTO poe_companion.boss_encounter_participant (knowledge_id, boss_definition_id) VALUES (?, ?)",
            knowledgeId,
            bossId,
        )
    }

    private fun insertRaw(
        sourceId: UUID,
        leagueId: UUID? = null,
        availability: String = "AVAILABLE",
        kind: String = "FIXED",
        label: String = "Uber Elder",
        id: UUID = UUID.randomUUID(),
    ) {
        jdbc.update(
            """
            INSERT INTO poe_companion.boss_encounter_knowledge (
                id, acquisition_source_id, compatibility_version_major, compatibility_version_minor,
                ruleset, league_definition_id, display_label, availability, participation_kind, revision
            ) VALUES (?, ?, 3, 30, 'NORMAL', ?, ?, ?, ?, 0)
            """.trimIndent(),
            id,
            sourceId,
            leagueId,
            label,
            availability,
            kind,
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
                            check(start.await(10, TimeUnit.SECONDS)) { "Curator race did not start" }
                            runCatching(operation)
                        },
                    )
                }
            check(ready.await(10, TimeUnit.SECONDS)) { "Curator workers did not become ready" }
            start.countDown()
            tasks.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}
