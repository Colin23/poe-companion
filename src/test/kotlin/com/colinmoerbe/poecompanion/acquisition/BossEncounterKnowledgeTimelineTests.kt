package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.Ruleset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class BossEncounterKnowledgeTimelineTests {

    private val version = CompatibilityVersion(3, 30)
    private val sourceId = AcquisitionSourceId.generate()
    private val bossId = BossDefinitionId.generate()
    private val leagueId = LeagueDefinitionId.generate()

    @Test
    fun `baseline-only timeline should resolve without exact patch precision`() {
        val snapshot = snapshot()
        val timeline = timeline(snapshot)

        val resolution = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL))

        assertThat(resolution).isEqualTo(BossEncounterKnowledgeResolution.Resolved(snapshot))
    }

    @Test
    fun `later change point should supersede baseline from its patch onward`() {
        val baseline = snapshot(availability = AcquisitionSourceAvailability.AVAILABLE)
        val unavailable =
            snapshot(
                effectiveFromPatch = GamePatch(version, 2),
                availability = AcquisitionSourceAvailability.UNAVAILABLE,
            )
        val timeline = timeline(unavailable, baseline)

        val before = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1)))
        val after = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 3)))

        assertThat(before).isEqualTo(BossEncounterKnowledgeResolution.Resolved(baseline))
        assertThat(after).isEqualTo(BossEncounterKnowledgeResolution.Resolved(unavailable))
    }

    @Test
    fun `patch-dependent timeline should be unknown without exact patch precision`() {
        val timeline =
            timeline(
                snapshot(),
                snapshot(
                    effectiveFromPatch = GamePatch(version, 2),
                    availability = AcquisitionSourceAvailability.UNAVAILABLE,
                ),
            )

        val resolution = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL))

        assertThat(resolution).isEqualTo(BossEncounterKnowledgeResolution.Unknown)
    }

    @Test
    fun `context before first known change should remain unknown when no baseline exists`() {
        val timeline = timeline(snapshot(effectiveFromPatch = GamePatch(version, 2)))

        val resolution = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1)))

        assertThat(resolution).isEqualTo(BossEncounterKnowledgeResolution.Unknown)
    }

    @Test
    fun `different compatibility version or ruleset should not apply`() {
        val timeline = timeline(snapshot())

        assertThat(timeline.resolve(EvaluationContext(CompatibilityVersion(3, 31), Ruleset.NORMAL)))
            .isEqualTo(BossEncounterKnowledgeResolution.DoesNotApply)
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.RUTHLESS)))
            .isEqualTo(BossEncounterKnowledgeResolution.DoesNotApply)
    }

    @Test
    fun `league-specific timeline should be unknown without league precision and not apply to another league`() {
        val timeline = timeline(snapshot(), leagueDefinitionId = leagueId)

        val missingLeague = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL))
        val otherLeague =
            timeline.resolve(
                EvaluationContext(
                    version,
                    Ruleset.NORMAL,
                    leagueDefinitionId = LeagueDefinitionId.generate(),
                ),
            )

        assertThat(missingLeague).isEqualTo(BossEncounterKnowledgeResolution.Unknown)
        assertThat(otherLeague).isEqualTo(BossEncounterKnowledgeResolution.DoesNotApply)
    }

    @Test
    fun `timeline should reject duplicate change points`() {
        val patch = GamePatch(version, 2)

        assertThatThrownBy {
            timeline(
                snapshot(effectiveFromPatch = patch),
                snapshot(effectiveFromPatch = patch, displayLabel = "Updated label"),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `timeline should reject snapshot patch from another compatibility version`() {
        assertThatThrownBy {
            timeline(snapshot(effectiveFromPatch = GamePatch(CompatibilityVersion(3, 31), 1)))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `snapshot should reject blank display label`() {
        assertThatThrownBy {
            snapshot(displayLabel = " ")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `fixed participation should require at least one boss and preserve multiple bosses`() {
        val otherBossId = BossDefinitionId.generate()
        val participation = BossParticipation.Fixed(setOf(bossId, otherBossId))

        assertThat(participation.bossDefinitionIds).containsExactlyInAnyOrder(bossId, otherBossId)
        assertThatThrownBy {
            BossParticipation.Fixed(emptySet())
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `configuration-dependent participation should not require a fake fixed roster`() {
        val snapshot = snapshot(bossParticipation = BossParticipation.ConfigurationDependent)

        assertThat(snapshot.bossParticipation).isEqualTo(BossParticipation.ConfigurationDependent)
    }

    private fun timeline(
        vararg snapshots: BossEncounterKnowledgeSnapshot,
        leagueDefinitionId: LeagueDefinitionId? = null,
    ): BossEncounterKnowledgeTimeline =
        BossEncounterKnowledgeTimeline(
            acquisitionSourceId = sourceId,
            scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, leagueDefinitionId),
            snapshots = snapshots.toList(),
        )

    private fun snapshot(
        effectiveFromPatch: GamePatch? = null,
        displayLabel: String = "Uber Elder",
        availability: AcquisitionSourceAvailability = AcquisitionSourceAvailability.AVAILABLE,
        bossParticipation: BossParticipation = BossParticipation.Fixed(setOf(bossId)),
    ): BossEncounterKnowledgeSnapshot =
        BossEncounterKnowledgeSnapshot(
            effectiveFromPatch = effectiveFromPatch,
            displayLabel = displayLabel,
            availability = availability,
            bossParticipation = bossParticipation,
        )
}
