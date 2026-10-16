package com.colinmoerbe.poecompanion.acquisition.persistence

/**
 * Persistence-only curator publication state; not a property of the immutable domain snapshot.
 */
internal enum class FixedDropEstimateStatus {
    ACTIVE,
    SUPERSEDED,
}
