package com.colinmoerbe.poecompanion.acquisition.overrides

import com.colinmoerbe.poecompanion.acquisition.probability.FixedBernoulliModel

/**
 * One immutable player-confirmed probability revision for an exact personal context.
 *
 * It is not a canonical DropEstimate. No mutable STALE status belongs to the original confirmation;
 * a proposed reuse in another context will be resolved and reviewed separately.
 */
data class DropRateOverride(
    val revisionId: DropRateOverrideRevisionId,
    val key: DropRateOverrideKey,
    val model: FixedBernoulliModel,
)
