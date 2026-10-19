package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.acquisition.knowledge.AcquisitionKnowledgeScope
import com.colinmoerbe.poecompanion.league.ApplicabilityResult
import com.colinmoerbe.poecompanion.league.EvaluationContext
import com.colinmoerbe.poecompanion.league.GamePatch
import java.util.Collections

/**
 * Accepted game-patch probability knowledge for one source-to-Unique relationship and one exact scope.
 *
 * A withdrawal remains a change point in this timeline, preventing an obsolete numeric rate from carrying forward.
 * Superseded curator publications are retained only in persistence and never presented as accepted game knowledge.
 */
class FixedDropEstimateTimeline(
    val dropRelationship: DropRelationship,
    val scope: AcquisitionKnowledgeScope,
    val attemptDefinition: CompletedBossEncounterAttempt,
    snapshots: List<FixedDropEstimateSnapshot>,
    withdrawals: List<FixedProbabilityWithdrawal> = emptyList(),
) {
    val snapshots: List<FixedDropEstimateSnapshot>
    val withdrawals: List<FixedProbabilityWithdrawal>
    val changePoints: List<FixedProbabilityChangePoint>

    init {
        require(dropRelationship.acquisitionSourceId == attemptDefinition.acquisitionSourceId) {
            "Fixed probability attempt must refer to the drop relationship's acquisition source"
        }
        val points =
            snapshots.map { FixedProbabilityChangePoint.Known(it) } +
                withdrawals.map { FixedProbabilityChangePoint.Withdrawn(it) }
        require(points.isNotEmpty()) { "Fixed probability timeline must contain at least one accepted change point" }
        require(points.map { it.publicationId.value }.distinct().size == points.size) {
            "Fixed probability timeline must not repeat a publication identity"
        }
        require(points.map { it.effectiveFromPatch }.distinct().size == points.size) {
            "Fixed probability timeline must not contain duplicate game change points"
        }
        require(
            points.all { point ->
                val effectivePatch = point.effectiveFromPatch
                effectivePatch == null || effectivePatch.compatibilityVersion == scope.compatibilityVersion
            },
        ) {
            "Fixed probability change points must belong to the timeline compatibility version"
        }

        changePoints = Collections.unmodifiableList(points.sortedWith(Comparator(::compareByEffectivePatch)))
        this.snapshots = Collections.unmodifiableList(
            changePoints.filterIsInstance<FixedProbabilityChangePoint.Known>().map { it.estimate },
        )
        this.withdrawals = Collections.unmodifiableList(
            changePoints.filterIsInstance<FixedProbabilityChangePoint.Withdrawn>().map { it.withdrawal },
        )
    }

    /** The caller must independently resolve CAN_DROP and source availability. */
    fun resolve(context: EvaluationContext): FixedDropEstimateResolution = when (scope.evaluateAgainst(context)) {
        ApplicabilityResult.DOES_NOT_APPLY -> FixedDropEstimateResolution.DoesNotApply
        ApplicabilityResult.UNKNOWN -> FixedDropEstimateResolution.Unknown
        ApplicabilityResult.APPLIES -> resolvePatch(context)
    }

    @Suppress("ReturnCount")
    private fun resolvePatch(context: EvaluationContext): FixedDropEstimateResolution {
        val contextPatch = context.gamePatch
        if (contextPatch == null) {
            if (changePoints.any { it.effectiveFromPatch != null }) {
                return FixedDropEstimateResolution.Unknown
            }
            return resolveAccepted(changePoints.single())
        }
        val applicable =
            changePoints.lastOrNull { changePoint ->
                val effectivePatch = changePoint.effectiveFromPatch
                effectivePatch == null || effectivePatch.compareWithinCompatibilityVersion(contextPatch) <= 0
            }
        return applicable?.let(::resolveAccepted) ?: FixedDropEstimateResolution.Unknown
    }

    private fun resolveAccepted(changePoint: FixedProbabilityChangePoint): FixedDropEstimateResolution =
        when (changePoint) {
            is FixedProbabilityChangePoint.Known -> FixedDropEstimateResolution.Resolved(changePoint.estimate)
            is FixedProbabilityChangePoint.Withdrawn -> FixedDropEstimateResolution.Unknown
        }

    private fun compareByEffectivePatch(first: FixedProbabilityChangePoint, second: FixedProbabilityChangePoint): Int {
        val firstPatch: GamePatch? = first.effectiveFromPatch
        val secondPatch: GamePatch? = second.effectiveFromPatch
        return when {
            firstPatch == null && secondPatch == null -> 0
            firstPatch == null -> -1
            secondPatch == null -> 1
            else -> firstPatch.compareWithinCompatibilityVersion(secondPatch)
        }
    }
}
