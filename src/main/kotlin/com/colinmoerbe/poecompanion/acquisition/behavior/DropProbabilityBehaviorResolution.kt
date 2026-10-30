package com.colinmoerbe.poecompanion.acquisition.behavior

import com.colinmoerbe.poecompanion.league.ApplicabilityResult

/** Resolves independently authored model suitability, not a numerical DropEstimate. */
sealed interface DropProbabilityBehaviorResolution {
    val applicability: ApplicabilityResult

    data class Resolved(val snapshot: DropProbabilityBehaviorSnapshot) : DropProbabilityBehaviorResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.APPLIES
    }

    data object DoesNotApply : DropProbabilityBehaviorResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.DOES_NOT_APPLY
    }

    data object Unknown : DropProbabilityBehaviorResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.UNKNOWN
    }
}
