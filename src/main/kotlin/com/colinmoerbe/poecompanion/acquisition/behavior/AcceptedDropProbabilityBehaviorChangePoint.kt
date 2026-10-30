package com.colinmoerbe.poecompanion.acquisition.behavior

/** One current curator fact and its optimistic revision token, not a probability estimate revision. */
data class AcceptedDropProbabilityBehaviorChangePoint(
    val snapshot: DropProbabilityBehaviorSnapshot,
    val revision: Long,
) {
    init {
        require(revision >= 0) { "Accepted probability-behavior revision must not be negative" }
    }
}
