package com.colinmoerbe.poecompanion.acquisition.behavior

import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.league.CompatibilityVersion
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GamePatch
import com.colinmoerbe.poecompanion.league.LeagueDefinitionId
import com.colinmoerbe.poecompanion.league.Ruleset
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DropProbabilityBehaviorTimelineTests {
    private val version = CompatibilityVersion(3, 30)
    private val relationshipId = DropRelationshipId.generate()
    private val scope = AcquisitionKnowledgeScope(version, Ruleset.NORMAL)

    @Test
    fun `single verified fixed baseline applies even without exact patch precision`() {
        val baseline = snapshot()
        val timeline = timeline(baseline)

        assertThat(timeline.resolve(context()))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(baseline))
        assertThat(timeline.resolve(context(GamePatch(version, 1))))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(baseline))
    }

    @Test
    fun `modifier dependent behavior is explicitly known and not confused with unknown`() {
        val modified = snapshot(behavior = DropProbabilityBehavior.MODIFIER_DEPENDENT)
        val timeline = timeline(modified)

        assertThat(timeline.resolve(context()))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(modified))
        assertThat(timeline.resolve(context())).isNotEqualTo(DropProbabilityBehaviorResolution.Unknown)
    }

    @Test
    fun `a patch change can turn fixed behavior into modifier dependent and back`() {
        val baseline = snapshot()
        val modified = snapshot(GamePatch(version, 2), DropProbabilityBehavior.MODIFIER_DEPENDENT)
        val restored = snapshot(GamePatch(version, 4), DropProbabilityBehavior.FIXED_INDEPENDENT)
        val timeline = timeline(restored, modified, baseline)

        assertThat(timeline.snapshots).containsExactly(baseline, modified, restored)
        assertThat(timeline.resolve(context(GamePatch(version, 1))))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(baseline))
        assertThat(timeline.resolve(context(GamePatch(version, 2))))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(modified))
        assertThat(timeline.resolve(context(GamePatch(version, 5))))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(restored))
        assertThat(timeline.resolve(context())).isEqualTo(DropProbabilityBehaviorResolution.Unknown)
    }

    @Test
    fun `a missing baseline and unspecified patch cannot be assumed fixed`() {
        val later = snapshot(GamePatch(version, 2))
        val timeline = timeline(later)

        assertThat(timeline.resolve(context())).isEqualTo(DropProbabilityBehaviorResolution.Unknown)
        assertThat(timeline.resolve(context(GamePatch(version, 1))))
            .isEqualTo(DropProbabilityBehaviorResolution.Unknown)
        assertThat(timeline.resolve(context(GamePatch(version, 2))))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(later))
    }

    @Test
    fun `patch suffix order must be observed`() {
        val earlier = snapshot(GamePatch(version, 2, "a"), DropProbabilityBehavior.FIXED_INDEPENDENT)
        val later = snapshot(GamePatch(version, 2, "b"), DropProbabilityBehavior.MODIFIER_DEPENDENT)
        val timeline = timeline(later, earlier)

        assertThat(timeline.resolve(context(GamePatch(version, 2))))
            .isEqualTo(DropProbabilityBehaviorResolution.Unknown)
        assertThat(timeline.resolve(context(GamePatch(version, 2, "a"))))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(earlier))
        assertThat(timeline.resolve(context(GamePatch(version, 2, "c"))))
            .isEqualTo(DropProbabilityBehaviorResolution.Resolved(later))
    }

    @Test
    fun `other version ruleset or league cannot reuse a fact`() {
        val leagueId = LeagueDefinitionId.generate()
        val specific = DropProbabilityBehaviorTimeline(
            relationshipId,
            AcquisitionKnowledgeScope(version, Ruleset.NORMAL, leagueId),
            listOf(snapshot()),
        )

        assertThat(specific.resolve(context()))
            .isEqualTo(DropProbabilityBehaviorResolution.Unknown)
        assertThat(specific.resolve(context(leagueDefinitionId = LeagueDefinitionId.generate())))
            .isEqualTo(DropProbabilityBehaviorResolution.DoesNotApply)
        assertThat(specific.resolve(context(leagueDefinitionId = leagueId)))
            .isInstanceOf(DropProbabilityBehaviorResolution.Resolved::class.java)
        assertThat(specific.resolve(context(compatibilityVersion = CompatibilityVersion(3, 31))))
            .isEqualTo(DropProbabilityBehaviorResolution.DoesNotApply)
        assertThat(specific.resolve(context(ruleset = Ruleset.RUTHLESS)))
            .isEqualTo(DropProbabilityBehaviorResolution.DoesNotApply)
    }

    @Test
    fun `duplicated patches and incompatible patch versions are rejected`() {
        assertThatThrownBy { timeline() }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            timeline(snapshot(), snapshot(behavior = DropProbabilityBehavior.MODIFIER_DEPENDENT))
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            timeline(snapshot(GamePatch(CompatibilityVersion(3, 31), 1)))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun timeline(vararg snapshots: DropProbabilityBehaviorSnapshot) =
        DropProbabilityBehaviorTimeline(relationshipId, scope, snapshots.toList())

    private fun snapshot(
        patch: GamePatch? = null,
        behavior: DropProbabilityBehavior = DropProbabilityBehavior.FIXED_INDEPENDENT,
    ): DropProbabilityBehaviorSnapshot = DropProbabilityBehaviorSnapshot(patch, behavior)

    private fun context(
        patch: GamePatch? = null,
        ruleset: Ruleset = Ruleset.NORMAL,
        compatibilityVersion: CompatibilityVersion = version,
        leagueDefinitionId: LeagueDefinitionId? = null,
    ): EvaluationContext = EvaluationContext(compatibilityVersion, ruleset, patch, leagueDefinitionId)
}
