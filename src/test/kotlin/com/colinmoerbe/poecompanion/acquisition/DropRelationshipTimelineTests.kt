package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.Ruleset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DropRelationshipTimelineTests {

    private val version = CompatibilityVersion(3, 30)
    private val relationshipId = DropRelationshipId.generate()
    private val leagueId = LeagueDefinitionId.generate()

    @Test
    fun `baseline-only timeline should resolve without exact patch precision`() {
        val snapshot = snapshot()
        val timeline = timeline(snapshot)

        val resolution = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL))

        assertThat(resolution).isEqualTo(DropRelationshipResolution.Resolved(snapshot))
    }

    @Test
    fun `later negative knowledge should supersede earlier positive knowledge`() {
        val canDrop = snapshot(state = DropRelationshipState.CAN_DROP)
        val cannotDrop =
            snapshot(
                effectiveFromPatch = GamePatch(version, 2),
                state = DropRelationshipState.CANNOT_DROP,
            )
        val timeline = timeline(cannotDrop, canDrop)

        val before = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1)))
        val after = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 3)))

        assertThat(before).isEqualTo(DropRelationshipResolution.Resolved(canDrop))
        assertThat(after).isEqualTo(DropRelationshipResolution.Resolved(cannotDrop))
    }

    @Test
    fun `later positive knowledge should restore a relationship after negative knowledge`() {
        val canDrop = snapshot(state = DropRelationshipState.CAN_DROP)
        val cannotDrop =
            snapshot(
                effectiveFromPatch = GamePatch(version, 2),
                state = DropRelationshipState.CANNOT_DROP,
            )
        val restored =
            snapshot(
                effectiveFromPatch = GamePatch(version, 4),
                state = DropRelationshipState.CAN_DROP,
            )
        val timeline = timeline(restored, cannotDrop, canDrop)

        val resolution = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 5)))

        assertThat(resolution).isEqualTo(DropRelationshipResolution.Resolved(restored))
    }

    @Test
    fun `explicit cannot-drop knowledge should resolve instead of becoming unknown`() {
        val cannotDrop = snapshot(state = DropRelationshipState.CANNOT_DROP)
        val timeline = timeline(cannotDrop)

        val resolution = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL))

        assertThat(resolution).isEqualTo(DropRelationshipResolution.Resolved(cannotDrop))
        assertThat(resolution).isNotEqualTo(DropRelationshipResolution.Unknown)
    }

    @Test
    fun `patch-dependent timeline should be unknown without exact patch precision`() {
        val timeline =
            timeline(
                snapshot(),
                snapshot(
                    effectiveFromPatch = GamePatch(version, 2),
                    state = DropRelationshipState.CANNOT_DROP,
                ),
            )

        val resolution = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL))

        assertThat(resolution).isEqualTo(DropRelationshipResolution.Unknown)
    }

    @Test
    fun `context before first known change should remain unknown when no baseline exists`() {
        val timeline = timeline(snapshot(effectiveFromPatch = GamePatch(version, 2)))

        val resolution = timeline.resolve(EvaluationContext(version, Ruleset.NORMAL, GamePatch(version, 1)))

        assertThat(resolution).isEqualTo(DropRelationshipResolution.Unknown)
    }

    @Test
    fun `different compatibility version or ruleset should not apply`() {
        val timeline = timeline(snapshot())

        assertThat(timeline.resolve(EvaluationContext(CompatibilityVersion(3, 31), Ruleset.NORMAL)))
            .isEqualTo(DropRelationshipResolution.DoesNotApply)
        assertThat(timeline.resolve(EvaluationContext(version, Ruleset.RUTHLESS)))
            .isEqualTo(DropRelationshipResolution.DoesNotApply)
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

        assertThat(missingLeague).isEqualTo(DropRelationshipResolution.Unknown)
        assertThat(otherLeague).isEqualTo(DropRelationshipResolution.DoesNotApply)
    }

    @Test
    fun `timeline should reject duplicate change points`() {
        val patch = GamePatch(version, 2)

        assertThatThrownBy {
            timeline(
                snapshot(effectiveFromPatch = patch),
                snapshot(effectiveFromPatch = patch, state = DropRelationshipState.CANNOT_DROP),
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `timeline should reject snapshot patch from another compatibility version`() {
        assertThatThrownBy {
            timeline(snapshot(effectiveFromPatch = GamePatch(CompatibilityVersion(3, 31), 1)))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun timeline(
        vararg snapshots: DropRelationshipSnapshot,
        leagueDefinitionId: LeagueDefinitionId? = null,
    ): DropRelationshipTimeline = DropRelationshipTimeline(
        dropRelationshipId = relationshipId,
        scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL, leagueDefinitionId),
        snapshots = snapshots.toList(),
    )

    private fun snapshot(
        effectiveFromPatch: GamePatch? = null,
        state: DropRelationshipState = DropRelationshipState.CAN_DROP,
    ): DropRelationshipSnapshot = DropRelationshipSnapshot(
        effectiveFromPatch = effectiveFromPatch,
        state = state,
    )
}
