package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.ApplicabilityResult
import com.colinmoerbe.poecompanion.league.EvaluationContext
import java.util.Collections

/**
 * Currently accepted fixed-probability knowledge for one source-to-Unique relationship in one scope.
 *
 * Snapshots are game-patch change points, not curator-edit history. Superseded evidence/estimate records and
 * transactional activation are the responsibility of the later persistence workflow.
 */
class FixedDropEstimateTimeline(
    val dropRelationship: DropRelationship,
    val scope: AcquisitionKnowledgeScope,
    val attemptDefinition: CompletedBossEncounterAttempt,
    snapshots: List<FixedDropEstimateSnapshot>,
) {
    val snapshots: List<FixedDropEstimateSnapshot>

    init {
        require(dropRelationship.acquisitionSourceId == attemptDefinition.acquisitionSourceId) {
            "Fixed drop estimate attempt must refer to the drop relationship's acquisition source"
        }
        require(snapshots.isNotEmpty()) { "Fixed drop estimate timeline must contain at least one snapshot" }
        require(snapshots.map { it.id }.distinct().size == snapshots.size) {
            "Fixed drop estimate timeline must not repeat a canonical estimate identity"
        }
        require(snapshots.map { it.effectiveFromPatch }.distinct().size == snapshots.size) {
            "Fixed drop estimate timeline must not contain duplicate change points"
        }
        require(
            snapshots.all {
                it.effectiveFromPatch == null ||
                    it.effectiveFromPatch.compatibilityVersion == scope.compatibilityVersion
            },
        ) {
            "Fixed drop estimate snapshot patch must belong to the timeline compatibility version"
        }

        this.snapshots =
            Collections.unmodifiableList(
                snapshots.sortedWith(Comparator(::compareSnapshotsByEffectivePatch)),
            )
    }

    /**
     * Resolves a fixed probability only where its known game-version scope and patch precision allow it.
     *
     * Callers must separately establish that the source is available and the relationship currently CAN_DROP.
     */
    fun resolve(context: EvaluationContext): FixedDropEstimateResolution = when (scope.evaluateAgainst(context)) {
        ApplicabilityResult.DOES_NOT_APPLY -> FixedDropEstimateResolution.DoesNotApply
        ApplicabilityResult.UNKNOWN -> FixedDropEstimateResolution.Unknown
        ApplicabilityResult.APPLIES -> resolvePatch(context)
    }

    @Suppress("ReturnCount")
    private fun resolvePatch(context: EvaluationContext): FixedDropEstimateResolution {
        val contextPatch = context.gamePatch

        if (contextPatch == null) {
            if (snapshots.any { it.effectiveFromPatch != null }) {
                return FixedDropEstimateResolution.Unknown
            }

            return FixedDropEstimateResolution.Resolved(snapshots.single())
        }

        val applicableSnapshot =
            snapshots.lastOrNull { snapshot ->
                val effectivePatch = snapshot.effectiveFromPatch
                effectivePatch == null || effectivePatch.compareWithinCompatibilityVersion(contextPatch) <= 0
            }

        return applicableSnapshot?.let { FixedDropEstimateResolution.Resolved(it) }
            ?: FixedDropEstimateResolution.Unknown
    }

    /**
     * Baseline first, then patch-specific snapshots in chronological order within the same compatibility version.
     */
    private fun compareSnapshotsByEffectivePatch(
        first: FixedDropEstimateSnapshot,
        second: FixedDropEstimateSnapshot,
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
}
