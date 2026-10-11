package com.colinmoerbe.poecompanion.acquisition

import com.colinmoerbe.poecompanion.league.ApplicabilityResult

/**
 * Result of resolving a drop-relationship timeline against an evaluation context.
 */
sealed interface DropRelationshipResolution {
    val applicability: ApplicabilityResult

    data class Resolved(val snapshot: DropRelationshipSnapshot) : DropRelationshipResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.APPLIES
    }

    data object DoesNotApply : DropRelationshipResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.DOES_NOT_APPLY
    }

    data object Unknown : DropRelationshipResolution {
        override val applicability: ApplicabilityResult = ApplicabilityResult.UNKNOWN
    }
}
