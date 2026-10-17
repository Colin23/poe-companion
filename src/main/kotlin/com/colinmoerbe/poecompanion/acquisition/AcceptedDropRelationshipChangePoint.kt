package com.colinmoerbe.poecompanion.acquisition

/**
 * One currently accepted game-patch change point and its curator-edit concurrency token.
 *
 * A changed accepted fact advances revision; this is not a historical curator revision record.
 */
data class AcceptedDropRelationshipChangePoint(
    val snapshot: DropRelationshipSnapshot,
    val revision: Long,
) {
    init {
        require(revision >= 0) { "Accepted drop relationship knowledge revision must not be negative" }
    }
}
