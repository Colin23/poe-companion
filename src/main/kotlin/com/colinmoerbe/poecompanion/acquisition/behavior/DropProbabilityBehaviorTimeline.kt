package com.colinmoerbe.poecompanion.acquisition.behavior

import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.acquisition.relationship.DropRelationshipId
import com.colinmoerbe.poecompanion.league.ApplicabilityResult
import com.colinmoerbe.poecompanion.league.EvaluationContext
import java.util.Collections

/**
 * Curator-accepted probability behavior, independent of numeric estimates and CAN_DROP applicability.
 *
 * A null change-point patch is the compatibility-version baseline, not a personal patch-unspecified override.
 */
class DropProbabilityBehaviorTimeline(
    val dropRelationshipId: DropRelationshipId,
    val scope: AcquisitionKnowledgeScope,
    snapshots: List<DropProbabilityBehaviorSnapshot>,
) {
    val snapshots: List<DropProbabilityBehaviorSnapshot>

    init {
        require(snapshots.isNotEmpty()) { "Probability-behavior timeline must not be empty" }
        require(snapshots.map { it.effectiveFromPatch }.distinct().size == snapshots.size) {
            "Probability-behavior timeline must not repeat a patch change point"
        }
        require(
            snapshots.all { snapshot ->
                snapshot.effectiveFromPatch == null ||
                    snapshot.effectiveFromPatch.compatibilityVersion == scope.compatibilityVersion
            },
        ) {
            "Probability-behavior change points must use the same compatibility version as their scope"
        }
        this.snapshots = Collections.unmodifiableList(snapshots.sortedWith(Comparator(::compareByPatch)))
    }

    /** Insufficient patch precision cannot select a version baseline if later behavior changes are known. */
    fun resolve(context: EvaluationContext): DropProbabilityBehaviorResolution = when (scope.evaluateAgainst(context)) {
        ApplicabilityResult.DOES_NOT_APPLY -> DropProbabilityBehaviorResolution.DoesNotApply
        ApplicabilityResult.UNKNOWN -> DropProbabilityBehaviorResolution.Unknown
        ApplicabilityResult.APPLIES -> resolvePatch(context)
    }

    private fun resolvePatch(context: EvaluationContext): DropProbabilityBehaviorResolution {
        val patch = context.gamePatch
        if (patch == null) {
            return if (snapshots.size == 1 && snapshots.single().effectiveFromPatch == null) {
                DropProbabilityBehaviorResolution.Resolved(snapshots.single())
            } else {
                DropProbabilityBehaviorResolution.Unknown
            }
        }
        val snapshot = snapshots.lastOrNull { candidate ->
            candidate.effectiveFromPatch?.compareWithinCompatibilityVersion(patch)?.let { it <= 0 } ?: true
        }
        return snapshot?.let { DropProbabilityBehaviorResolution.Resolved(it) }
            ?: DropProbabilityBehaviorResolution.Unknown
    }

    private fun compareByPatch(first: DropProbabilityBehaviorSnapshot, second: DropProbabilityBehaviorSnapshot): Int {
        val left = first.effectiveFromPatch
        val right = second.effectiveFromPatch
        return when {
            left == null && right == null -> 0
            left == null -> -1
            right == null -> 1
            else -> left.compareWithinCompatibilityVersion(right)
        }
    }
}
