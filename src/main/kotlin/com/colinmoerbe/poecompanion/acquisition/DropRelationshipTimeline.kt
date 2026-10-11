package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.ApplicabilityResult
import com.colinmoerbe.poecompanion.league.EvaluationContext
import java.util.Collections

/**
 * Versioned canonical knowledge for one stable drop relationship in one independently evolving scope.
 *
 * Snapshots are complete states at change points. They express explicit CAN_DROP/CANNOT_DROP knowledge rather than
 * treating an absent relationship as negative knowledge.
 */
class DropRelationshipTimeline(
    val dropRelationshipId: DropRelationshipId,
    val scope: AcquisitionKnowledgeScope,
    snapshots: List<DropRelationshipSnapshot>,
) {
    val snapshots: List<DropRelationshipSnapshot>

    init {
        require(snapshots.isNotEmpty()) { "Drop relationship timeline must contain at least one snapshot" }
        require(snapshots.map { it.effectiveFromPatch }.distinct().size == snapshots.size) {
            "Drop relationship timeline must not contain duplicate change points"
        }
        require(
            snapshots.all {
                it.effectiveFromPatch == null ||
                    it.effectiveFromPatch.compatibilityVersion == scope.compatibilityVersion
            },
        ) {
            "Drop relationship snapshot patch must belong to the timeline compatibility version"
        }

        this.snapshots =
            Collections.unmodifiableList(
                snapshots.sortedWith(Comparator(::compareSnapshotsByEffectivePatch)),
            )
    }

    /**
     * Orders the compatibility-version baseline first, then patch-specific snapshots chronologically.
     *
     * Comparator convention: negative means the first snapshot comes earlier, zero means the same ordering position,
     * and positive means the first snapshot comes later.
     */
    private fun compareSnapshotsByEffectivePatch(
        first: DropRelationshipSnapshot,
        second: DropRelationshipSnapshot,
    ): Int {
        val firstPatch = first.effectiveFromPatch
        val secondPatch = second.effectiveFromPatch

        return when {
            firstPatch == null && secondPatch == null -> 0
            firstPatch == null -> -1
            secondPatch == null -> 1
            else -> firstPatch.compareWithinCompatibilityVersion(secondPatch)
        }
    }

    /**
     * Resolves the current trustworthy relationship state for the supplied evaluation context.
     */
    fun resolve(context: EvaluationContext): DropRelationshipResolution = when (scope.evaluateAgainst(context)) {
        ApplicabilityResult.DOES_NOT_APPLY -> DropRelationshipResolution.DoesNotApply
        ApplicabilityResult.UNKNOWN -> DropRelationshipResolution.Unknown
        ApplicabilityResult.APPLIES -> resolvePatch(context)
    }

    @Suppress("ReturnCount")
    private fun resolvePatch(context: EvaluationContext): DropRelationshipResolution {
        val contextPatch = context.gamePatch

        if (contextPatch == null) {
            if (snapshots.any { it.effectiveFromPatch != null }) {
                return DropRelationshipResolution.Unknown
            }

            return DropRelationshipResolution.Resolved(snapshots.single())
        }

        val applicableSnapshot =
            snapshots.lastOrNull { snapshot ->
                val effectivePatch = snapshot.effectiveFromPatch
                effectivePatch == null || effectivePatch.compareWithinCompatibilityVersion(contextPatch) <= 0
            }

        return applicableSnapshot?.let { DropRelationshipResolution.Resolved(it) }
            ?: DropRelationshipResolution.Unknown
    }
}
