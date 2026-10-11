package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.ApplicabilityResult
import com.colinmoerbe.poecompanion.league.EvaluationContext
import java.util.Collections

/**
 * Versioned canonical encounter knowledge for one acquisition source and one independently evolving scope.
 *
 * Snapshots are complete states at change points, not deltas or curator edit history.
 */
class BossEncounterKnowledgeTimeline(
    val acquisitionSourceId: AcquisitionSourceId,
    val scope: AcquisitionKnowledgeScope,
    snapshots: List<BossEncounterKnowledgeSnapshot>,
) {
    val snapshots: List<BossEncounterKnowledgeSnapshot>

    init {
        require(snapshots.isNotEmpty()) { "Boss encounter knowledge timeline must contain at least one snapshot" }
        require(snapshots.map { it.effectiveFromPatch }.distinct().size == snapshots.size) {
            "Boss encounter knowledge timeline must not contain duplicate change points"
        }
        require(
            snapshots.all {
                it.effectiveFromPatch == null ||
                    it.effectiveFromPatch.compatibilityVersion == scope.compatibilityVersion
            },
        ) {
            "Boss encounter knowledge snapshot patch must belong to the timeline compatibility version"
        }

        this.snapshots =
            Collections.unmodifiableList(
                snapshots.sortedWith(
                    compareBy<BossEncounterKnowledgeSnapshot> { it.effectiveFromPatch != null }
                        .thenComparator { first, second ->
                            val firstPatch = first.effectiveFromPatch
                            val secondPatch = second.effectiveFromPatch
                            when {
                                firstPatch == null || secondPatch == null -> 0
                                else -> firstPatch.compareWithinCompatibilityVersion(secondPatch)
                            }
                        },
                ),
            )
    }

    /**
     * Resolves the current trustworthy snapshot for the supplied evaluation context.
     */
    fun resolve(context: EvaluationContext): BossEncounterKnowledgeResolution {
        return when (scope.evaluateAgainst(context)) {
            ApplicabilityResult.DOES_NOT_APPLY -> BossEncounterKnowledgeResolution.DoesNotApply
            ApplicabilityResult.UNKNOWN -> BossEncounterKnowledgeResolution.Unknown
            ApplicabilityResult.APPLIES -> resolvePatch(context)
        }
    }

    @Suppress("ReturnCount")
    private fun resolvePatch(context: EvaluationContext): BossEncounterKnowledgeResolution {
        val contextPatch = context.gamePatch

        if (contextPatch == null) {
            if (snapshots.any { it.effectiveFromPatch != null }) {
                return BossEncounterKnowledgeResolution.Unknown
            }

            return BossEncounterKnowledgeResolution.Resolved(snapshots.single())
        }

        val applicableSnapshot =
            snapshots.lastOrNull { snapshot ->
                val effectivePatch = snapshot.effectiveFromPatch
                effectivePatch == null || effectivePatch.compareWithinCompatibilityVersion(contextPatch) <= 0
            }

        return applicableSnapshot?.let { BossEncounterKnowledgeResolution.Resolved(it) }
            ?: BossEncounterKnowledgeResolution.Unknown
    }
}
