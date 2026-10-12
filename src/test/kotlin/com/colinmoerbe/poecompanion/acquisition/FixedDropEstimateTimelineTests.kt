package com.colinmoerbe.poecompanion.acquisition

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
