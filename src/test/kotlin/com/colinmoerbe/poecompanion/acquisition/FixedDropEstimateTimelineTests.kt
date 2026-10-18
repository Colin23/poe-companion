package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.sourceidentity.AcquisitionSourceId
import com.colinmoerbe.poecompanion.catalog.UniqueDefinitionId
import com.colinmoerbe.poecompanion.league.ApplicabilityResult
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.Ruleset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class FixedDropEstimateTimelineTests {

    private val version = CompatibilityVersion(3, 30)
    private val relationship =
        DropRelationship(DropRelationshipId.generate(), AcquisitionSourceId.generate(), UniqueDefinitionId.generate())
    private val leagueId = LeagueDefinitionId.generate()
    private val reviewedAt = Instant.parse("2026-10-01T12:00:00Z")

    @Test
    fun `canonical estimate IDs should be unique and reconstructable`() {
        val id = DropEstimateId.generate()

        assertThat(DropEstimateId(id.value)).isEqualTo(id)
        assertThat(DropEstimateId.generate()).isNotEqualTo(id)
    }

    @Test
    fun `canonical provenance should contain only supported evidence kinds`() {
        assertThat(DropEstimateProvenance.entries)
            .containsExactly(
                DropEstimateProvenance.OFFICIAL,
                DropEstimateProvenance.MEASURED,
                DropEstimateProvenance.COMMUNITY_ESTIMATE,
            )
    }

    @Test
    fun `snapshot should retain its probability provenance and review evidence`() {
        val snapshot = snapshot(probability = 0.10, provenance = DropEstimateProvenance.MEASURED)

        assertThat(snapshot.model.probabilityPerAttempt).isEqualTo(0.10)
        assertThat(snapshot.provenance).isEqualTo(DropEstimateProvenance.MEASURED)
        assertThat(snapshot.evidenceReference).isEqualTo("https://example.org/drop-data")
        assertThat(snapshot.reviewedAt).isEqualTo(reviewedAt)
    }

    @Test
    fun `snapshot should reject missing evidence but accept a meaningful reference`() {
        assertThatThrownBy { snapshot(evidenceReference = " \t ") }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThat(snapshot(evidenceReference = "Developer-curated research note #42").evidenceReference)
            .isEqualTo("Developer-curated research note #42")
    }

    @Test
    fun `fixed probability validation should be inherited from FixedBernoulliModel`() {
        assertThatThrownBy { snapshot(probability = 0.0) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { snapshot(probability = Double.NaN) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { snapshot(probability = 1.01) }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThat(snapshot(probability = 1.0).model.probabilityPerAttempt).isEqualTo(1.0)
    }

    @Test
    fun `one completed multi-boss encounter is one source-defined attempt`() {
        val attempt = CompletedBossEncounterAttempt(relationship.acquisitionSourceId)
        val timeline = timeline(snapshot(), attemptDefinition = attempt)

        assertThat(timeline.attemptDefinition).isEqualTo(attempt)
        assertThat(timeline.dropRelationship.id).isEqualTo(relationship.id)
    }

    @Test
    fun `attempt source must match the drop relationship source`() {
        assertThatThrownBy {
            timeline(
                snapshot(),
                attemptDefinition = CompletedBossEncounterAttempt(AcquisitionSourceId.generate()),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `baseline-only fixed estimate should resolve without exact patch precision`() {
        val initial = snapshot()
        val timeline = timeline(initial)

        val result = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL))

        assertThat(result).isEqualTo(FixedDropEstimateResolution.Resolved(initial))
        assertThat(result.applicability).isEqualTo(ApplicabilityResult.APPLIES)
    }

    @Test
    fun `later accepted fixed rate should replace an earlier game-patch value`() {
        val initial = snapshot(probability = 0.10)
        val later = snapshot(patch = GamePatch(version, 2), probability = 0.08)
        val timeline = timeline(later, initial)

        val before = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1)))
        val after = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 3)))

        assertThat(before).isEqualTo(FixedDropEstimateResolution.Resolved(initial))
        assertThat(after).isEqualTo(FixedDropEstimateResolution.Resolved(later))
        assertThat(timeline.snapshots).containsExactly(initial, later)
    }

    @Test
    fun `patchless evaluation should remain unknown when a later change point exists`() {
        val timeline = timeline(snapshot(), snapshot(patch = GamePatch(version, 2), probability = 0.08))

        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL)))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
    }

    @Test
    fun `earlier context should be unknown if the first estimate starts in a later patch`() {
        val timeline = timeline(snapshot(patch = GamePatch(version, 2)))

        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1))))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL)))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
    }

    @Test
    fun `wrong compatibility version or ruleset should not apply`() {
        val timeline = timeline(snapshot())

        assertThat(timeline.resolve(EvaluationContext(CompatibilityVersion(3, 31), Ruleset.NORMAL)))
            .isEqualTo(FixedDropEstimateResolution.DoesNotApply)
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.RUTHLESS)))
            .isEqualTo(FixedDropEstimateResolution.DoesNotApply)
    }

    @Test
    fun `league-specific knowledge should require matching league precision`() {
        val accepted = snapshot()
        val timeline = timeline(accepted, leagueDefinitionId = leagueId)

        val unknown = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL))
        val mismatch =
            timeline.resolve(
                EvaluationContext(
                    version,
                    Ruleset.NORMAL,
                    leagueDefinitionId = LeagueDefinitionId.generate(),
                ),
            )
        val match =
            timeline.resolve(
                EvaluationContext(version, Ruleset.NORMAL, leagueDefinitionId = leagueId),
            )

        assertThat(unknown).isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(mismatch).isEqualTo(FixedDropEstimateResolution.DoesNotApply)
        assertThat(match).isEqualTo(FixedDropEstimateResolution.Resolved(accepted))
    }

    @Test
    fun `empty accepted estimate timeline should be rejected`() {
        assertThatThrownBy { timeline() }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `duplicate game patch change point should be rejected even when estimates have different IDs`() {
        val patch = GamePatch(version, 2)

        assertThatThrownBy {
            timeline(snapshot(patch = patch), snapshot(patch = patch, probability = 0.08))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `duplicate estimate identity should not be reused for different change points`() {
        val id = DropEstimateId.generate()

        assertThatThrownBy {
            timeline(snapshot(id = id), snapshot(id = id, patch = GamePatch(version, 2)))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `snapshot patches must belong to the estimate compatibility version`() {
        assertThatThrownBy {
            timeline(snapshot(patch = GamePatch(CompatibilityVersion(3, 31), 1)))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `timeline should copy and protect the accepted snapshot list`() {
        val first = snapshot()
        val mutable = mutableListOf(first)
        val timeline =
            FixedDropEstimateTimeline(
                dropRelationship = relationship,
                scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL),
                attemptDefinition = CompletedBossEncounterAttempt(relationship.acquisitionSourceId),
                snapshots = mutable,
            )

        mutable.add(snapshot(patch = GamePatch(version, 2)))

        assertThat(timeline.snapshots).containsExactly(first)
        assertThatThrownBy { (timeline.snapshots as MutableList<FixedDropEstimateSnapshot>).clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    @Test
    fun `withdrawal is a distinct canonical publication identity`() {
        val id = FixedProbabilityWithdrawalId.generate()

        assertThat(FixedProbabilityWithdrawalId(id.value)).isEqualTo(id)
        assertThat(FixedProbabilityPublicationId.Withdrawal(id).value).isEqualTo(id.value)
        assertThat(FixedProbabilityPublicationId.Estimate(DropEstimateId(id.value)))
            .isNotEqualTo(FixedProbabilityPublicationId.Withdrawal(id))
    }

    @Test
    fun `withdrawal cannot have blank reason or evidence reference`() {
        assertThatThrownBy { withdrawal(reason = " \t ") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { withdrawal(evidenceReference = " \t ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `known withdrawal and reestablished estimate should resolve by change point`() {
        val baseline = snapshot(probability = 0.10)
        val invalidated = withdrawal(patch = GamePatch(version, 2))
        val reestablished = snapshot(patch = GamePatch(version, 4), probability = 0.08)
        val timeline = withWithdrawal(listOf(baseline, reestablished), listOf(invalidated))

        assertThat(timeline.changePoints).containsExactly(
            FixedProbabilityChangePoint.Known(baseline),
            FixedProbabilityChangePoint.Withdrawn(invalidated),
            FixedProbabilityChangePoint.Known(reestablished),
        )
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1))))
            .isEqualTo(FixedDropEstimateResolution.Resolved(baseline))
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 3))))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 4))))
            .isEqualTo(FixedDropEstimateResolution.Resolved(reestablished))
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL)))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
    }

    @Test
    fun `withdrawal-only baseline remains unknown even with precise or absent patch`() {
        val invalidation = withdrawal()
        val timeline = withWithdrawal(emptyList(), listOf(invalidation))

        assertThat(timeline.snapshots).isEmpty()
        assertThat(timeline.changePoints).containsExactly(FixedProbabilityChangePoint.Withdrawn(invalidation))
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL)))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 3))))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
    }

    @Test
    fun `later withdrawal with no baseline should not imply prior knowledge`() {
        val invalidation = withdrawal(patch = GamePatch(version, 2))
        val timeline = withWithdrawal(emptyList(), listOf(invalidation))

        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1))))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 2))))
            .isEqualTo(FixedDropEstimateResolution.Unknown)
    }

    @Test
    fun `a withdrawal and numeric estimate cannot occupy the same accepted change point`() {
        assertThatThrownBy {
            withWithdrawal(listOf(snapshot()), listOf(withdrawal()))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `withdrawal patches must match compatibility version and identities must be unique`() {
        assertThatThrownBy {
            withWithdrawal(emptyList(), listOf(withdrawal(patch = GamePatch(CompatibilityVersion(3, 31), 2))))
        }.isInstanceOf(IllegalArgumentException::class.java)
        val id = FixedProbabilityWithdrawalId.generate()
        assertThatThrownBy {
            withWithdrawal(
                emptyList(),
                listOf(withdrawal(id = id), withdrawal(id = id, patch = GamePatch(version, 2))),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `timeline must defensively copy and freeze withdrawal and combined lists`() {
        val invalidation = withdrawal()
        val mutable = mutableListOf(invalidation)
        val timeline = withWithdrawal(emptyList(), mutable)
        mutable.add(withdrawal(patch = GamePatch(version, 2)))

        assertThat(timeline.withdrawals).containsExactly(invalidation)
        assertThatThrownBy { (timeline.withdrawals as MutableList<FixedProbabilityWithdrawal>).clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
        assertThatThrownBy { (timeline.changePoints as MutableList<FixedProbabilityChangePoint>).clear() }
            .isInstanceOf(UnsupportedOperationException::class.java)
    }

    private fun withWithdrawal(
        known: List<FixedDropEstimateSnapshot>,
        withdrawn: List<FixedProbabilityWithdrawal>,
    ): FixedDropEstimateTimeline = FixedDropEstimateTimeline(
        dropRelationship = relationship,
        scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL),
        attemptDefinition = CompletedBossEncounterAttempt(relationship.acquisitionSourceId),
        snapshots = known,
        withdrawals = withdrawn,
    )

    private fun withdrawal(
        id: FixedProbabilityWithdrawalId = FixedProbabilityWithdrawalId.generate(),
        patch: GamePatch? = null,
        reason: String = "Game changes invalidate the old fixed rate",
        evidenceReference: String = "Developer-curated evidence #withdrawal",
    ): FixedProbabilityWithdrawal = FixedProbabilityWithdrawal(
        id = id,
        effectiveFromPatch = patch,
        reason = reason,
        evidenceReference = evidenceReference,
        reviewedAt = reviewedAt,
    )

    private fun timeline(
        vararg snapshots: FixedDropEstimateSnapshot,
        leagueDefinitionId: LeagueDefinitionId? = null,
        attemptDefinition: CompletedBossEncounterAttempt =
            CompletedBossEncounterAttempt(relationship.acquisitionSourceId),
    ): FixedDropEstimateTimeline = FixedDropEstimateTimeline(
        dropRelationship = relationship,
        scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, leagueDefinitionId),
        attemptDefinition = attemptDefinition,
        snapshots = snapshots.toList(),
    )

    private fun snapshot(
        id: DropEstimateId = DropEstimateId.generate(),
        patch: GamePatch? = null,
        probability: Double = 0.10,
        provenance: DropEstimateProvenance = DropEstimateProvenance.COMMUNITY_ESTIMATE,
        evidenceReference: String = "https://example.org/drop-data",
    ): FixedDropEstimateSnapshot = FixedDropEstimateSnapshot(
        id = id,
        effectiveFromPatch = patch,
        model = FixedBernoulliModel(probability),
        provenance = provenance,
        evidenceReference = evidenceReference,
        reviewedAt = reviewedAt,
    )
}
