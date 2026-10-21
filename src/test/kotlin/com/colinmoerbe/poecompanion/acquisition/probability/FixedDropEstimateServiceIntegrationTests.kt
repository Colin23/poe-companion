package com.colinmoerbe.poecompanion.acquisition.probability

import com.colinmoerbe.poecompanion.TestcontainersConfiguration
import com.colinmoerbe.poecompanion.acquisition.attempts.CompletedBossEncounterAttempt
import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.probability.persistence.SpringDataFixedDropEstimateRepository
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationship
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.DropRelationshipPersistenceAdapter
import com.colinmoerbe.poecompanion.acquisition.relationship.persistence.SpringDataDropRelationshipRepository
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
import org.springframework.dao.InvalidDataAccessApiUsageException
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * PostgreSQL-backed publication contract, including concurrent curator operations and exact applicability.
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
@WithMockUser(username = "test-admin", roles = ["ADMIN"])
internal class FixedDropEstimateServiceIntegrationTests(
    @Autowired private val service: FixedDropEstimateService,
    @Autowired private val estimateRepository: SpringDataFixedDropEstimateRepository,
    @Autowired private val relationshipRepository: SpringDataDropRelationshipRepository,
    @Autowired private val relationshipAdapter: DropRelationshipPersistenceAdapter,
    @Autowired private val sourceAdapter: AcquisitionSourcePersistenceAdapter,
    @Autowired private val uniqueAdapter: UniqueDefinitionPersistenceAdapter,
    @Autowired private val leagueAdapter: LeagueDefinitionPersistenceAdapter,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) {
    private val version = CompatibilityVersion(3, 30)
    private val normalScope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL)
    private val reviewedAt = Instant.parse("2026-10-01T12:00:00Z")

    @BeforeEach
    fun cleanBeforeTest() {
        estimateRepository.deleteAllInBatch()
        relationshipRepository.deleteAllInBatch()
    }

    @AfterEach
    fun cleanAfterTest() {
        estimateRepository.deleteAllInBatch()
        relationshipRepository.deleteAllInBatch()
    }

    @Test
    fun `first publication should round trip accepted estimate and source-defined attempt`() {
        val relationship = relationship()
        val estimate = snapshot(probability = 0.10)

        service.publish(relationship.id, normalScope, estimate, null)

        val timeline = service.findAcceptedTimeline(relationship.id, normalScope)
        assertThat(timeline?.dropRelationship?.id).isEqualTo(relationship.id)
        assertThat(timeline?.attemptDefinition).isEqualTo(
            CompletedBossEncounterAttempt(relationship.acquisitionSourceId),
        )
        assertThat(timeline?.snapshots).containsExactly(estimate)
        assertThat(timeline?.resolve(EvaluationContext(version, Ruleset.NORMAL)))
            .isEqualTo(FixedDropEstimateResolution.Resolved(estimate))
        assertThat(estimateRepository.findById(estimate.id.value).orElseThrow().publishedAt).isNotNull()
    }

    @Test
    fun `missing accepted estimate should remain absent rather than zero probability`() {
        val relationship = relationship()

        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)).isNull()
    }

    @Test
    fun `correcting the same game-patch change point should retain old evidence as superseded`() {
        val relationship = relationship()
        val previous = snapshot(probability = 0.08, evidenceReference = "research-old")
        val replacement = snapshot(probability = 0.09, evidenceReference = "research-new")
        service.publish(relationship.id, normalScope, previous, null)

        service.publish(relationship.id, normalScope, replacement, FixedProbabilityPublicationId.Estimate(previous.id))

        val timeline = service.findAcceptedTimeline(relationship.id, normalScope)
        assertThat(timeline?.snapshots).containsExactly(replacement)
        assertThat(estimateRepository.findById(previous.id.value).orElseThrow().status.name)
            .isEqualTo("SUPERSEDED")
        assertThat(estimateRepository.findById(previous.id.value).orElseThrow().evidenceReference)
            .isEqualTo("research-old")
        assertThat(estimateRepository.findById(previous.id.value).orElseThrow().supersededAt).isNotNull()
        assertThat(estimateRepository.findById(replacement.id.value).orElseThrow().status.name)
            .isEqualTo("ACTIVE")
        assertThat(estimateRepository.count()).isEqualTo(2L)
    }

    @Test
    fun `different game-patch changes should both remain accepted historical game knowledge`() {
        val relationship = relationship()
        val baseline = snapshot(probability = 0.10)
        val changed = snapshot(probability = 0.08, patch = GamePatch(version, 2))

        service.publish(relationship.id, normalScope, changed, null)
        service.publish(relationship.id, normalScope, baseline, null)

        val timeline = service.findAcceptedTimeline(relationship.id, normalScope)
        assertThat(timeline?.snapshots).containsExactly(baseline, changed)
        assertThat(timeline?.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1))))
            .isEqualTo(FixedDropEstimateResolution.Resolved(baseline))
        assertThat(timeline?.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 3))))
            .isEqualTo(FixedDropEstimateResolution.Resolved(changed))
        assertThat(timeline?.resolve(EvaluationContext(version, Ruleset.NORMAL)))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
    }

    @Test
    fun `compatibility version and ruleset should isolate accepted timelines`() {
        val relationship = relationship()
        val ruthlessScope = AcquisitionKnowledgeScope(version, Ruleset.RUTHLESS)
        val nextVersionScope = AcquisitionKnowledgeScope(CompatibilityVersion(3, 31), Ruleset.NORMAL)

        service.publish(relationship.id, normalScope, snapshot(probability = 0.10), null)
        service.publish(relationship.id, ruthlessScope, snapshot(probability = 0.05), null)
        service.publish(relationship.id, nextVersionScope, snapshot(probability = 0.09), null)

        assertThat(acceptedProbability(relationship.id, normalScope)).isEqualTo(0.10)
        assertThat(acceptedProbability(relationship.id, ruthlessScope)).isEqualTo(0.05)
        assertThat(acceptedProbability(relationship.id, nextVersionScope)).isEqualTo(0.09)
    }

    @Test
    fun `broad and league-specific scopes should be stored independently without automatic precedence`() {
        val relationship = relationship()
        val leagueScope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, league().id)
        service.publish(relationship.id, normalScope, snapshot(probability = 0.10), null)
        service.publish(relationship.id, leagueScope, snapshot(probability = 0.12), null)

        assertThat(acceptedProbability(relationship.id, normalScope)).isEqualTo(0.10)
        assertThat(acceptedProbability(relationship.id, leagueScope)).isEqualTo(0.12)
    }

    @Test
    fun `incorrect expected ID should reject correction without affecting original estimate`() {
        val relationship = relationship()
        val original = snapshot()
        service.publish(relationship.id, normalScope, original, null)

        assertThatThrownBy {
            service.publish(
                relationship.id,
                normalScope,
                snapshot(probability = 0.25),
                FixedProbabilityPublicationId.Estimate(DropEstimateId.generate()),
            )
        }.isInstanceOf(FixedDropEstimatePublicationConflictException::class.java)

        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.snapshots).containsExactly(original)
        assertThat(estimateRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `expected ID from another change point must not supersede its active record`() {
        val relationship = relationship()
        val baseline = snapshot()
        service.publish(relationship.id, normalScope, baseline, null)

        assertThatThrownBy {
            service.publish(
                relationship.id,
                normalScope,
                snapshot(patch = GamePatch(version, 2)),
                FixedProbabilityPublicationId.Estimate(baseline.id),
            )
        }.isInstanceOf(FixedDropEstimatePublicationConflictException::class.java)

        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.snapshots).containsExactly(baseline)
    }

    @Test
    fun `a new estimate record must never reuse an existing or replaced identity`() {
        val relationship = relationship()
        val first = snapshot()
        val second = snapshot(probability = 0.20)
        service.publish(relationship.id, normalScope, first, null)
        service.publish(relationship.id, normalScope, second, FixedProbabilityPublicationId.Estimate(first.id))

        assertThatThrownBy {
            service.publish(
                relationship.id,
                normalScope,
                snapshot(id = first.id, probability = 0.15),
                FixedProbabilityPublicationId.Estimate(second.id),
            )
        }.isInstanceOf(InvalidDataAccessApiUsageException::class.java)
            .hasCauseInstanceOf(IllegalArgumentException::class.java)

        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.snapshots).containsExactly(second)
    }

    @Test
    fun `publishing against an unknown relationship should be rejected`() {
        assertThatThrownBy {
            service.publish(DropRelationshipId.generate(), normalScope, snapshot(), null)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `snapshot effective patch must belong to the chosen compatibility version`() {
        val relationship = relationship()

        assertThatThrownBy {
            service.publish(
                relationship.id,
                normalScope,
                snapshot(patch = GamePatch(CompatibilityVersion(3, 31), 1)),
                null,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(estimateRepository.count()).isZero()
    }

    @Test
    fun `database should prevent duplicate ACTIVE baseline with null league and null patch`() {
        val relationship = relationship()
        service.publish(relationship.id, normalScope, snapshot(), null)

        assertThatThrownBy {
            insertRaw(relationship.id)
        }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `database should prevent duplicate ACTIVE specific patch and league`() {
        val relationship = relationship()
        val leagueId = league().id
        val scoped = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, leagueId)
        service.publish(relationship.id, scoped, snapshot(patch = GamePatch(version, 2)), null)

        assertThatThrownBy {
            insertRaw(relationship.id, leagueId = leagueId, patchNumber = 2)
        }.isInstanceOf(DuplicateKeyException::class.java)
    }

    @Test
    fun `database should reject invalid probability and provenance`() {
        val relationship = relationship()

        assertThatThrownBy { insertRaw(relationship.id, probability = 0.0) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(relationship.id, probability = 1.2) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(relationship.id, provenance = "UNKNOWN") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject invalid foreign keys and patch suffix`() {
        val relationship = relationship()

        assertThatThrownBy { insertRaw(DropRelationshipId.generate()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(relationship.id, leagueId = LeagueDefinitionId.generate()) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRaw(relationship.id, patchSuffix = "a") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `database should reject whitespace-only evidence`() {
        val relationship = relationship()

        assertThatThrownBy { insertRaw(relationship.id, evidenceReference = "   ") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
    }

    @Test
    fun `competing first publications must yield one success and one explicit conflict`() {
        val relationship = relationship()
        val candidates = listOf(snapshot(probability = 0.10), snapshot(probability = 0.20))

        val results =
            racePublications(
                {
                    service.publish(relationship.id, normalScope, candidates[0], null)
                },
                {
                    service.publish(relationship.id, normalScope, candidates[1], null)
                },
            )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is FixedDropEstimatePublicationConflictException })
            .isEqualTo(1)
        assertThat(estimateRepository.count()).isEqualTo(1L)
        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.snapshots).hasSize(1)
    }

    @Test
    fun `competing corrections must reject the stale expected active estimate`() {
        val relationship = relationship()
        val initial = snapshot()
        service.publish(relationship.id, normalScope, initial, null)
        val candidates = listOf(snapshot(probability = 0.11), snapshot(probability = 0.12))

        val results =
            racePublications(
                {
                    service.publish(
                        relationship.id,
                        normalScope,
                        candidates[0],
                        FixedProbabilityPublicationId.Estimate(initial.id),
                    )
                },
                {
                    service.publish(
                        relationship.id,
                        normalScope,
                        candidates[1],
                        FixedProbabilityPublicationId.Estimate(initial.id),
                    )
                },
            )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is FixedDropEstimatePublicationConflictException })
            .isEqualTo(1)
        assertThat(estimateRepository.count()).isEqualTo(2L)
        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.snapshots).hasSize(1)
        assertThat(estimateRepository.findById(initial.id.value).orElseThrow().status.name).isEqualTo("SUPERSEDED")
    }

    @Test
    fun `accepted withdrawal must stop baseline carry forward until a later estimate`() {
        val relationship = relationship()
        val baseline = snapshot(probability = 0.10)
        val invalidation = withdrawal(patch = GamePatch(version, 2))
        val restored = snapshot(probability = 0.08, patch = GamePatch(version, 4))

        service.publish(relationship.id, normalScope, baseline, null)
        service.withdraw(relationship.id, normalScope, invalidation, null)
        service.publish(relationship.id, normalScope, restored, null)

        val timeline = service.findAcceptedTimeline(relationship.id, normalScope)
        assertThat(timeline?.snapshots).containsExactly(baseline, restored)
        assertThat(timeline?.withdrawals).containsExactly(invalidation)
        assertThat(timeline?.changePoints).containsExactly(
            FixedProbabilityChangePoint.Known(baseline),
            FixedProbabilityChangePoint.Withdrawn(invalidation),
            FixedProbabilityChangePoint.Known(restored),
        )
        assertThat(timeline?.resolve(context(GamePatch(version, 1))))
            .isEqualTo(FixedDropEstimateResolution.Resolved(baseline))
        assertThat(timeline?.resolve(context(GamePatch(version, 2))))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(timeline?.resolve(context(GamePatch(version, 3))))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(timeline?.resolve(context(GamePatch(version, 5))))
            .isEqualTo(FixedDropEstimateResolution.Resolved(restored))
        assertThat(timeline?.resolve(context())).isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(estimateRepository.count()).isEqualTo(3L)
    }

    @Test
    fun `withdrawal can be the only accepted baseline knowledge`() {
        val relationship = relationship()
        val invalidation = withdrawal()

        service.withdraw(relationship.id, normalScope, invalidation, null)

        val timeline = service.findAcceptedTimeline(relationship.id, normalScope)
        assertThat(timeline?.snapshots).isEmpty()
        assertThat(timeline?.withdrawals).containsExactly(invalidation)
        assertThat(timeline?.resolve(context())).isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(estimateRepository.findById(invalidation.id.value).orElseThrow().knowledgeKind.name)
            .isEqualTo("WITHDRAWAL")
    }

    @Test
    fun `correcting a baseline estimate into withdrawal retains original immutable evidence`() {
        val relationship = relationship()
        val original = snapshot(probability = 0.10, evidenceReference = "earlier-study")
        val invalidation = withdrawal(evidenceReference = "mechanics-correction")
        service.publish(relationship.id, normalScope, original, null)

        service.withdraw(
            relationship.id,
            normalScope,
            invalidation,
            FixedProbabilityPublicationId.Estimate(original.id),
        )

        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.resolve(context()))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.snapshots).isEmpty()
        val old = estimateRepository.findById(original.id.value).orElseThrow()
        assertThat(old.status.name).isEqualTo("SUPERSEDED")
        assertThat(old.evidenceReference).isEqualTo("earlier-study")
        assertThat(old.probabilityPerAttempt).isEqualTo(0.10)
        assertThat(old.supersededAt).isNotNull()
    }

    @Test
    fun `a fresh estimate can replace accepted withdrawal without deleting the withdrawal evidence`() {
        val relationship = relationship()
        val invalidation = withdrawal()
        val replacement = snapshot(probability = 0.08)
        service.withdraw(relationship.id, normalScope, invalidation, null)

        service.publish(
            relationship.id,
            normalScope,
            replacement,
            FixedProbabilityPublicationId.Withdrawal(invalidation.id),
        )

        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.snapshots).containsExactly(replacement)
        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.withdrawals).isEmpty()
        assertThat(estimateRepository.findById(invalidation.id.value).orElseThrow().status.name)
            .isEqualTo("SUPERSEDED")
        assertThat(estimateRepository.findById(invalidation.id.value).orElseThrow().withdrawalReason)
            .isEqualTo("Old probability no longer applies")
    }

    @Test
    fun `withdrawal validation must reject missing reason evidence and wrong patch version`() {
        val relationship = relationship()
        assertThatThrownBy { withdrawal(reason = " \t ") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { withdrawal(evidenceReference = " \t ") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            service.withdraw(
                relationship.id,
                normalScope,
                withdrawal(patch = GamePatch(CompatibilityVersion(3, 31), 2)),
                null,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            service.withdraw(DropRelationshipId.generate(), normalScope, withdrawal(), null)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(estimateRepository.count()).isZero()
    }

    @Test
    fun `wrong expected publication kind and stale ID cannot supersede a withdrawal`() {
        val relationship = relationship()
        val invalidation = withdrawal()
        service.withdraw(relationship.id, normalScope, invalidation, null)

        assertThatThrownBy {
            service.publish(
                relationship.id,
                normalScope,
                snapshot(),
                FixedProbabilityPublicationId.Estimate(DropEstimateId(invalidation.id.value)),
            )
        }.isInstanceOf(FixedDropEstimatePublicationConflictException::class.java)
        assertThatThrownBy {
            service.withdraw(
                relationship.id,
                normalScope,
                withdrawal(),
                FixedProbabilityPublicationId.Withdrawal(FixedProbabilityWithdrawalId.generate()),
            )
        }.isInstanceOf(FixedDropEstimatePublicationConflictException::class.java)
        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.withdrawals)
            .containsExactly(invalidation)
        assertThat(estimateRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `active estimate and withdrawal cannot share one exact key`() {
        val relationship = relationship()
        val first = snapshot()
        service.publish(relationship.id, normalScope, first, null)

        assertThatThrownBy {
            service.withdraw(relationship.id, normalScope, withdrawal(), null)
        }.isInstanceOf(FixedDropEstimatePublicationConflictException::class.java)
        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.snapshots).containsExactly(first)
        assertThat(estimateRepository.count()).isEqualTo(1L)
    }

    @Test
    fun `database must enforce mutually exclusive withdrawal and numeric payloads`() {
        val relationship = relationship()
        assertThatThrownBy { insertRawWithdrawal(relationship.id, reason = " \t ") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRawWithdrawal(relationship.id, probability = 0.10) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRawWithdrawal(relationship.id, provenance = "MEASURED") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRawWithdrawal(relationship.id, reason = null) }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRawWithdrawal(relationship.id, kind = "UNVERIFIED") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThatThrownBy { insertRawWithdrawal(relationship.id, kind = "ESTIMATE") }
            .isInstanceOf(DataIntegrityViolationException::class.java)
        assertThat(estimateRepository.count()).isZero()
    }

    @Test
    fun `withdrawals should remain exact scope and league isolated`() {
        val relationship = relationship()
        val leagueScope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, league().id)
        val ruthlessScope = AcquisitionKnowledgeScope(version, Ruleset.RUTHLESS)
        val initial = snapshot(probability = 0.10)
        val invalidation = withdrawal()
        service.publish(relationship.id, normalScope, initial, null)
        service.withdraw(relationship.id, leagueScope, invalidation, null)
        service.withdraw(relationship.id, ruthlessScope, withdrawal(), null)

        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.resolve(context()))
            .isEqualTo(FixedDropEstimateResolution.Resolved(initial))
        assertThat(service.findAcceptedTimeline(relationship.id, leagueScope)?.withdrawals)
            .containsExactly(invalidation)
        assertThat(service.findAcceptedTimeline(relationship.id, ruthlessScope)?.snapshots).isEmpty()
        assertThat(estimateRepository.count()).isEqualTo(3L)
    }

    @Test
    fun `competing withdrawal and replacement must protect the same exact active slot`() {
        val relationship = relationship()
        val original = snapshot()
        service.publish(relationship.id, normalScope, original, null)
        val invalidation = withdrawal()
        val replacement = snapshot(probability = 0.08)
        val expected = FixedProbabilityPublicationId.Estimate(original.id)

        val results = racePublications(
            { service.withdraw(relationship.id, normalScope, invalidation, expected) },
            { service.publish(relationship.id, normalScope, replacement, expected) },
        )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is FixedDropEstimatePublicationConflictException })
            .isEqualTo(1)
        assertThat(estimateRepository.count()).isEqualTo(2L)
        assertThat(estimateRepository.findById(original.id.value).orElseThrow().status.name).isEqualTo("SUPERSEDED")
        assertThat(service.findAcceptedTimeline(relationship.id, normalScope)?.changePoints).hasSize(1)
    }

    @Test
    fun `competing first withdrawal and estimate publication allow only one accepted value`() {
        val relationship = relationship()
        val results = racePublications(
            { service.withdraw(relationship.id, normalScope, withdrawal(), null) },
            { service.publish(relationship.id, normalScope, snapshot(), null) },
        )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.exceptionOrNull() is FixedDropEstimatePublicationConflictException })
            .isEqualTo(1)
        assertThat(estimateRepository.count()).isEqualTo(1L)
    }

    private fun acceptedProbability(
        dropRelationshipId: DropRelationshipId,
        scope: AcquisitionKnowledgeScope,
    ): Double? = service.findAcceptedTimeline(dropRelationshipId, scope)
        ?.snapshots
        ?.single()
        ?.model
        ?.probabilityPerAttempt

    private fun relationship(): DropRelationship {
        val source = AcquisitionSource(AcquisitionSourceId.generate())
        val unique = UniqueDefinition(UniqueDefinitionId.generate())
        sourceAdapter.save(source)
        uniqueAdapter.save(unique)
        val relationship = DropRelationship(DropRelationshipId.generate(), source.id, unique.id)
        relationshipAdapter.save(relationship)
        return relationship
    }

    private fun league(): LeagueDefinition = LeagueDefinition(
        id = LeagueDefinitionId.generate(),
        name = "Test league",
        type = LeagueType.CHALLENGE,
        participation = LeagueParticipation.SSF,
        mortality = LeagueMortality.SOFTCORE,
        realm = GameRealm.PC,
    ).also(leagueAdapter::save)

    private fun context(patch: GamePatch? = null): EvaluationContext = EvaluationContext(version, Ruleset.NORMAL, patch)

    private fun withdrawal(
        id: FixedProbabilityWithdrawalId = FixedProbabilityWithdrawalId.generate(),
        patch: GamePatch? = null,
        reason: String = "Old probability no longer applies",
        evidenceReference: String = "https://example.org/invalidation",
    ): FixedProbabilityWithdrawal = FixedProbabilityWithdrawal(
        id = id,
        effectiveFromPatch = patch,
        reason = reason,
        evidenceReference = evidenceReference,
        reviewedAt = reviewedAt,
    )

    private fun insertRawWithdrawal(
        relationshipId: DropRelationshipId,
        reason: String? = "Invalidated",
        probability: Double? = null,
        provenance: String? = null,
        kind: String = "WITHDRAWAL",
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.fixed_drop_estimate (
                id, drop_relationship_id, compatibility_version_major, compatibility_version_minor,
                ruleset, model_type, evidence_reference, reviewed_at, status, published_at,
                knowledge_kind, withdrawal_reason, probability_per_attempt, provenance
            ) VALUES (?, ?, 3, 30, 'NORMAL', 'FIXED_BERNOULLI', ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            relationshipId.value,
            "https://example.org/invalidation",
            Timestamp.from(reviewedAt),
            kind,
            reason,
            probability,
            provenance,
        )
    }

    private fun snapshot(
        id: DropEstimateId = DropEstimateId.generate(),
        patch: GamePatch? = null,
        probability: Double = 0.10,
        evidenceReference: String = "https://example.org/estimate",
    ): FixedDropEstimateSnapshot = FixedDropEstimateSnapshot(
        id = id,
        effectiveFromPatch = patch,
        model = FixedBernoulliModel(probability),
        provenance = DropEstimateProvenance.MEASURED,
        evidenceReference = evidenceReference,
        reviewedAt = reviewedAt,
    )

    private fun insertRaw(
        relationshipId: DropRelationshipId,
        leagueId: LeagueDefinitionId? = null,
        patchNumber: Int? = null,
        patchSuffix: String? = null,
        probability: Double = 0.10,
        provenance: String = "MEASURED",
        evidenceReference: String = "https://example.org/estimate",
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO poe_companion.fixed_drop_estimate (
                id, drop_relationship_id, compatibility_version_major, compatibility_version_minor, ruleset,
                league_definition_id, model_type, effective_patch_number, effective_patch_suffix,
                probability_per_attempt, provenance, evidence_reference, reviewed_at, status, published_at
            ) VALUES (?, ?, 3, 30, 'NORMAL', ?, 'FIXED_BERNOULLI', ?, ?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
            """.trimIndent(),
            DropEstimateId.generate().value,
            relationshipId.value,
            leagueId?.value,
            patchNumber,
            patchSuffix,
            probability,
            provenance,
            evidenceReference,
            Timestamp.from(reviewedAt),
        )
    }

    private fun racePublications(first: () -> Unit, second: () -> Unit): List<Result<Unit>> {
        val executor = DelegatingSecurityContextExecutorService(Executors.newFixedThreadPool(2))
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)

        return try {
            val tasks =
                listOf(first, second).map { operation ->
                    executor.submit(
                        Callable {
                            ready.countDown()
                            check(start.await(10, TimeUnit.SECONDS)) { "Timed out starting competing publications" }
                            runCatching(operation)
                        },
                    )
                }
            check(ready.await(10, TimeUnit.SECONDS)) { "Publication workers did not become ready" }
            start.countDown()
            tasks.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }
}
