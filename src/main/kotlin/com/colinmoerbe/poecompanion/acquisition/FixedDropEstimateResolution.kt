package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.ApplicabilityResult

/**
 * Context-dependent resolution of the currently accepted fixed probability knowledge.
 *
 * Unknown also covers insufficient evaluation precision, not only missing baseline probability knowledge.
 */
sealed interface FixedDropEstimateResolution {
    val applicability: ApplicabilityResult

    data class Resolved(val snapshot: FixedDropEstimateSnapshot) : FixedDropEstimateResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.APPLIES
    }

    data object DoesNotApply : FixedDropEstimateResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.DOES_NOT_APPLY
    }

    data object Unknown : FixedDropEstimateResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.UNKNOWN
    }
}
